package dev.equerry.app.assistant

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.data.ProbeStore
import dev.equerry.app.ui.probe.ProbeLogViewModel
import dev.equerry.app.ui.probe.ProbeSessionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProbeCaptureTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // A ViewNodeLike fake (the input ProbeCapture analyzes).
    private class FakeNode(
        override val text: CharSequence?,
        private val children: List<ViewNodeLike> = emptyList(),
    ) : ViewNodeLike {
        override val childCount: Int get() = children.size
        override fun childAt(index: Int): ViewNodeLike = children[index]
    }

    // A node "shaped like" AssistStructure.ViewNode, for exercising the adapter.
    private class FakeVN(val t: CharSequence?, val kids: List<FakeVN> = emptyList())

    private lateinit var scope: CoroutineScope
    private lateinit var store: ProbeStore
    private lateinit var sessionVm: ProbeSessionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        scope = CoroutineScope(Dispatchers.IO + Job())
        val file = File(tmp.newFolder(), "probe.preferences_pb")
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        store = ProbeStore(ds)
        sessionVm = ProbeSessionViewModel(store)
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    @Test
    fun capture_builds_record_and_pushes_to_viewmodel_and_store() = runBlocking {
        val capture = ProbeCapture(sessionVm::onCapture)
        val root = FakeNode("a", listOf(FakeNode("b"), FakeNode(null), FakeNode("c")))

        capture.capture(root, screenshotWidth = 1080, screenshotHeight = 2400, packageName = "com.example.app", timestamp = 7L)

        val rec = sessionVm.currentCapture.value!!
        assertEquals("com.example.app", rec.packageName)
        assertTrue(rec.structureProvided)
        assertEquals(3, rec.nodeCount) // a, b, c (null skipped)
        assertTrue(rec.screenshotArrived)
        assertEquals(1080, rec.screenshotWidth)
        assertEquals(2400, rec.screenshotHeight)

        // It also lands in the shared store, so the log screen sees it.
        val logVm = ProbeLogViewModel(store)
        assertEquals(listOf(rec), logVm.records.first { it.isNotEmpty() })
    }

    @Test
    fun capture_with_no_structure_or_screenshot_records_absence() {
        var captured = false
        val capture = ProbeCapture { r ->
            captured = true
            assertTrue(!r.structureProvided)
            assertEquals(0, r.nodeCount)
            assertTrue(r.screenshotBlocked)
            assertEquals(null, r.screenshotWidth)
        }
        capture.capture(null, screenshotWidth = null, screenshotHeight = null, packageName = "unknown", timestamp = 1L)
        assertTrue(captured)
    }

    @Test
    fun adapter_over_a_stub_viewnode_tree_counts_text_nodes() {
        // a -> [b, null, c -> [d]]  => 4 text-bearing nodes.
        val tree = FakeVN("a", listOf(FakeVN("b"), FakeVN(null), FakeVN("c", listOf(FakeVN("d")))))
        val adapted = AssistAdapter.adapt(tree, { it.t }, { it.kids.size }, { n, i -> n.kids[i] })
        assertEquals(4, AssistAnalyzer.countTextNodes(adapted))
    }
}
