package dev.equerry.app.tools.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * FOSS on-device OCR backed by Tesseract (Tesseract4Android). The trained model
 * `tessdata/eng.traineddata` must be bundled under `app/src/main/assets/` (see the README there);
 * it is copied to app-private storage on first use because [TessBaseAPI] reads it from the
 * filesystem, not assets. When the model is absent (or init fails) [recognise] returns "" so the
 * screen-context flow degrades to the blank-screen path rather than crashing.
 *
 * Native-bound: real OCR accuracy is verified on-device (instrumented/manual). The no-model
 * degradation path returns before touching native code and IS unit-tested (TesseractOcrEngineTest).
 */
class TesseractOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrEngine {

    override suspend fun recognise(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val dataPath = ensureTrainedData() ?: return@withContext ""
        val tess = TessBaseAPI()
        try {
            if (!tess.init(dataPath, LANGUAGE)) return@withContext ""
            tess.setImage(bitmap)
            tess.getUTF8Text().orEmpty().trim()
        } catch (e: Exception) {
            "" // Any native/recognition failure degrades to "no text", never a crash.
        } finally {
            tess.recycle()
        }
    }

    /**
     * Ensures `<filesDir>/tessdata/<lang>.traineddata` exists by copying it from assets on first use,
     * and returns the tessdata PARENT dir to hand to [TessBaseAPI.init] (it expects the directory that
     * contains `tessdata/`, not the file). Returns null when the model isn't bundled or the copy
     * fails — OCR is then unavailable and [recognise] yields "".
     */
    private fun ensureTrainedData(): String? {
        val parent = context.filesDir
        val tessdata = File(parent, TESSDATA_DIR)
        val model = File(tessdata, "$LANGUAGE.traineddata")
        if (model.exists() && model.length() > 0) return parent.absolutePath
        return try {
            tessdata.mkdirs()
            context.assets.open("$TESSDATA_DIR/$LANGUAGE.traineddata").use { input ->
                model.outputStream().use { output -> input.copyTo(output) }
            }
            parent.absolutePath
        } catch (e: Exception) {
            model.delete() // drop any partial copy so a later attempt re-copies cleanly
            null
        }
    }

    private companion object {
        const val LANGUAGE = "eng"
        const val TESSDATA_DIR = "tessdata"
    }
}
