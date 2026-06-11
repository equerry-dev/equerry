package dev.equerry.app.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.drivers.ChatHttpClient
import dev.equerry.app.providers.drivers.TranscribeRequestBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Records one utterance, auto-stopping the moment [shouldStop] (fed each frame's peak amplitude) returns true. */
interface AudioClipRecorder {
    suspend fun record(shouldStop: (amplitude: Int) -> Boolean): ByteArray
}

/** Uploads a recorded clip to the remote transcribe endpoint and returns the transcript text. */
interface TranscribeTransport {
    /** @throws Throwable on any transport/HTTP failure — mapped to [SttError.Unavailable] upstream. */
    suspend fun transcribe(clip: ByteArray): String
}

/**
 * Remote speech-to-text: record an utterance (auto-stopping on [SilenceVad] trailing silence — there
 * is no system VAD for a record-then-POST endpoint), then POST the clip to the provider's transcribe
 * endpoint. Emits exactly [SttEvent.EndOfSpeech] (when recording stops) then [SttEvent.Final] (the
 * transcript) — never [SttEvent.Partial], since remote transcribe returns no interim hypotheses. A
 * transport failure terminates the flow with [SttEvent.Error] (SttError.Unavailable — the sealed set
 * has no Auth variant) without hanging.
 *
 * Pure and Android-free at its core: the AudioRecord / OkHttp machinery lives behind the [recorder]
 * and [transport] seams (production wired via [fromProfile]); tests drive fakes. Mirrors
 * [SystemSpeechToText].
 */
class RemoteSpeechToText(
    private val recorder: AudioClipRecorder,
    private val transport: TranscribeTransport,
    private val vadFactory: () -> SilenceVad = { SilenceVad() },
) : SpeechToText {

    override fun listen(): Flow<SttEvent> = flow {
        val vad = vadFactory()
        val clip = recorder.record { amplitude -> vad.onFrame(amplitude) }
        emit(SttEvent.EndOfSpeech)
        val transcript = try {
            transport.transcribe(clip)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emit(SttEvent.Error(SttError.Unavailable))
            return@flow
        }
        emit(SttEvent.Final(transcript))
    }

    companion object {
        /**
         * Production factory: AudioRecord capture + an OkHttp multipart POST to the profile's
         * transcribe endpoint. The audio/HTTP seams are framework-bound and verified at integration.
         */
        fun fromProfile(
            profile: ProviderProfile,
            key: String,
            client: OkHttpClient = ChatHttpClient.create(),
        ): RemoteSpeechToText = RemoteSpeechToText(
            recorder = AudioRecordClipRecorder(),
            transport = OkHttpTranscribeTransport(client, profile.baseUrl, profile.model, key),
        )
    }
}

/** Carries an HTTP status from the transcribe transport. Not an IOException, so it never maps to Network. */
class RemoteAudioHttpException(val code: Int) : Exception("remote audio HTTP $code")

@Serializable
private data class TranscriptResponse(val text: String = "")

/** Framework-bound recorder: 16 kHz mono PCM via AudioRecord, wrapped in a WAV container. Integration-edge. */
@SuppressLint("MissingPermission")
private class AudioRecordClipRecorder(
    private val sampleRate: Int = 16_000,
) : AudioClipRecorder {

    override suspend fun record(shouldStop: (Int) -> Boolean): ByteArray = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val frameSamples = sampleRate / 10 // ~100 ms frames
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, frameSamples * 2),
        )
        val pcm = ByteArrayOutputStream()
        val buf = ShortArray(frameSamples)
        try {
            record.startRecording()
            while (currentCoroutineContext().isActive) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) continue
                var peak = 0
                for (i in 0 until n) {
                    val a = abs(buf[i].toInt())
                    if (a > peak) peak = a
                    pcm.write(buf[i].toInt() and 0xff)
                    pcm.write((buf[i].toInt() shr 8) and 0xff)
                }
                if (shouldStop(peak)) break
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
        wavFromPcm(pcm.toByteArray(), sampleRate)
    }

    private fun wavFromPcm(data: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bits = 16
        val byteRate = sampleRate * channels * bits / 8
        val buffer = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + data.size)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((channels * bits / 8).toShort())
        buffer.putShort(bits.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(data.size)
        buffer.put(data)
        return buffer.array()
    }
}

/** Framework-bound transport: OkHttp multipart POST to `audio/transcriptions`. Integration-edge. */
private class OkHttpTranscribeTransport(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val model: String,
    private val key: String,
) : TranscribeTransport {

    override suspend fun transcribe(clip: ByteArray): String = withContext(Dispatchers.IO) {
        val built = TranscribeRequestBuilder.build(model, key)
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        built.formFields.forEach { (name, value) -> multipart.addFormDataPart(name, value) }
        multipart.addFormDataPart("file", "audio.wav", clip.toRequestBody(WAV_MEDIA))
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/" + built.path)
            .post(multipart.build())
            .apply { built.headers.forEach { (name, value) -> header(name, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RemoteAudioHttpException(response.code)
            JSON.decodeFromString<TranscriptResponse>(response.body?.string().orEmpty()).text
        }
    }

    private companion object {
        val WAV_MEDIA = "audio/wav".toMediaType()
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
