package dev.equerry.app.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.equerry.app.providers.drivers.ChatImage
import dev.equerry.app.screencontext.ScreenContext
import dev.equerry.app.tools.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Framework-bound camera capture for the "look through the camera" flow. Shows a brief preview so the
 * user can aim, auto-captures one frame after a short delay, OCRs it on-device, and hands the frame
 * (JPEG bytes + recognised text) back through [CameraCaptureCoordinator] for the VISION round-trip.
 * The image is a transient request payload — never written to storage. Integration-edge (manual
 * verification); the capture-trigger grammar and the VISION/OCR round-trip it feeds are unit-tested.
 */
@AndroidEntryPoint
class CameraCaptureActivity : ComponentActivity() {

    @Inject
    lateinit var coordinator: CameraCaptureCoordinator

    @Inject
    lateinit var ocr: OcrEngine

    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null

    // Exactly one outcome is ever delivered (capture, failure, or back-out), so the awaiting voice flow
    // never hangs and never double-completes.
    private val settled = AtomicBoolean(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) startCamera() else finishWith(null) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewView = PreviewView(this)
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                Text(
                    "Hold steady — looking…",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
                )
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                // Give the user ~2s to aim before grabbing the frame (auto-snapshot).
                lifecycleScope.launch {
                    delay(2_000)
                    captureFrame()
                }
            } catch (_: Throwable) {
                finishWith(null)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureFrame() {
        val capture = imageCapture ?: return finishWith(null)
        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val jpeg = image.toJpegBytes()
                    image.close()
                    lifecycleScope.launch {
                        val text = runCatching { withContext(Dispatchers.Default) { ocr.recognise(ChatImage(jpeg, "image/jpeg")) } }
                            .getOrDefault("")
                        finishWith(ScreenContext(text = text, screenshot = ChatImage(jpeg, "image/jpeg")))
                    }
                }

                override fun onError(exception: ImageCaptureException) = finishWith(null)
            },
        )
    }

    /** Deliver the single outcome to the awaiting voice flow and close. Idempotent. */
    private fun finishWith(result: ScreenContext?) {
        if (settled.getAndSet(true)) return
        coordinator.deliver(result)
        finish()
    }

    override fun onDestroy() {
        // If the activity is torn down before capturing (e.g. back press), unblock the voice flow.
        finishWith(null)
        super.onDestroy()
    }
}

/** Read the JPEG bytes out of a CameraX [ImageProxy] (ImageCapture default output is a single JPEG plane). */
private fun ImageProxy.toJpegBytes(): ByteArray {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
}
