package com.kaagazvault.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.googlecode.tesseract.android.TessBaseAPI
import io.legere.pdfiumandroid.PdfiumCore
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class OcrResult(
    val text: String,
    val meanConfidence: Int?,
    val languages: String,
    val truncated: Boolean = false
)

/**
 * Offline OCR with pinned English, Hindi, and Telugu models.
 * PDF input is processed from memory: native text is extracted when present and scanned pages
 * are rendered one at a time for OCR. No decrypted PDF/page is written to disk.
 */
internal class OfflineOcrEngine(private val context: Context) {
    fun recognizeImage(bytes: ByteArray): OcrResult {
        val dataRoot = ensureModelsAvailable()
        val bitmap = decodeBoundedBitmap(bytes)
        val api = createTessApi(dataRoot)
        try {
            api.setImage(bitmap)
            val rawText = api.getUTF8Text().orEmpty().trim()
            return OcrResult(
                text = sanitizeText(rawText).take(MAX_OCR_TEXT_CHARS),
                meanConfidence = api.meanConfidence().coerceIn(0, 100),
                languages = LANGUAGES,
                truncated = rawText.length > MAX_OCR_TEXT_CHARS
            )
        } finally {
            api.recycle()
            bitmap.recycle()
        }
    }

    @Throws(IOException::class)
    fun recognizePdf(bytes: ByteArray): OcrResult {
        if (bytes.isEmpty()) throw IOException("The PDF is empty")
        if (bytes.size > MAX_PDF_BYTES) throw IOException("PDF exceeds the offline OCR size limit")

        val core = PdfiumCore(context)
        val document = core.newDocument(bytes)
        var tessApi: TessBaseAPI? = null
        var modelRoot: File? = null
        val combined = StringBuilder()
        var truncated = false
        var totalPixels = 0L
        var confidenceTotal = 0L
        var confidencePages = 0

        fun appendPageText(pageNumber: Int, rawText: String) {
            val safeText = sanitizeText(rawText).trim()
            if (safeText.isEmpty()) return
            if (combined.isNotEmpty()) combined.append("\n\n")
            combined.append("[Page ").append(pageNumber + 1).append("]\n")
            val remaining = MAX_OCR_TEXT_CHARS - combined.length
            if (remaining <= 0) {
                truncated = true
                return
            }
            if (safeText.length > remaining) {
                combined.append(safeText, 0, remaining)
                truncated = true
            } else {
                combined.append(safeText)
            }
        }

        fun getTessApi(): TessBaseAPI {
            tessApi?.let { return it }
            val root = modelRoot ?: ensureModelsAvailable().also { modelRoot = it }
            val created = createTessApi(root)
            tessApi = created
            return created
        }

        try {
            val pageCount = document.getPageCount()
            if (pageCount <= 0) throw IOException("PDF contains no readable pages")
            val pagesToProcess = min(pageCount, MAX_PDF_PAGES)
            if (pageCount > pagesToProcess) truncated = true

            for (pageIndex in 0 until pagesToProcess) {
                if (combined.length >= MAX_OCR_TEXT_CHARS) {
                    truncated = true
                    break
                }

                val page = document.openPage(pageIndex)
                    ?: throw IOException("Could not open PDF page")
                try {
                    val nativeTextPage = page.openTextPage()
                    val nativeText: String
                    try {
                        val characterCount = nativeTextPage.textPageCountChars()
                        val boundedCount = min(characterCount, MAX_NATIVE_TEXT_CHARS_PER_PAGE)
                        if (characterCount > boundedCount) truncated = true
                        nativeText = if (boundedCount > 0) {
                            nativeTextPage.textPageGetText(0, boundedCount).orEmpty().trim()
                        } else {
                            ""
                        }
                    } finally {
                        nativeTextPage.close()
                    }

                    // Prefer embedded text; rasterize only scanned/image-only pages.
                    if (nativeText.length >= MIN_NATIVE_TEXT_CHARS) {
                        appendPageText(pageIndex, nativeText)
                        continue
                    }

                    val sourceWidth = page.getPageWidth(RENDER_DPI).coerceAtLeast(1)
                    val sourceHeight = page.getPageHeight(RENDER_DPI).coerceAtLeast(1)
                    val scale = min(
                        1f,
                        MAX_RENDER_DIMENSION.toFloat() / max(sourceWidth, sourceHeight).toFloat()
                    )
                    val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
                    val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
                    val pagePixels = width.toLong() * height.toLong()
                    if (totalPixels + pagePixels > MAX_TOTAL_RENDERED_PIXELS) {
                        truncated = true
                        break
                    }
                    totalPixels += pagePixels

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        page.renderPageBitmap(
                            bitmap,
                            0,
                            0,
                            width,
                            height,
                            canvasColor = Color.WHITE,
                            pageBackgroundColor = Color.WHITE
                        )
                        val api = getTessApi()
                        api.setImage(bitmap)
                        val pageText = api.getUTF8Text().orEmpty().trim()
                        confidenceTotal += api.meanConfidence().coerceIn(0, 100)
                        confidencePages++
                        appendPageText(pageIndex, pageText)
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    page.close()
                }
            }

            return OcrResult(
                text = combined.toString().take(MAX_OCR_TEXT_CHARS),
                meanConfidence = if (confidencePages == 0) null else (confidenceTotal / confidencePages).toInt(),
                languages = if (confidencePages == 0) "PDF text layer" else LANGUAGES,
                truncated = truncated || combined.length > MAX_OCR_TEXT_CHARS
            )
        } finally {
            tessApi?.recycle()
            document.close()
        }
    }

    private fun createTessApi(dataRoot: File): TessBaseAPI {
        val api = TessBaseAPI()
        try {
            api.setDebug(false)
            if (!api.init(dataRoot.absolutePath, LANGUAGES, TessBaseAPI.OEM_LSTM_ONLY)) {
                throw IOException("Offline OCR models could not be initialized")
            }
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            return api
        } catch (error: Throwable) {
            api.recycle()
            throw error
        }
    }

    private fun sanitizeText(value: String): String =
        value.filter { it == '\n' || it == '\r' || it == '\t' || !it.isISOControl() }

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
        while (bounds.outWidth / sample > MAX_RENDER_DIMENSION || bounds.outHeight / sample > MAX_RENDER_DIMENSION) {
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
        const val MAX_RENDER_DIMENSION = 2000
        const val MAX_OCR_TEXT_CHARS = 150_000
        const val MAX_PDF_BYTES = 20 * 1024 * 1024
        const val MAX_PDF_PAGES = 30
        const val MAX_NATIVE_TEXT_CHARS_PER_PAGE = 10_000
        const val MIN_NATIVE_TEXT_CHARS = 20
        const val RENDER_DPI = 144
        const val MAX_TOTAL_RENDERED_PIXELS = 60_000_000L
        val MODEL_BLOB_SHAS = mapOf(
            "eng.traineddata" to "bbef4675053b5b468cdb477053e28b1c698ba08e",
            "hin.traineddata" to "a8f0aaec09378115b12a2c6287c57695d082aea2",
            "tel.traineddata" to "ee8a33b0f658fc75b8cbfe8c10413eac4f41e6ad"
        )
    }
}
