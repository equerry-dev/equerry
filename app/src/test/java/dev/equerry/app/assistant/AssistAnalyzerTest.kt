package dev.equerry.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistAnalyzerTest {

    private class FakeNode(
        override val text: CharSequence?,
        private val children: List<ViewNodeLike> = emptyList(),
    ) : ViewNodeLike {
        override val childCount: Int get() = children.size
        override fun childAt(index: Int): ViewNodeLike = children[index]
    }

    @Test
    fun null_structure_yields_not_provided_and_zero_count() {
        val summary = AssistAnalyzer.analyze(null)
        assertFalse(summary.structureProvided)
        assertEquals(0, summary.nodeCount)
    }

    @Test
    fun counts_only_nodes_with_non_blank_text() {
        // root("a") + children: "b", null, "  ", "c" -> 3 text-bearing, 2 blank/null.
        val root = FakeNode(
            text = "a",
            children = listOf(
                FakeNode("b"),
                FakeNode(null),
                FakeNode("  "),
                FakeNode("c"),
            ),
        )
        val summary = AssistAnalyzer.analyze(root)
        assertTrue(summary.structureProvided)
        assertEquals(3, summary.nodeCount)
    }

    @Test
    fun counts_all_descendants_in_a_deeply_nested_tree() {
        // 4 levels, each carrying text -> a recursion bug that stops early counts < 4.
        val tree = FakeNode("l1", listOf(FakeNode("l2", listOf(FakeNode("l3", listOf(FakeNode("l4")))))))
        assertEquals(4, AssistAnalyzer.countTextNodes(tree))
    }

    @Test
    fun extractText_joins_non_blank_nodes_in_pre_order() {
        // root "a" + children "b", null, "  ", "c" -> three non-blank fragments, in order.
        val root = FakeNode(
            text = "a",
            children = listOf(FakeNode("b"), FakeNode(null), FakeNode("  "), FakeNode("c")),
        )
        assertEquals("a\nb\nc", AssistAnalyzer.extractText(root))
    }

    @Test
    fun extractText_trims_each_fragment_and_descends_all_levels() {
        val tree = FakeNode("  l1  ", listOf(FakeNode("l2", listOf(FakeNode("l3")))))
        assertEquals("l1\nl2\nl3", AssistAnalyzer.extractText(tree))
    }

    @Test
    fun extractText_null_or_textless_tree_is_empty() {
        assertEquals("", AssistAnalyzer.extractText(null))
        assertEquals("", AssistAnalyzer.extractText(FakeNode(null, listOf(FakeNode("   ")))))
    }

    @Test
    fun screenshot_with_null_dimensions_is_blocked() {
        val meta = AssistAnalyzer.screenshotMeta(null, null)
        assertFalse(meta.arrived)
        assertTrue(meta.blocked)
        assertEquals(null, meta.width)
        assertEquals(null, meta.height)
    }

    @Test
    fun screenshot_with_dimensions_arrived() {
        val meta = AssistAnalyzer.screenshotMeta(1080, 2400)
        assertTrue(meta.arrived)
        assertFalse(meta.blocked)
        assertEquals(1080, meta.width)
        assertEquals(2400, meta.height)
    }
}
