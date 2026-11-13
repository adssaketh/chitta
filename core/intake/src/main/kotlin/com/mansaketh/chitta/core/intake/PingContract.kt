package com.mansaketh.chitta.core.intake

import java.net.URLEncoder
import java.security.MessageDigest

/** The fixed facts of the Ping / Paddhu contract (contract/ping-paddhu/SPEC.md sections 4, 7 and 10). Plain Kotlin so it can be tested. */
object PingContract {
    const val PACKAGE = "com.ping.sms"
    const val ACTION_OPEN_SMS = "com.ping.sms.action.OPEN_SMS"
    const val LOGO_AUTHORITY = "com.ping.sms.logos"

    /**
     * SHA-256 of Ping's release signing certificate (SPEC section 7). The caller must have the right package name AND this certificate.
     * More than one digest may be listed, for a key change.
     */
    val PINNED_CERT_SHA256: List<String> = listOf("12cb1f4ff2ddcd91feda62d09f555ccc6c10a8f270c031908e6ee38dec20a502")

    const val MAX_JSON_CHARS = 20_000
    const val MAX_INBOX_FILES = 2_000
    const val MAX_LOGO_BYTES = 600 * 1024
    /** Logos Ping fetched online may not be kept longer than this (SPEC section 10). */
    const val LOGO_CACHE_MS = 24L * 60 * 60 * 1000

    /** Lower-case hex SHA-256 of the DER certificate bytes. */
    fun sha256Hex(certificate: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** True when the certificate's digest equals one of [pinned] (hex, any case, optional ':' separators). An empty list refuses everybody. */
    fun isPinned(certificate: ByteArray?, pinned: List<String> = PINNED_CERT_SHA256): Boolean {
        if (certificate == null || certificate.isEmpty()) return false
        val actual = sha256Hex(certificate)
        return pinned.any { actual == it.replace(":", "").trim().lowercase() }
    }

    /** True when every signer is pinned (a second, unknown signer is refused) and there is at least one. */
    fun allPinned(signers: List<ByteArray>?, pinned: List<String> = PINNED_CERT_SHA256): Boolean =
        !signers.isNullOrEmpty() && signers.all { isPinned(it, pinned) }

    /** The logo address for a brand key (preferred) or a business sender header, or null when neither is usable. */
    fun logoUri(brandKey: String?, sender: String?): String? {
        PingExport.validBrandKey(brandKey).takeIf { it.isNotEmpty() }?.let { return "content://$LOGO_AUTHORITY/brand/$it" }
        val s = sender?.trim().orEmpty()
        if (!Regex("^[A-Za-z0-9._&-]{1,32}\\z").matches(s) || s.all { it.isDigit() }) return null
        return "content://$LOGO_AUTHORITY/sender/" + URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}
