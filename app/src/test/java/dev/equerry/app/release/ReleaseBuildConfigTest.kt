package dev.equerry.app.release

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural guard over the release build config + proguard keeps (t-3, c-6).
 *
 * The release buildType is not reachable from a debug unit test's BuildConfig (tests run against
 * the debug variant), so this asserts over the Gradle DSL and proguard source text directly:
 * a debug-only leftover (debuggable=true / minify off), a drifted version/appId, or missing
 * keep-rules that would let R8 strip the assistant entry point all fail here.
 */
class ReleaseBuildConfigTest {

    private fun moduleFile(name: String): File {
        // Gradle runs unit tests with the module dir as CWD; fall back to ./app defensively.
        val direct = File(name)
        return if (direct.exists()) direct else File("app", name)
    }

    private val buildGradle: String by lazy { moduleFile("build.gradle.kts").readText() }
    private val proguard: String by lazy { moduleFile("proguard-rules.pro").readText() }

    // ---- release buildType hardening ----

    @Test
    fun release_is_not_debuggable() {
        assertTrue(
            "release buildType must not enable isDebuggable",
            !buildGradle.contains(Regex("""isDebuggable\s*=\s*true""")),
        )
    }

    @Test
    fun release_enables_minify_and_shrink() {
        assertTrue(
            "release must set isMinifyEnabled = true",
            buildGradle.contains(Regex("""isMinifyEnabled\s*=\s*true""")),
        )
        assertTrue(
            "release must set isShrinkResources = true",
            buildGradle.contains(Regex("""isShrinkResources\s*=\s*true""")),
        )
    }

    @Test
    fun version_and_application_id_are_pinned() {
        assertTrue(
            """applicationId must be "dev.equerry.app"""",
            buildGradle.contains(Regex("""applicationId\s*=\s*"dev\.equerry\.app"""")),
        )
        assertTrue(
            "versionCode must be 1",
            buildGradle.contains(Regex("""versionCode\s*=\s*1\b""")),
        )
        assertTrue(
            """versionName must be "0.1.0"""",
            buildGradle.contains(Regex("""versionName\s*=\s*"0\.1\.0"""")),
        )
    }

    // ---- proguard keep-rules so R8 cannot strip the assistant / break DI ----

    @Test
    fun proguard_keeps_the_voice_interaction_service() {
        assertTrue(
            "proguard must keep EquerryVoiceInteractionService — R8 would otherwise strip the assistant entry point",
            proguard.contains("EquerryVoiceInteractionService"),
        )
    }

    @Test
    fun proguard_keeps_hilt_and_serialization() {
        assertTrue(
            "proguard must keep the Hilt generated graph",
            proguard.contains("dagger.hilt"),
        )
        assertTrue(
            "proguard must keep kotlinx.serialization serializers",
            proguard.contains("serializer"),
        )
    }
}
