package dev.equerry.app.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** Creates an [EquerryVoiceInteractionSession] for each assist invocation. */
class EquerryVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        EquerryVoiceInteractionSession(this)
}
