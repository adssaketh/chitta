package com.mansaketh.chitta.intake

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.mansaketh.chitta.core.intake.PingContract
import com.mansaketh.chitta.core.intake.PingExport
import com.mansaketh.chitta.core.intake.PingSmsRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Everything Chitta asks of the Ping app: opening a message, and business logos. Both do nothing when Ping is missing or is not the real Ping. */
object PingBridge {
    /** True when Ping is installed and signed with the pinned certificate. */
    fun isRealPing(context: Context): Boolean = try {
        PingInboxProvider.callerHasPinnedCertificate(context, PingContract.PACKAGE)
    } catch (e: Throwable) { false }

    /** The "View SMS in Ping" intent for an entry note, or null when the note says too little to find the message again, or Ping is missing. */
    fun viewIntent(context: Context, note: String): Intent? = viewIntent(context, PingExport.refOfNote(note))

    fun viewIntent(context: Context, ref: PingSmsRef?): Intent? {
        if (ref == null || !isRealPing(context)) return null
        val intent = Intent(PingContract.ACTION_OPEN_SMS).setPackage(PingContract.PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ref.smsId?.let { intent.putExtra("smsId", it) }
        ref.address?.let { intent.putExtra("address", it) }
        ref.dateMs?.let { intent.putExtra("date", it) }
        return if (intent.resolveActivity(context.packageManager) != null) intent else null
    }

    fun open(context: Context, note: String): Boolean {
        val intent = viewIntent(context, note) ?: return false
        return try { context.startActivity(intent); true } catch (e: Exception) { false }
    }

    private val memory = HashMap<String, Pair<Long, Bitmap?>>()

    /**
     * Ping's logo for a brand key or a business sender, or null. Before reading, the provider is checked to belong to Ping and Ping to carry the
     * pinned certificate. Logos are kept for 24 hours only, as Ping asks (SPEC section 10); a miss is kept for 24 hours too, so a message
     * list does not ask Ping again for every row.
     */
    suspend fun logo(context: Context, brandKey: String?, sender: String?): Bitmap? = withContext(Dispatchers.IO) {
        val address = PingContract.logoUri(brandKey, sender) ?: return@withContext null
        val now = System.currentTimeMillis()
        synchronized(memory) { memory[address]?.takeIf { now - it.first < PingContract.LOGO_CACHE_MS }?.let { return@withContext it.second } }
        val file = File(context.cacheDir, "ping-logos/" + PingExport.stableHashOf(address) + ".img")
        if (file.isFile && now - file.lastModified() < PingContract.LOGO_CACHE_MS) {
            val cached = BitmapFactory.decodeFile(file.path)
            synchronized(memory) { memory[address] = file.lastModified() to cached }
            return@withContext cached
        }
        file.delete()
        val bytes = try {
            val provider = context.packageManager.resolveContentProvider(PingContract.LOGO_AUTHORITY, 0)
            if (provider == null || provider.packageName != PingContract.PACKAGE || !isRealPing(context)) null
            else context.contentResolver.openInputStream(Uri.parse(address))?.use { IntakeStore.readLimited(it, PingContract.MAX_LOGO_BYTES + 1) }
        } catch (e: Exception) { null }
        val bitmap = bytes?.takeIf { it.size in 1..PingContract.MAX_LOGO_BYTES }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bitmap != null && bytes != null) {
            file.parentFile?.mkdirs()
            try { file.writeBytes(bytes) } catch (e: Exception) { /* the cache is only a convenience */ }
        }
        synchronized(memory) { memory[address] = now to bitmap }
        bitmap
    }
}
