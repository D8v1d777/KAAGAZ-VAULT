package com.kaagazvault.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal data class OcrResult(
    val text: String,
    val meanConfidence: Int,
    val languages: String
)

/**
 * Offline Tesseract engine with bundled English, Hindi (Devanagari), and Telugu models.
 * Recognition must run on a worker thread. OCR output is untrusted evidence, never an action.
 */
internal class OfflineOcrEngine(private val context: Context) {
    fun recognizeImage(bytes: ByteArray): OcrResult {
        val dataRoot = ensureModelsAvailable()
        val bitmap = decodeBoundedBitmap(bytes)
        val api = try {
            TessBaseAPI()
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
        try {
            api.setDebug(false)
            if (!api.init(dataRoot.absolutePath, LANGUAGES, TessBaseAPI.OEM_LSTM_ONLY)) {
                throw IOException("Offline OCR models could not be initialized")
            }
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            api.setImage(bitmap)
            val text = api.getUTF8Text().orEmpty().trim()
            return OcrResult(
                text = text,
                meanConfidence = api.meanConfidence().coerceIn(0, 100),
                languages = LANGUAGES
            )
        } finally {
            api.recycle()
            bitmap.recycle()
        }
    }

    private fun ensureModelsAvailable(): File {
        val root = File(context.filesDir, "tesseract")
        val directory = File(root, "tessdata")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not prepare offline OCR models")
        }

        MODEL_BLOB_SHAS.forEach { (name, expectedSha) ->
            val target = File(directory, name)
            if (target.isFile && gitBlobSha(target.readBytes()) == expectedSha) return@forEach

            val temporary = File(directory, ".$name.pending")
            try {
                context.assets.open("tessdata/$name").use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                if (gitBlobSha(temporary.readBytes()) != expectedSha) {
                    throw IOException("Bundled OCR model integrity check failed")
                }
                if (target.exists() && !target.delete()) {
                    throw IOException("Could not replace local OCR model")
                }
                if (!temporary.renameTo(target)) {
                    throw IOException("Could not install local OCR model")
                }
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
        return root
    }

    private fun gitBlobSha(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
        digest.update(bytes)
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun decodeBoundedBitmap(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Image could not be decoded for OCR")
        }

        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IOException("Image could not be decoded for OCR")
    }

    private companion object {
        const val LANGUAGES = "eng+hin+tel"
        const val MAX_DIMENSION = 2400
        val MODEL_BLOB_SHAS = mapOf(
            "eng.traineddata" to "bbef4675053b5b468cdb477053e28b1c698ba08e",
            "hin.traineddata" to "a8f0aaec09378115b12a2c6287c57695d082aea2",
            "tel.traineddata" to "ee8a33b0f658fc75b8cbfe8c10413eac4f41e6ad"
        )
    }
}
