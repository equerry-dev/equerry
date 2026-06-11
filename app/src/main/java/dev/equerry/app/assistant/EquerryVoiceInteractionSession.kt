package dev.equerry.app.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import dev.equerry.app.data.VoiceSettingsStore
import dev.equerry.app.providers.ProviderRepository
import dev.equerry.app.providers.drivers.ChatDriverFactory
import dev.equerry.app.providers.drivers.ChatImage
import dev.equerry.app.providers.drivers.ChatSession
import dev.equerry.app.screencontext.ScreenContext
import dev.equerry.app.tools.actions.ActionRunner
import dev.equerry.app.tools.ocr.OcrEngine
import dev.equerry.app.ui.chat.ChatViewModel
import dev.equerry.app.ui.theme.EquerryTheme
import dev.equerry.app.voice.MicPermission
import dev.equerry.app.voice.SystemSpeechToText
import dev.equerry.app.voice.SystemTextToSpeech
import dev.equerry.app.voice.VoiceFlowController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * The session's lifecycle wiring, extracted from the framework-bound session so it is unit-testable
 * (see VoiceSessionWiringTest). [onShow] checks mic permission BEFORE arming STT — when denied it
 * requests the mic instead of blindly arming a recognizer that would immediately error. [onDestroy]
 * releases exactly once (the underlying stop is idempotent).
 */
class VoiceSessionCoordinator(
    private val isMicGranted: () -> Boolean,
    private val arm: () -> Unit,
    private val release: () -> Unit,
    private val requestMic: () -> Unit,
) {
    fun onShow() {
        if (isMicGranted()) arm() else requestMic()
    }

    fun onDestroy() {
        release()
    }
}

/**
 * The assist session. On invocation it renders the phase-04 [ChatScreen] (session_ui_reuse, locked)
 * driven by a [VoiceFlowController]: the spoken question is captured by system STT, sent to the
 * CHAT provider, and the reply spoken via TTS. AssistStructure probe capture still feeds [ProbeStore]
 * (off the rendered surface — the Probe dashboard stays reachable via the Probe log route only).
 *
 * Framework-bound (a [VoiceInteractionSession]); verified MANUALLY: assist gesture → listening →
 * spoken question shown → reply renders as it streams → reply spoken aloud; a denied mic or missing
 * provider shows guidance instead of crashing (c-1..c-5). The branching/decision logic lives in
 * [VoiceFlowController] and [VoiceSessionCoordinator], which ARE unit-tested. All collaborators are
 * pulled from Hilt as singletons via [VoiceEntryPoint]; [ChatViewModel] is constructed directly from
 * those singletons (it is a @HiltViewModel, so it cannot be exposed through a SingletonComponent
 * EntryPoint, and the session is not a Hilt ViewModel host).
 */
class EquerryVoiceInteractionSession(context: Context) :
    VoiceInteractionSession(context),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    /** Pulls the singleton collaborators the voice flow needs — a Session is not a Hilt entry point. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface VoiceEntryPoint {
        fun probeStore(): ProbeStore
        fun providerRepository(): ProviderRepository
        fun chatDriverFactory(): ChatDriverFactory
        fun chatSession(): ChatSession
        fun voiceSettingsStore(): VoiceSettingsStore
        fun actionRunner(): ActionRunner
        fun ocrEngine(): OcrEngine
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val entry: VoiceEntryPoint = EntryPointAccessors
        .fromApplication(context.applicationContext, VoiceEntryPoint::class.java)

    private val probeStore: ProbeStore = entry.probeStore()

    // ChatViewModel reuses the phase-04 round-trip; constructed directly from the injected singletons.
    private val chatViewModel: ChatViewModel = ChatViewModel(
        entry.providerRepository(),
        entry.chatDriverFactory(),
        entry.chatSession(),
        entry.actionRunner(),
        entry.ocrEngine(),
    )

    private val controller: VoiceFlowController = VoiceFlowController(
        chat = chatViewModel,
        stt = SystemSpeechToText.fromContext(context.applicationContext),
        tts = SystemTextToSpeech.fromContext(context.applicationContext),
        settings = entry.voiceSettingsStore(),
        scope = scope,
        isMicGranted = { MicPermission.isGranted(context.applicationContext) },
        isChatConfigured = { entry.providerRepository().observeChatMapping().first() != null },
        screenContext = { currentScreenContext() },
    )

    private val coordinator = VoiceSessionCoordinator(
        isMicGranted = { MicPermission.isGranted(context.applicationContext) },
        arm = { controller.start() },
        // The controller is the single guidance authority: when the mic is denied, start() surfaces
        // the mic-denied guidance (with the settings path) without arming the recognizer.
        requestMic = { controller.start() },
        release = { controller.stop() },
    )

    /** Latest screenshot classification; reset to "blocked" after each capture. */
    private var pendingShot: ScreenshotMeta = AssistAnalyzer.screenshotMeta(null, null)

    /**
     * The current invocation's screen capture, held ONLY in memory to answer a screen-context query
     * (c-6, screenshot_retention): the latest assist screenshot and the extracted Assist text. Never
     * written to ProbeStore/DataStore — the probe log still records dimensions only. Both are replaced
     * on the next invocation and dropped on destroy.
     */
    private var pendingBitmap: Bitmap? = null
    private var pendingScreenText: String = ""

    private val capture = ProbeCapture { record ->
        scope.launch { probeStore.append(record) }
    }

    /**
     * Build the [ScreenContext] for a screen-context query from the current in-memory capture: the
     * Assist-extracted text and the screenshot encoded as PNG bytes (transient request payload). Null
     * when nothing was captured this invocation, so a spoken query falls through to normal chat.
     */
    private fun currentScreenContext(): ScreenContext? {
        val bitmap = pendingBitmap
        val image = bitmap?.let {
            val bytes = ByteArrayOutputStream().use { out ->
                it.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            ChatImage(bytes, "image/png")
        }
        if (pendingScreenText.isBlank() && image == null) return null
        return ScreenContext(text = pendingScreenText, screenshot = image)
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
                EquerryTheme {
                    val state by chatViewModel.state.collectAsState()
                    val guidance by controller.guidance.collectAsState()
                    AssistSessionContent(
                        state = state,
                        guidance = guidance,
                        // A null capture sends an empty context so the user still gets the
                        // blank-screen note rather than nothing (c-1).
                        onAskScreen = {
                            chatViewModel.askAboutScreen(currentScreenContext() ?: ScreenContext("", null))
                        },
                        onInput = chatViewModel::onInputChange,
                        onSend = chatViewModel::send,
                        onNewChat = chatViewModel::newChat,
                    )
                }
            }
        }

    /** The session UI is shown on each invocation — begin listening (c-1). */
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        coordinator.onShow()
    }

    /** Session hidden — stop the voice loop; restartable on the next onShow. */
    override fun onHide() {
        coordinator.onDestroy()
        super.onHide()
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        // Probe log records dimensions only — never the bitmap (screenshot_retention).
        pendingShot = AssistAnalyzer.screenshotMeta(screenshot?.width, screenshot?.height)
        // Retain the bitmap transiently (in-memory only) to answer a screen-context query (c-6).
        pendingBitmap = screenshot
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
        // Retain the screen's text (in-memory only) for a screen-context query (c-2/c-3 text path).
        pendingScreenText = AssistAnalyzer.extractText(root)
        pendingShot = AssistAnalyzer.screenshotMeta(null, null)
    }

    override fun onDestroy() {
        coordinator.onDestroy()
        // Drop the transient capture so the screenshot isn't held past the session (c-6).
        pendingBitmap = null
        pendingScreenText = ""
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        scope.cancel()
        super.onDestroy()
    }
}
