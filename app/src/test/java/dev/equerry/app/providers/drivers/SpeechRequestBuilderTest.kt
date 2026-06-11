package dev.equerry.app.providers.drivers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRequestBuilderTest {

    private val key = "sk-secret-ABCDEF1234567890"

    private fun body(req: SpeechHttpRequest) = Json.parseToJsonElement(req.body).jsonObject

    @Test
    fun builds_the_speech_path_with_model_input_voice_and_mp3_format() {
        val req = SpeechRequestBuilder.build("tts-1", "hello there", voice = "alloy", key = key)
        val json = body(req)

        assertEquals("audio/speech", req.path)
        assertEquals("tts-1", json["model"]!!.jsonPrimitive.content)
        assertEquals("hello there", json["input"]!!.jsonPrimitive.content)
        assertEquals("alloy", json["voice"]!!.jsonPrimitive.content)
        assertEquals("mp3", json["response_format"]!!.jsonPrimitive.content)
    }

    @Test
    fun blank_or_null_voice_omits_the_voice_field() {
        assertFalse(body(SpeechRequestBuilder.build("tts-1", "hi", voice = null, key = key)).containsKey("voice"))
        assertFalse(body(SpeechRequestBuilder.build("tts-1", "hi", voice = "", key = key)).containsKey("voice"))
    }

    @Test
    fun key_lives_in_the_authorization_header_only_never_in_body_or_path() {
        val req = SpeechRequestBuilder.build("tts-1", "hi", voice = "alloy", key = key)
        assertEquals("Bearer $key", req.headers["Authorization"])
        // r-03: the key must not leak into the URL/query or the JSON body.
        assertFalse(req.body.contains(key))
        assertFalse(req.path.contains(key))
        assertTrue(req.headers["Content-Type"] == "application/json")
    }
}
