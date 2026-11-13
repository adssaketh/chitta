package com.mansaketh.chitta.core.intake

/** Marks an entry as coming from a Ping SMS (or a UPI payment, id starting "pp-"), kept in its note. Used to skip duplicates. */
const val PING_NOTE_MARKER = "Ping SMS #"

/** The text of the message (and its day) is also remembered: the same SMS can arrive twice, once without Android's own id. */
const val PING_KEY_MARKER = "Ping SMS key "

/** The raw sender header (for example `AX-AXISBK-S`), so "View SMS in Ping" can find the message again. Never a phone number. */
const val PING_FROM_MARKER = "Ping SMS from "

private val safeSenderRe = Regex("^[A-Za-z0-9._&-]{1,32}\\z")

/** One candidate entry read from a bank SMS or a UPI payment page. Nothing is saved until the person confirms. */
data class IntakeRow(
    val sourceId: String,
    /** Day plus a hash of the message text, see [PING_KEY_MARKER]. */
    val contentKey: String,
    val sender: String,
    val dateMs: Long,
    /** Always positive, see [isExpense]. */
    val amountPaise: Long,
    val isExpense: Boolean,
    val title: String,
    val merchant: String,
    /** Rule-based label ("dining", "groceries" ...), "" when none was suggested. */
    val categoryLabel: String,
    /** Your name for the account / card, or "SBI ••6919" style, or null when the SMS does not say. */
    val accountName: String?,
    /** The Chitta account this goes into. */
    val walletName: String,
    val closed: Boolean,
    val tickByDefault: Boolean,
    /** true when the account / card is in your own list. Only those are saved automatically. */
    val knownAccount: Boolean = false,
    /** Extra line for the note (for example the UPI details), before the duplicate markers. */
    val extraNote: String = "",
    /** Ping's `brandKey` for the sender (logo key), "" when Ping sent none or an invalid one. */
    val brandKey: String = "",
    /** A UPI amount read without a rupee sign: it has to be checked by the person. */
    val amountUnconfirmed: Boolean = false,
    /** The row is the "card bill payment" kind: a debit that pays a credit card bill. */
    val cardBillDebit: Boolean = false,
) {
    val isUpi: Boolean get() = sourceId.startsWith("pp-")

    private val markers: String
        get() {
            val from = if (!isUpi && safeSenderRe.matches(sender)) " $PING_FROM_MARKER$sender" else ""
            return "$PING_NOTE_MARKER$sourceId $PING_KEY_MARKER$contentKey$from"
        }

    /** Text stored in the entry's note: the account, then the duplicate markers. */
    val note: String
        get() {
            val head = if (accountName == null) "" else "$accountName${if (closed) " (closed)" else ""}\n"
            val extra = if (extraNote.isEmpty()) "" else "$extraNote\n"
            return head + extra + markers
        }

    /** What is added to the note of an entry that is already in the book for the same payment (its other half), so neither half is imported again. */
    val claimNote: String get() = (if (extraNote.isEmpty()) "" else "$extraNote\n") + markers
}

/** What reading one Ping file or message found. */
data class PingIntake(
    /** Newest first. */
    val rows: List<IntakeRow>,
    /** Messages that are not importable (not a transaction, or a credit card bill acknowledgement). */
    val skipped: Int,
    val fromMs: Long?,
    val toMs: Long?,
    val cardDues: List<CardDueItem>,
    /** Ids of messages that announce a fixed deposit. Chitta does not read those yet; the app keeps them (see DECISIONS). */
    val fixedDepositIds: List<String>,
)

/** The effect of one message on what you owe on a credit card. */
data class CardDueItem(val id: String, val card: String, val kind: String, val amountPaise: Long, val dateMs: Long, val note: String)

/** What "View SMS in Ping" sends to Ping: it needs [smsId], or [address] together with [dateMs]. */
data class PingSmsRef(val smsId: String? = null, val address: String? = null, val dateMs: Long? = null)
