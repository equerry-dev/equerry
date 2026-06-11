package dev.equerry.app.tools.ocr

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the OCR seam to its Tesseract implementation. Callers (the screen-context orchestration in
 * t-7) inject [OcrEngine] and stay decoupled from the native engine — tests substitute a fake.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {

    @Binds
    @Singleton
    abstract fun bindOcrEngine(impl: TesseractOcrEngine): OcrEngine
}
