package com.mansaketh.chitta.intake

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.mansaketh.chitta.MainActivity
import java.io.File

/**
 * Receives what is shared to Chitta: a Ping JSON (one message as text, or a date-range file), or a payment screenshot, statement PDF or
 * statement CSV from any app. It has no screen: it keeps a copy, then opens Chitta on the review list. Nothing is saved here.
 *
 * Ping starts it by name (`com.mansaketh.paddhupusthakam.ShareReceiverActivity`, SPEC section 4), which the manifest keeps as an alias.
 */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handle(intent)
        } catch (e: Throwable) {
            Toast.makeText(this, "Could not read the shared file: ${e.message}", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    @Suppress("DEPRECATION")
    private fun handle(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND) return
        var uri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        if (uri == null) {
            val clip = intent.clipData
            if (clip != null && clip.itemCount > 0) uri = clip.getItemAt(0).uri
        }
        val type = intent.type?.lowercase().orEmpty()
        val kind = when {
            type.startsWith("image/") -> "image"
            type == "application/pdf" -> "pdf"
            type == "text/csv" || type == "text/comma-separated-values" || type == "application/csv" -> "csv"
            else -> null
        }
        if (kind != null) {
            if (uri == null) error("nothing was shared")
            val target = File(cacheDir, IntakeStore.sharedName(kind))
            copy(uri, target)
            open("upi:$kind")
            return
        }
        // Ping: a JSON file, or one message as a small JSON text. Both go to the inbox, which is read when Chitta opens.
        val text: String = if (uri != null) {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("could not open the shared file")
            if (bytes.size > IntakeStore.MAX_FILE_BYTES) error("that file is larger than 8 MB")
            String(bytes, Charsets.UTF_8)
        } else {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.trim().startsWith("{") } ?: error("nothing was shared")
        }
        IntakeStore.writeToInbox(this, text)
        open("review")
    }

    private fun copy(from: Uri, to: File) {
        val input = contentResolver.openInputStream(from) ?: error("could not open the shared file")
        input.use { i -> to.outputStream().use { o ->
            val buf = ByteArray(65536); var total = 0L
            while (true) {
                val n = i.read(buf); if (n < 0) break
                total += n; if (total > IntakeStore.MAX_FILE_BYTES) error("that file is larger than 8 MB")
                o.write(buf, 0, n)
            }
        } }
    }

    private fun open(what: String) {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_OPEN, what))
    }

    companion object { const val EXTRA_OPEN = "chitta.intake.open" }
}
