package dev.equerry.app.tools.ocr

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM-runnable coverage for the OCR engine's degradation path. Real recognition accuracy is
 * native and verified on-device (instrumented/manual); this asserts the contract that matters for
 * any build without the bundled model: no `tessdata/eng.traineddata` -> recognise returns "",
 * never a crash. The no-model branch returns before constructing [com.googlecode.tesseract.android.TessBaseAPI],
 * so no native library is loaded under the JVM unit test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TesseractOcrEngineTest {

    @Test
    fun recognise_returns_empty_when_no_trained_model_is_bundled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = TesseractOcrEngine(context)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

        assertEquals("", engine.recognise(bitmap))
    }
}
