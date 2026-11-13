package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import java.security.MessageDigest
import java.time.ZoneId

/**
 * Notification scanning (master section 7). Chitta reads only the notifications of apps the person ticked, keeps only those that
 * mention rupees, and hands the text to the same bank-message reader and review list as Ping (D99). Nothing is saved here.
 */
object NotificationScan {
    /** A notification turned into the shape the Ping inbox understands. */
    data class Message(val id: String, val sender: String, val body: String, val dateMs: Long)

    private val moneyRe = Regex("(?i)(₹|\\brs\\.?|\\binr)\\s?[0-9]")
    private const val MAX_BODY = 600

    /** Null when the app is not ticked, it is Chitta itself, or the text does not mention an amount in rupees. */
    fun accept(packageName: String, title: String?, text: String?, postedMs: Long, allowed: Set<String>, ownPackage: String): Message? {
        if (packageName == ownPackage || packageName !in allowed) return null
        val t = title?.trim().orEmpty()
        val x = text?.trim().orEmpty()
        if (x.isEmpty()) return null
        val body = (if (t.isEmpty() || x.contains(t, ignoreCase = true)) x else "$t. $x").take(MAX_BODY)
        if (!moneyRe.containsMatchIn(body)) return null
        val minute = postedMs / 60_000
        return Message("nt-" + hash("$packageName|$body|$minute"), senderOf(packageName), body, postedMs)
    }

    /** A sender id made only of letters, digits and . _ - (what the inbox accepts), at most 32 characters. */
    fun senderOf(packageName: String): String = packageName.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }.takeLast(32).ifEmpty { "app" }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).take(6).joinToString("") { "%02x".format(it) }

    /** True for a package name like com.bank.app: dots, letters, digits, underscores, at least one dot. */
    fun looksLikePackage(s: String): Boolean = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(s.trim())
}

/**
 * The background receiver (master section 7). Another app may send an "add" broadcast; Chitta accepts it only with the secret the
 * person set, and only when the amount, a known account and a known category are all clear. Anything less is refused with a notice,
 * never guessed (D99). Transfers are not accepted. The entry is saved with Undo for 10 seconds.
 */
object BroadcastAdd {
    const val MIN_SECRET = 8

    /** Same-time comparison, and never true when no secret (or a short one) is set: the receiver is off until the person makes a secret. */
    fun secretOk(stored: String?, given: String?): Boolean {
        if (stored == null || stored.length < MIN_SECRET || given == null) return false
        return MessageDigest.isEqual(stored.toByteArray(Charsets.UTF_8), given.toByteArray(Charsets.UTF_8))
    }

    sealed interface Result {
        data class Ready(val draft: QuickAddDraft, val notes: List<String>) : Result
        data class Rejected(val reason: String) : Result
    }

    /** Reads the same fields as an app link (amount, title, category, account, type, note, date). */
    fun parse(params: Map<String, String>, accounts: List<Account>, categories: List<Category>, nowMs: Long, zone: ZoneId): Result {
        // an app link falls back to "expense" for a type it does not know; a background add must not guess a type, so it refuses
        val type = params["type"]?.trim()?.lowercase()
        if (!type.isNullOrEmpty() && type != "expense" && type != "income") return Result.Rejected("Only expense and income can be added this way.")
        val r = AppLinks.parse(params - "secret", accounts, categories, nowMs, zone)
        val d = r.draft
        val amount = AmountExpression.evaluate(d.amountRaw)
        if (amount == null || amount.isZero) return Result.Rejected("The amount was missing or not above zero.")
        if (d.accountId == null) return Result.Rejected("The account was missing or not one of yours.")
        if (d.categoryId == null) return Result.Rejected("The category was missing or not one of yours.")
        return Result.Ready(d, r.notes)
    }

    /** A new secret: 16 random bytes as 32 letters and digits, shown once to copy into the other app. */
    fun newSecret(random: java.security.SecureRandom = java.security.SecureRandom()): String {
        val bytes = ByteArray(16).also { random.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
