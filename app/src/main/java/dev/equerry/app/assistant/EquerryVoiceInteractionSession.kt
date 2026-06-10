package dev.equerry.app.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.equerry.app.data.ProbeStore
import dev.equerry.app.ui.probe.ProbeSessionScreen
import dev.equerry.app.ui.theme.EquerryTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The assist session. On each invocation its callbacks adapt the AssistStructure and
 * screenshot into a [ProbeRecord] via [ProbeCapture], persist it to [ProbeStore], and
 * render the live capture through [ProbeSessionScreen] in a Compose content view.
 *
 * This class is framework-bound and verified manually (c-2). The record-shaping logic
 * lives in [ProbeCapture]/[AssistAnalyzer] which are unit-tested.
 */
class EquerryVoiceInteractionSession(context: Context) :
    VoiceInteractionSession(context),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    /** Pulls the singleton [ProbeStore] out of Hilt — a Session is not a Hilt entry point. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProbeStoreEntryPoint {
        fun probeStore(): ProbeStore
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val probeStore: ProbeStore = EntryPointAccessors
        .fromApplication(context.applicationContext, ProbeStoreEntryPoint::class.java)
        .probeStore()

    private var current by mutableStateOf<ProbeRecord?>(null)

    /** Latest screenshot classification; reset to "blocked" after each capture. */
    private var pendingShot: ScreenshotMeta = AssistAnalyzer.screenshotMeta(null, null)

    private val capture = ProbeCapture { record ->
        current = record
        scope.launch { probeStore.append(record) }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onCreateContentView(): View =
        ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@EquerryVoiceInteractionSession)
            setViewTreeViewModelStoreOwner(this@EquerryVoiceInteractionSession)
            setViewTreeSavedStateRegistryOwner(this@EquerryVoiceInteractionSession)
            setContent {
                EquerryTheme { ProbeSessionScreen(current) }
            }
        }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        // Record dimensions only — never the bitmap (screenshot_retention).
        pendingShot = AssistAnalyzer.screenshotMeta(screenshot?.width, screenshot?.height)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        super.onHandleAssist(data, structure, content)
        val pkg = structure?.activityComponent?.packageName ?: "unknown"
        val root: ViewNodeLike? = structure
            ?.takeIf { it.windowNodeCount > 0 }
            ?.getWindowNodeAt(0)
            ?.rootViewNode
            ?.let { node ->
                AssistAdapter.adapt(node, { it.text }, { it.childCount }, { n, i -> n.getChildAt(i) })
            }
        capture.capture(root, pendingShot.width, pendingShot.height, pkg, System.currentTimeMillis())
        pendingShot = AssistAnalyzer.screenshotMeta(null, null)
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        scope.cancel()
        super.onDestroy()
    }
}
