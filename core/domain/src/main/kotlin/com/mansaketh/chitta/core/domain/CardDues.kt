package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.CardDue
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import kotlin.math.abs

/**
 * What you owe on a credit card, worked out from bank messages (master section 7, "Credit-card dues from SMS"). Behaviour of Paddhu's
 * `cardDues.dart`: a spend message adds to what is owed; a payment or refund message takes from it; an "opening" entry is a correction
 * typed by hand and may be negative. A payment message is never saved as an entry (the money leaving the bank is its own message), so these
 * rows are kept on the side.
 *
 * The cockpit (see [Cards.figures]) counts them as well, so SMS-read spending and payments feed the same outstanding figure. To stop one
 * purchase being counted twice, a message is left out of the cockpit when an entry on the card already covers it: the same message
 * (the entry's note carries `Ping SMS key <key>`), or, for a payment, a positive entry of the same amount within [PAYMENT_WINDOW_DAYS].
 */
object CardDues {
    const val PAYMENT_WINDOW_DAYS = 3L
    private const val DAY_MS = 86_400_000L

    /** One message as the cockpit sees it: [signed] is negative for money owed more (like an entry on the card), positive for payments and refunds. */
    data class Flow(val dueId: String, val dateMs: Long, val signed: Long)

    /** Positive = owed more. */
    fun effect(d: CardDue): Long = when (d.kind) {
        "spend", "opening" -> d.amount
        else -> -d.amount // payment, refund, and any kind we do not know reduce what is owed
    }

    /** Owed per card name; below zero when more was paid than spent (a credit). */
    fun owedByCard(dues: List<CardDue>): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for (d in dues) out[d.card] = Math.addExact(out[d.card] ?: 0L, effect(d))
        return out
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** The messages whose card name is this account's name (ignoring case, spaces and punctuation). */
    fun forAccount(account: Account, dues: List<CardDue>): List<CardDue> = dues.filter { norm(it.card) == norm(account.name) && norm(account.name).isNotEmpty() }

    /** Cards that have messages but no account with the same name: shown on their own, not in a cockpit. */
    fun unmatchedCards(dues: List<CardDue>, accounts: List<Account>): List<String> {
        val names = accounts.filter { !it.archived }.map { norm(it.name) }.toSet()
        return dues.map { it.card }.distinct().filter { norm(it) !in names }
    }

    private fun keyOf(dueId: String) = dueId.removePrefix("cd-")

    /** What the cockpit adds for [account]: its messages that no entry on the card already covers. */
    fun flowsFor(account: Account, dues: List<CardDue>, entries: List<Transaction>): List<Flow> {
        val mine = entries.filter { it.accountId == account.id && it.paid && !it.skipPaid }
        val usedEntries = HashSet<String>()
        return forAccount(account, dues).sortedBy { it.dateMs }.mapNotNull { d ->
            val key = keyOf(d.id)
            if (key.isNotEmpty() && mine.any { it.note.contains("Ping SMS key $key") }) return@mapNotNull null
            if (d.kind == "payment") {
                val twin = mine.firstOrNull { it.id !in usedEntries && it.amount == d.amount && abs(it.dateMs - d.dateMs) <= PAYMENT_WINDOW_DAYS * DAY_MS }
                if (twin != null) { usedEntries += twin.id; return@mapNotNull null }
            }
            Flow(d.id, d.dateMs, -effect(d))
        }
    }

    /** The hand-made entry that makes [card] show [targetOwed] given what its messages add up to ([currentOwed]). Null when nothing to change. */
    fun correctionFor(card: String, currentOwed: Long, targetOwed: Long, nowMs: Long, newId: () -> String): CardDue? {
        val diff = Math.subtractExact(targetOwed, currentOwed)
        if (diff == 0L) return null
        return CardDue("cd-open-${newId()}", card, "opening", diff, nowMs, "Set to ${IndianFormat.rupees(Paise(targetOwed))}", nowMs)
    }
}
