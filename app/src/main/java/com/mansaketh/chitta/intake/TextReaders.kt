package com.mansaketh.chitta.intake

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mansaketh.chitta.core.intake.IntakeFormatException
import com.mansaketh.chitta.core.intake.OcrLine
import com.mansaketh.chitta.core.intake.OcrRows
import com.mansaketh.chitta.core.intake.UpiEntry
import com.mansaketh.chitta.core.intake.UpiParser
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.coroutines.resume

/**
 * Turns a payment screenshot or a statement PDF into text, on the phone. Nothing is sent anywhere: ML Kit's text recogniser used here
 * has its model inside the app. What the text means is decided by [UpiParser] (plain Kotlin, tested with invented text).
 */
object TextReaders {
    private const val MAX_SIDE = 3000

    private fun decode(context: Context, uri: Uri): Bitmap {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > MAX_SIDE) sample *= 2
        val real = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, real) } ?: throw IntakeFormatException("That picture could not be opened.")
    }

    /** Grey and stronger contrast: helps with a thin ₹ sign on a light background. */
    private fun enhanced(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.6f; val shift = (-0.5f * contrast + 0.5f) * 255f
        cm.postConcat(ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, shift, 0f, contrast, 0f, 0f, shift, 0f, 0f, contrast, 0f, shift, 0f, 0f, 0f, 1f, 0f)))
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    /** The top part of the page, where the amount is, enlarged: the amount is read larger and apart from the id lines below it. */
    private fun amountZone(src: Bitmap): Bitmap {
        val h = (src.height * 0.45f).toInt().coerceAtLeast(1)
        val crop = Bitmap.createBitmap(src, 0, 0, src.width, h)
        val scale = if (src.width < 1200) 2 else 1
        return if (scale == 1) crop else Bitmap.createScaledBitmap(crop, crop.width * scale, crop.height * scale, true)
    }

    private suspend fun recognise(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val recogniser = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recogniser.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resume("") }
            .addOnCompleteListener { recogniser.close() }
    }

    /** The picture read as table rows (see [OcrRows]): lines that sit side by side are joined, so a statement row stays in one piece. */
    private suspend fun recogniseRows(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val recogniser = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recogniser.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { t ->
                val lines = t.textBlocks.flatMap { it.lines }.mapNotNull { l -> l.boundingBox?.let { OcrLine(l.text, it.left, it.top, it.bottom) } }
                cont.resume(OcrRows.join(lines))
            }
            .addOnFailureListener { cont.resume("") }
            .addOnCompleteListener { recogniser.close() }
    }

    private const val MAX_OCR_PAGES = 12
    private const val OCR_WIDTH = 1800

    /**
     * A PDF that is only pictures of pages (a printed or screenshot statement) has no text to take out. Each page is drawn by the phone's own
     * PDF renderer and read with the same on-phone text recogniser as a screenshot. Nothing leaves the phone; the temporary copy is deleted.
     * A reading can have mistakes, which is why the statement readers check their own totals before anything is offered.
     */
    private suspend fun ocrPdf(context: Context, bytes: ByteArray): String {
        val file = File(context.cacheDir, "ocr-statement.pdf")
        try {
            file.writeBytes(bytes)
            val out = StringBuilder()
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until minOf(renderer.pageCount, MAX_OCR_PAGES)) {
                        renderer.openPage(i).use { page ->
                            val h = (page.height.toFloat() / page.width * OCR_WIDTH).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(OCR_WIDTH, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            out.append(recogniseRows(bmp)).append('\n')
                            bmp.recycle()
                        }
                    }
                }
            }
            return out.toString()
        } catch (e: SecurityException) {
            throw IntakeFormatException("That PDF is protected, so its pictures could not be read.")
        } catch (e: java.io.IOException) {
            throw IntakeFormatException("That PDF could not be drawn to read its pictures.")
        } finally { file.delete() }
    }

    /**
     * Reads one screenshot two ways (as it is, and with more contrast) and keeps the best reading: one with a rupee sign beats one without, then
     * the amount seen most often wins (UpiParser.chooseBestReading). When no reading had a rupee sign, the enlarged top of the page is read once more for the amount.
     */
    suspend fun readScreenshot(context: Context, uri: Uri): UpiEntry = withContext(Dispatchers.Default) {
        val original = decode(context, uri)
        val readings = listOf<() -> Bitmap>({ original }, { enhanced(original) }).map { make -> UpiParser.parseScreenshot(recognise(make())) }
        val best = UpiParser.chooseBestReading(readings)
            ?: throw IntakeFormatException("This does not look like a successful UPI payment page, or the amount or date could not be read. Nothing was changed.")
        if (!best.amountUnconfirmed) return@withContext best
        // No reading had a rupee sign: look at the enlarged amount zone alone. A signed amount there is trusted.
        val zoneAmount = UpiParser.amountWithRupeeSign(recognise(amountZone(original)))
        if (zoneAmount != null) best.copy(amountPaise = zoneAmount, amountUnconfirmed = false) else best
    }

    /**
     * The text of a shop receipt photo, read twice (as it is, and with more contrast) so that [ReceiptParser] can take the better of the two.
     * A reading that failed is an empty string. The picture is the one already stored on the phone; nothing is sent anywhere.
     */
    suspend fun readReceiptTexts(bitmap: Bitmap): List<String> = withContext(Dispatchers.Default) {
        listOf(recognise(bitmap), recognise(enhanced(bitmap)))
    }

    /** The PDF needs a password, or the one given was wrong. The password is used in memory only and never stored. */
    class PdfPasswordRequired(val wrong: Boolean) : IntakeFormatException(if (wrong) "That password did not open the PDF." else "That PDF has a password.")

    /**
     * All the text of a statement PDF. A scanned picture is refused in plain words. A PDF with a password is opened with [password] when
     * given; without one (or with a wrong one) [PdfPasswordRequired] says so, so the screen can ask.
     */
    suspend fun readPdf(context: Context, uri: Uri, password: String? = null): String = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val bytes = context.contentResolver.openInputStream(uri)?.use { readAll(it) } ?: throw IntakeFormatException("That file could not be opened.")
        val doc = try { PDDocument.load(bytes, password ?: "") } catch (e: Exception) {
            // An encrypted file that will not open with this password: ask for one. Anything else is a broken or unsupported file.
            if (String(bytes, Charsets.ISO_8859_1).contains("/Encrypt")) throw PdfPasswordRequired(wrong = !password.isNullOrEmpty())
            throw IntakeFormatException("That PDF could not be opened.")
        }
        doc.use {
            val text = PDFTextStripper().apply { endPage = 300 }.getText(it)
            if (text.isNotBlank()) return@use text
            // No text layer: the pages are pictures. Read them like screenshots.
            val read = ocrPdf(context, bytes)
            if (read.isBlank()) throw IntakeFormatException("No text could be found in that PDF, even from its pictures. Try the CSV or share a screenshot of each payment.")
            read
        }
    }

    private fun readAll(input: InputStream): ByteArray {
        val bytes = IntakeStore.readLimited(input, IntakeStore.MAX_FILE_BYTES + 1)
        if (bytes.size > IntakeStore.MAX_FILE_BYTES) throw IntakeFormatException("That file is larger than 8 MB.")
        return bytes
    }

    /** The first 8 MB of a picked file, so the caller can tell a PDF from an Excel file by its first bytes. */
    suspend fun readBytes(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { readAll(it) } ?: throw IntakeFormatException("That file could not be opened.")
    }

    suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        String(context.contentResolver.openInputStream(uri)?.use { readAll(it) } ?: throw IntakeFormatException("That file could not be opened."), Charsets.UTF_8)
    }
}
