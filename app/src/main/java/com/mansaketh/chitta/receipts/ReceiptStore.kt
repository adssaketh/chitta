package com.mansaketh.chitta.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receipt photos (DECISIONS D152 to D156): one JPEG per entry in the app's private storage, named by the entry id. Nothing is kept in
 * the database, so a backup file or restore point never carries a photo and an entry without its file simply shows "None". The copy
 * made here is re-drawn from the picture's pixels, so the camera's location and other hidden details are not kept.
 */
@Singleton
class ReceiptStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "receipts").apply { mkdirs() }
    private val tmp get() = File(context.cacheDir, "receipt_tmp").apply { mkdirs() }

    /** An id is a uuid in practice; anything else is cut down to safe characters so an id can never name another folder. */
    private fun name(entryId: String) = entryId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "x" }
    fun file(entryId: String) = File(dir, name(entryId) + ".jpg")
    fun has(entryId: String) = file(entryId).let { it.isFile && it.length() > 0 }

    private fun captureFile() = File(tmp, "capture.jpg")

    /** The address the camera app writes its picture to; an earlier picture there is cleared first. */
    fun newCaptureUri(): Uri {
        val f = captureFile().apply { delete() }
        return FileProvider.getUriForFile(context, "${context.packageName}.receipts", f)
    }

    /** The same address, after the camera has written to it (nothing is cleared). */
    fun capturedUri(): Uri = FileProvider.getUriForFile(context, "${context.packageName}.receipts", captureFile())

    /**
     * Keeps a picture (from the camera or the gallery) as this entry's receipt: turned upright, at most [MAX_SIDE] pixels on the long
     * side, JPEG at quality 85. An older receipt is replaced only after the new one is written. Returns false when the picture cannot be read.
     */
    suspend fun attach(entryId: String, source: Uri): Boolean = withContext(Dispatchers.IO) {
        val bitmap = decode(source) ?: return@withContext false
        val target = file(entryId)
        val part = File(dir, target.name + ".part")
        try {
            part.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) return@withContext false }
            if (!part.renameTo(target)) { part.copyTo(target, overwrite = true) }
            true
        } finally { part.delete(); bitmap.recycle() }
    }

    private fun decode(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val turn = try { resolver.openInputStream(uri)?.use { orientation(it) } ?: 0 } catch (e: Exception) { 0 }
        val longSide = maxOf(raw.width, raw.height)
        val scale = if (longSide > MAX_SIDE) MAX_SIDE.toFloat() / longSide else 1f
        if (turn == 0 && scale == 1f) return raw
        val m = Matrix().apply { postRotate(turn.toFloat()); postScale(scale, scale) }
        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (out !== raw) raw.recycle()
        return out
    }

    /** Degrees to turn the picture so it stands upright, from the camera's own note in the file. */
    private fun orientation(input: java.io.InputStream): Int = when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    /** The stored picture for showing or reading, or null. */
    suspend fun load(entryId: String): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(entryId)
        if (!f.isFile) null else BitmapFactory.decodeFile(f.path)
    }

    /** Takes the receipt off the entry but keeps the picture aside so Undo can bring it back. */
    fun removeKeepingCopy(entryId: String): Boolean {
        val f = file(entryId)
        if (!f.isFile) return false
        return f.renameTo(File(tmp, "removed-${f.name}"))
    }

    fun restoreRemoved(entryId: String) {
        val kept = File(tmp, "removed-${file(entryId).name}")
        if (kept.isFile) kept.renameTo(file(entryId))
    }

    /** Moves a receipt to another entry (a split gives it to its first line). Does nothing when there is none or the target has one. */
    fun move(from: String, to: String) {
        val a = file(from); val b = file(to)
        if (a.isFile && !b.exists()) a.renameTo(b)
    }

    /** Every receipt photo and every temporary copy: part of Delete everything. */
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
        tmp.listFiles()?.forEach { it.delete() }
    }

    /**
     * Removes photos whose entry no longer exists, but only once they are older than [GRACE_DAYS] days, so Undo, "Restore this version"
     * on a deleted entry and a restore point still find their photo for a month. Returns how many were removed.
     */
    fun cleanOrphans(liveIds: Set<String>, nowMs: Long): Int {
        val live = liveIds.map(::name).toSet()
        var removed = 0
        dir.listFiles()?.forEach { f ->
            val stem = f.name.removeSuffix(".jpg").removeSuffix(".part")
            if (stem !in live && nowMs - f.lastModified() > GRACE_DAYS * DAY_MS && f.delete()) removed++
        }
        // Leftovers of Undo and of the camera are only useful for seconds.
        tmp.listFiles()?.forEach { if (nowMs - it.lastModified() > DAY_MS) it.delete() }
        return removed
    }

    companion object {
        const val MAX_SIDE = 2000
        const val GRACE_DAYS = 30
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
