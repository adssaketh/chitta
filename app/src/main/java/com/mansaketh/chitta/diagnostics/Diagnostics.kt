package com.mansaketh.chitta.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small on-phone error log, so a crash or a failed background job can be sent for a look (Settings > Diagnostics).
 * Only the error itself is kept: when it happened, where, and the stack trace. Nothing is sent anywhere; the person chooses to share.
 * The file is capped at [MAX_BYTES]; the oldest part is dropped first.
 */
object Diagnostics {
    private const val MAX_BYTES = 120_000
    private const val KEEP_BYTES = 80_000
    private lateinit var file: File
    private lateinit var app: Context

    /** Call once from Application.onCreate. Also catches crashes: writes the trace, then lets Android handle the crash as usual. */
    fun install(context: Context) {
        app = context.applicationContext
        file = File(app.filesDir, "diagnostics.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record("CRASH on ${thread.name}", error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Adds one entry. Never throws: a broken log must not break the app. */
    @Synchronized
    fun record(where: String, error: Throwable?) {
        runCatching {
            if (!::file.isInitialized) return
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())
            val body = error?.let { "\n" + it.stackTraceToString().take(6_000) } ?: ""
            file.appendText("[$stamp] $where$body\n\n")
            if (file.length() > MAX_BYTES) {
                val text = file.readText()
                file.writeText(text.takeLast(KEEP_BYTES).substringAfter("\n\n", ""))
            }
        }
    }

    /** What to share: phone and app details, then the entries (or a line saying there are none). */
    fun report(context: Context): String {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val version = info?.versionName ?: "unknown"
        @Suppress("DEPRECATION") val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() } ?: 0L
        val entries = runCatching { if (::file.isInitialized && file.exists()) file.readText().trim() else "" }.getOrDefault("")
        return buildString {
            appendLine("Paddhu Pusthakam diagnostics")
            appendLine("App: $version ($code)")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Made: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date()))
            appendLine()
            appendLine(if (entries.isEmpty()) "No errors recorded." else entries.takeLast(40_000))
        }
    }

    fun count(): Int = runCatching { if (::file.isInitialized && file.exists()) file.readText().split("\n\n").count { it.isNotBlank() } else 0 }.getOrDefault(0)

    @Synchronized
    fun clear() { runCatching { if (::file.isInitialized) file.delete() } }
}
