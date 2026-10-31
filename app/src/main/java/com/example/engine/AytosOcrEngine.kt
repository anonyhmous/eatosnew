package com.example.engine

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** رقم اتقرا من الشاشة بالـ OCR ومعاه حجمه ومكانه (الرقم الأكبر غالباً هو السعر). */
data class OcrNumber(
    val value: Double,
    val text: String,
    val heightPx: Int,
    val topPx: Int
)

/** ناتج الـ OCR: النص الكامل + الأرقام لوحدها. */
data class OcrOutput(
    val text: String,
    val numbers: List<OcrNumber>
)

/**
 * قارئ الـ OCR (Google ML Kit، بيشتغل على الموبايل نفسه من غير نت).
 * الصورة بتتقرا في الذاكرة بس وبتتمسح، ومبتتحفظش في أي مكان.
 * ملحوظة: النموذج ده بيقرا الأرقام والحروف اللاتينية كويس، لكن مش الحروف العربية،
 * عشان كده الاعتماد الأساسي على حجم الأرقام ومكانها مش على الكلمات.
 */
class AytosOcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun recognize(bitmap: Bitmap, onDone: (OcrOutput?) -> Unit) {
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    val numbers = mutableListOf<OcrNumber>()
                    for (block in result.textBlocks) {
                        for (line in block.lines) {
                            for (element in line.elements) {
                                val clean = element.text.replace(',', '.')
                                if (!NUMBER.matches(clean)) continue
                                val value = clean.toDoubleOrNull() ?: continue
                                val box = element.boundingBox ?: continue
                                numbers.add(OcrNumber(value, clean, box.height(), box.top))
                            }
                        }
                    }
                    onDone(OcrOutput(result.text, numbers))
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "OCR failed", e)
                    onDone(null)
                }
        } catch (e: Exception) {
            Log.w(TAG, "OCR could not start", e)
            onDone(null)
        }
    }

    fun close() {
        try {
            recognizer.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "AytosOcr"
        private val NUMBER = Regex("^[0-9]+(?:\\.[0-9]{1,2})?$")
    }
}
