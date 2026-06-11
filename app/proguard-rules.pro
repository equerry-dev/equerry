# Equerry ProGuard / R8 rules.
# Keep kotlinx.serialization generated serializers for provider request/response models.
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers,allowobfuscation class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / OkHttp keep rules are bundled with the libraries (consumer rules); nothing
# extra is needed here for v1. Add app-specific keeps as drivers are implemented.

# Keep the manifest-declared assistant entry points. The system binds these reflectively via
# BIND_VOICE_INTERACTION; without explicit keeps, minify could strip the VoiceInteractionService
# that makes Equerry selectable as the default assistant — the app's entire reason to exist (c-6).
-keep class dev.equerry.app.assistant.EquerryVoiceInteractionService { *; }
-keep class dev.equerry.app.assistant.EquerryVoiceInteractionSessionService { *; }
-keep class dev.equerry.app.assistant.EquerryRecognitionService { *; }

# Hilt ships consumer rules, but pin its generated graph + @AndroidEntryPoint targets so a
# stripped component never breaks DI in a minified release.
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-dontwarn dagger.hilt.**
