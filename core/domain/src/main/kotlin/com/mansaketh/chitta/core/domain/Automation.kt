package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import java.security.MessageDigest
import java.time.ZoneId

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
