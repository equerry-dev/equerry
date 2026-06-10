package dev.equerry.app.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Minimal recognition service. A [VoiceInteractionService] must advertise a
 * `android:recognitionService` for the assistant role to validate and surface in the
 * picker across OEMs. Equerry uses the system STT in v0.1, so this performs no
 * recognition — it exists solely to satisfy the voice-interaction-service contract.
 */
class EquerryRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // No on-device recognition in v0.1 — report unavailability rather than hang.
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}
