package dev.equerry.app.assistant

import android.service.voice.VoiceInteractionService

/**
 * The always-available assistant service. Its declaration + the
 * `@xml/voice_interaction_service` metadata make Equerry selectable as the system
 * default digital assistant; the actual per-invocation work happens in
 * [EquerryVoiceInteractionSession].
 */
class EquerryVoiceInteractionService : VoiceInteractionService()
