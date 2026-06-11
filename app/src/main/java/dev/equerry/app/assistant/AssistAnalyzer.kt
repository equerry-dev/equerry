package dev.equerry.app.assistant

/**
 * Minimal view-tree abstraction over an assist node. The Android framework type is
 * `android.app.assist.AssistStructure.ViewNode`; depending on it directly would make
 * node analysis impossible to unit-test on the JVM. t-8 adapts ViewNode -> this; the
 * counting logic below stays pure and testable with fakes.
 */
interface ViewNodeLike {
    val text: CharSequence?
    val childCount: Int
    fun childAt(index: Int): ViewNodeLike
}

/** Facts about an AssistStructure, derived without retaining the structure itself. */
data class StructureSummary(
    val structureProvided: Boolean,
    val nodeCount: Int,
)

/**
 * Classification of an assist screenshot from its dimensions alone. There is no
 * bitmap field — the analyzer never sees pixels (screenshot_retention).
 */
data class ScreenshotMeta(
    val arrived: Boolean,
    val blocked: Boolean,
    val width: Int?,
    val height: Int?,
)

/** Pure analysis of assist payloads. No Android framework dependencies. */
object AssistAnalyzer {

    /** Summarise an assist structure. A null root means no structure was provided. */
    fun analyze(root: ViewNodeLike?): StructureSummary =
        StructureSummary(structureProvided = root != null, nodeCount = countTextNodes(root))

    /** Count nodes carrying non-blank text across the entire subtree. Null root -> 0. */
    fun countTextNodes(root: ViewNodeLike?): Int {
        if (root == null) return 0
        var count = if (!root.text.isNullOrBlank()) 1 else 0
        for (i in 0 until root.childCount) {
            count += countTextNodes(root.childAt(i))
        }
        return count
    }

    /**
     * Concatenate every non-blank text node across the subtree in pre-order, newline-separated —
     * the screen's textual content for the screen-context flow (the Assist-text fallback sent to a
     * VISION/CHAT provider). Null root -> "". Each fragment is trimmed; the result carries no pixels.
     */
    fun extractText(root: ViewNodeLike?): String {
        if (root == null) return ""
        val out = StringBuilder()
        collectText(root, out)
        return out.toString()
    }

    private fun collectText(node: ViewNodeLike, out: StringBuilder) {
        val text = node.text
        if (!text.isNullOrBlank()) {
            if (out.isNotEmpty()) out.append('\n')
            out.append(text.trim())
        }
        for (i in 0 until node.childCount) {
            collectText(node.childAt(i), out)
        }
    }

    /**
     * Classify a screenshot from its dimensions. Null dimensions (the platform
     * declined to provide a screenshot, or blocked it) -> arrived=false, blocked=true.
     * The signature takes no Bitmap, so pixels cannot leak past this boundary.
     */
    fun screenshotMeta(width: Int?, height: Int?): ScreenshotMeta =
        if (width == null || height == null) {
            ScreenshotMeta(arrived = false, blocked = true, width = null, height = null)
        } else {
            ScreenshotMeta(arrived = true, blocked = false, width = width, height = height)
        }
}
