# Tesseract trained data

The screen-context OCR fallback (`TesseractOcrEngine`, spec decision `fallback_engine`) needs a
Tesseract language model placed here:

    app/src/main/assets/tessdata/eng.traineddata

It is **not** committed — it is a multi-megabyte binary model, kept out of version control.

## Where to get it (FOSS, F-Droid-compatible)

Download `eng.traineddata` from the official tessdata repositories (Apache-2.0):

- Best accuracy:   https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata
- Balanced (LSTM): https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata
- Smallest/fast:   https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata

`tessdata_fast` is the recommended starting point for an on-device assistant (smallest APK impact).

## Behaviour without the model

`TesseractOcrEngine.recognise` returns `""` when the model is absent, so the screen-context flow
degrades to the blank-screen path (an honest "couldn't read this screen" note) rather than crashing.
Add the model before relying on the OCR fallback at runtime.
