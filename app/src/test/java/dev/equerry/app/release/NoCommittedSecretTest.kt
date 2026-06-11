package dev.equerry.app.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * r-03 guard (t-4, c-6): release signing credentials live outside the repo. Asserts the build
 * script externalizes signing (env vars / a gitignored keystore.properties) and hardcodes no
 * secret or keystore path, and that keystore.properties is gitignored so a real keystore can
 * never be committed.
 */
class NoCommittedSecretTest {

    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, ".gitignore").isFile && File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        throw AssertionError("could not locate repo root above ${File("").absolutePath}")
    }

    private val root: File = repoRoot()
    private val buildGradle: String = File(root, "app/build.gradle.kts").readText()
    private val gitignore: String = File(root, ".gitignore").readText()

    @Test
    fun signing_is_resolved_out_of_repo() {
        assertTrue(
            "release signing must read from env / keystore.properties, not committed literals",
            buildGradle.contains("resolveSigning") && buildGradle.contains("System.getenv"),
        )
    }

    @Test
    fun build_script_hardcodes_no_keystore_path() {
        assertFalse(
            "app/build.gradle.kts must not contain a literal .jks/.keystore path",
            buildGradle.contains(".jks") || buildGradle.contains(".keystore"),
        )
    }

    @Test
    fun keystore_properties_is_gitignored() {
        assertTrue(
            "keystore.properties must be gitignored so real secrets can't be committed (r-03)",
            gitignore.lines().any { it.trim() == "keystore.properties" },
        )
    }
}
