package dev.equerry.app.assistant

/**
 * Builds a [ProbeRecord] from analyzed assist inputs and emits it to [sink]. Pure and
 * unit-testable: the framework [EquerryVoiceInteractionSession] adapts its callbacks
 * into calls on this, so the record-shaping logic has a gate even though the session
 * itself is framework-bound.
 */
class ProbeCapture(private val sink: (ProbeRecord) -> Unit) {

    fun capture(
        root: ViewNodeLike?,
        screenshotWidth: Int?,
        screenshotHeight: Int?,
        packageName: String,
        timestamp: Long,
    ) {
        val structure = AssistAnalyzer.analyze(root)
        val shot = AssistAnalyzer.screenshotMeta(screenshotWidth, screenshotHeight)
        sink(
            ProbeRecord(
                packageName = packageName,
                timestamp = timestamp,
                structureProvided = structure.structureProvided,
                nodeCount = structure.nodeCount,
                screenshotArrived = shot.arrived,
                screenshotBlocked = shot.blocked,
                screenshotWidth = shot.width,
                screenshotHeight = shot.height,
            ),
        )
    }
}

/**
 * Adapts a framework ViewNode-shaped tree into the testable [ViewNodeLike]. Generic over
 * the node type `N` so the recursion/wiring can be unit-tested with a stub — the real
 * `android.app.assist.AssistStructure.ViewNode` is final and cannot be constructed in a
 * JVM test.
 */
object AssistAdapter {
    fun <N> adapt(
        node: N,
        text: (N) -> CharSequence?,
        childCount: (N) -> Int,
        childAt: (N, Int) -> N,
    ): ViewNodeLike = object : ViewNodeLike {
        override val text: CharSequence? = text(node)
        override val childCount: Int = childCount(node)
        override fun childAt(index: Int): ViewNodeLike =
            adapt(childAt(node, index), text, childCount, childAt)
    }
}
