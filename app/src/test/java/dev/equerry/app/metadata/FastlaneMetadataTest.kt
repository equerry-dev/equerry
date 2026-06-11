package dev.equerry.app.metadata

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lint over the single canonical fastlane metadata tree that feeds both F-Droid and Play
 * (t-5, c-4 + c-5). Enforces the store length caps (Play's are the binding limits since one
 * tree serves both stores), a present changelog for versionCode 1, and the required donate
 * links from .github/FUNDING.yml. Pure file I/O — no Android dependencies.
 */
class FastlaneMetadataTest {

    private val root: File = repoRoot()
    private val metadata = File(root, "fastlane/metadata/android/en-US")

    private fun repoRoot(): File {
        // Unit tests run with the module dir (app/) as CWD; walk up to the repo root that holds
        // the fastlane tree so the test works regardless of where Gradle anchors the CWD.
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "fastlane/metadata/android/en-US").isDirectory) return dir
            dir = dir.parentFile
        }
        throw AssertionError("could not locate fastlane/metadata/android/en-US above ${File("").absolutePath}")
    }

    private fun text(name: String): String = File(metadata, name).readText().trim()

    @Test
    fun title_is_within_the_play_cap() {
        val title = text("title.txt")
        assertTrue("title.txt must not be empty", title.isNotEmpty())
        assertTrue("title.txt must be <= 30 chars (Play cap), was ${title.length}", title.length <= 30)
    }

    @Test
    fun short_description_is_within_the_play_cap() {
        val short = text("short_description.txt")
        assertTrue("short_description.txt must not be empty", short.isNotEmpty())
        assertTrue(
            "short_description.txt must be <= 80 chars (Play cap), was ${short.length}",
            short.length <= 80,
        )
    }

    @Test
    fun full_description_is_within_the_play_cap() {
        val full = text("full_description.txt")
        assertTrue("full_description.txt must not be empty", full.isNotEmpty())
        assertTrue(
            "full_description.txt must be <= 4000 chars (Play cap), was ${full.length}",
            full.length <= 4000,
        )
    }

    @Test
    fun changelog_for_version_code_1_is_present() {
        val changelog = File(metadata, "changelogs/1.txt")
        assertTrue("changelogs/1.txt must exist", changelog.isFile)
        assertTrue("changelogs/1.txt must not be empty", changelog.readText().trim().isNotEmpty())
    }

    @Test
    fun full_description_carries_the_funding_donate_links() {
        val full = text("full_description.txt")
        // Mirrors .github/FUNDING.yml: liberapay: equerry, github: [equerry-dev].
        assertTrue(
            "full_description.txt must link the Liberapay donate page",
            full.contains("liberapay.com/equerry"),
        )
        assertTrue(
            "full_description.txt must link the GitHub Sponsors page",
            full.contains("github.com/sponsors/equerry-dev"),
        )
    }
}
