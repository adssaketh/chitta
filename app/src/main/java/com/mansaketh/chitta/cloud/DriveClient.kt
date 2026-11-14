package com.mansaketh.chitta.cloud

import com.mansaketh.chitta.core.domain.DriveFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Drive said no. [code] is the HTTP status; [message] is plain words for the person. */
class DriveHttpException(val code: Int, message: String) : IOException(message)

/** Reads Drive's JSON. Kept apart from the network so it can be tested with sample answers. */
object DriveJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun file(o: JsonObject): DriveFile {
        val modified = o["modifiedTime"]?.jsonPrimitive?.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        return DriveFile(
            id = o.getValue("id").jsonPrimitive.content,
            name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            sizeBytes = o["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            modifiedMs = modified,
        )
    }

    /** One page of a file list: the files and the token for the next page, if any. */
    fun page(body: String): Pair<List<DriveFile>, String?> {
        val root = json.parseToJsonElement(body).jsonObject
        val files = root["files"]?.jsonArray?.map { file(it.jsonObject) }.orEmpty()
        return files to root["nextPageToken"]?.jsonPrimitive?.contentOrNull
    }

    fun one(body: String): DriveFile = file(json.parseToJsonElement(body).jsonObject)

    /** Plain words for a failed call, from the status and Drive's own error text. */
    fun explain(code: Int, body: String): String = when {
        code == 401 -> "Google no longer accepts Chitta's Drive access. Try again."
        code == 403 && (body.contains("accessNotConfigured") || body.contains("SERVICE_DISABLED") || body.contains("has not been used")) ->
            "The Google Drive API is switched off for this Google Cloud project. Turn it on in the Cloud console, then try again."
        code == 403 && body.contains("storageQuotaExceeded") ->
            "Your Google Drive is full, so the backup was not saved."
        code == 403 -> "Google refused the Drive request."
        code == 404 -> "That backup is no longer in Drive."
        code == 429 -> "Google asked Chitta to wait. It will try again later."
        code >= 500 -> "Google Drive had a problem. It will try again later."
        else -> "Drive answered with an error ($code)."
    }
}

/**
 * Talks to the Drive REST API over HTTPS for the app's hidden folder only (spaces=appDataFolder). No Drive library: five small
 * calls, and the APK stays small. Every call blocks, so run it off the main thread.
 */
@Singleton
class DriveClient @Inject constructor() {
    private val base = "https://www.googleapis.com"

    fun list(token: String): List<DriveFile> {
        val all = ArrayList<DriveFile>()
        var page: String? = null
        do {
            val fields = URLEncoder.encode("nextPageToken,files(id,name,size,modifiedTime)", "UTF-8")
            val url = "$base/drive/v3/files?spaces=appDataFolder&pageSize=100&fields=$fields" + (page?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") } ?: "")
            val (files, next) = DriveJson.page(get(url, token))
            all += files; page = next
        } while (page != null)
        return all
    }

    /** Uploads [file] into the hidden folder as [name] and returns what Drive now holds. Resumable, so a large book is not a problem. */
    fun upload(token: String, name: String, file: File): DriveFile {
        val length = file.length()
        val meta = """{"name":${JsonPrimitive(name)},"parents":["appDataFolder"]}"""
        val start = open("$base/upload/drive/v3/files?uploadType=resumable&fields=" + URLEncoder.encode("id,name,size,modifiedTime", "UTF-8"), "POST", token).apply {
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", "application/octet-stream")
            setRequestProperty("X-Upload-Content-Length", length.toString())
            doOutput = true
            outputStream.use { it.write(meta.toByteArray(Charsets.UTF_8)) }
        }
        val session = try {
            check(start)
            start.getHeaderField("Location") ?: throw DriveHttpException(0, "Drive did not accept the upload.")
        } finally { start.disconnect() }

        val put = open(session, "PUT", token).apply {
            setRequestProperty("Content-Type", "application/octet-stream")
            doOutput = true
            setFixedLengthStreamingMode(length)
            readTimeout = 180_000
            outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
        }
        return try { check(put); DriveJson.one(put.inputStream.bufferedReader().readText()) } finally { put.disconnect() }
    }

    /** Downloads a file to [dest] (written to a temporary name first, so a cut connection leaves no half file). */
    fun download(token: String, id: String, dest: File) {
        val conn = open("$base/drive/v3/files/${URLEncoder.encode(id, "UTF-8")}?alt=media", "GET", token).apply { readTimeout = 180_000 }
        val tmp = File(dest.path + ".part")
        try {
            check(conn)
            tmp.parentFile?.mkdirs()
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
        } finally { conn.disconnect(); tmp.delete() }
    }

    fun delete(token: String, id: String) {
        val conn = open("$base/drive/v3/files/${URLEncoder.encode(id, "UTF-8")}", "DELETE", token)
        try { check(conn) } finally { conn.disconnect() }
    }

    private fun get(url: String, token: String): String {
        val conn = open(url, "GET", token)
        return try { check(conn); conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
    }

    private fun open(url: String, method: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    /** Throws [DriveHttpException] with plain words unless the answer is a success. */
    private fun check(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code in 200..299) return
        val body = runCatching { conn.errorStream?.bufferedReader()?.readText().orEmpty() }.getOrDefault("")
        throw DriveHttpException(code, DriveJson.explain(code, body))
    }
}
