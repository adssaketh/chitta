package com.mansaketh.chitta.intake

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mansaketh.chitta.core.intake.IntakeFormatException
import com.mansaketh.chitta.core.intake.UpiEntry
import com.mansaketh.chitta.core.intake.UpiParser
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

    /** All the text of a statement PDF. PDFs with a password and scanned pictures are refused in plain words. */
    suspend fun readPdf(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val bytes = context.contentResolver.openInputStream(uri)?.use { readAll(it) } ?: throw IntakeFormatException("That file could not be opened.")
        val doc = try { PDDocument.load(bytes) } catch (e: Exception) {
            throw IntakeFormatException("That PDF could not be opened. If it has a password, save a copy without one and try again.")
        }
        doc.use {
            if (it.isEncrypted) throw IntakeFormatException("That PDF has a password. Save a copy without one and try again.")
            val text = PDFTextStripper().apply { endPage = 300 }.getText(it)
            if (text.isBlank()) throw IntakeFormatException("No text was found in that PDF (it may be a scanned picture). Try the CSV or share a screenshot of each payment.")
            text
        }
    }

    private fun readAll(input: InputStream): ByteArray {
        val bytes = IntakeStore.readLimited(input, IntakeStore.MAX_FILE_BYTES + 1)
        if (bytes.size > IntakeStore.MAX_FILE_BYTES) throw IntakeFormatException("That file is larger than 8 MB.")
        return bytes
    }

    suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        String(context.contentResolver.openInputStream(uri)?.use { readAll(it) } ?: throw IntakeFormatException("That file could not be opened."), Charsets.UTF_8)
    }
}
