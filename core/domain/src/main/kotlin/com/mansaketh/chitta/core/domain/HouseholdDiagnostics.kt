package com.mansaketh.chitta.core.domain

/** What this phone holds about the household, counted from its own database (Phase 9b step 4). */
data class PhoneHolds(val sharedSent: Int, val privateSent: Int, val waiting: Int, val fromMembers: Int, val members: Int)

/** What the server holds, counted by the server, for the same questions. */
data class ServerHolds(val shared: Int, val myPrivate: Int, val members: Int)

enum class DiagnosticState { Same, PhoneBehind, ServerBehind }

/** One line of the diagnostics view: [phone] and [server] are the two counts, [state] says which side is short, [gap] by how many. */
data class DiagnosticRow(val label: String, val phone: Int, val server: Int, val state: DiagnosticState, val gap: Int)

/**
 * The household diagnostics view (master section 7: "what the phone and the server each hold"). Pure comparison of two sets of counts.
 *  - Shared entries: on the phone, entries from members plus my own sent shared entries; on the server, every entry not marked private.
 *  - My private entries: entries I marked Mine only on the server against my sent private ones on the phone. (Only I can read them there.)
 *  - Members: the phone knows everyone but me through placeholder accounts, plus me.
 * Entries still waiting to go up are not "sent", so a phone that is ahead by that many is normal until Send now; [waitingNote] says so.
 */
object HouseholdDiagnostics {
    fun rows(phone: PhoneHolds, server: ServerHolds): List<DiagnosticRow> = listOf(
        row("Shared entries", phone.fromMembers + phone.sharedSent, server.shared),
        row("My Mine-only entries", phone.privateSent, server.myPrivate),
        row("Members", phone.members, server.members),
    )

    private fun row(label: String, phone: Int, server: Int): DiagnosticRow = when {
        phone == server -> DiagnosticRow(label, phone, server, DiagnosticState.Same, 0)
        phone < server -> DiagnosticRow(label, phone, server, DiagnosticState.PhoneBehind, server - phone)
        else -> DiagnosticRow(label, phone, server, DiagnosticState.ServerBehind, phone - server)
    }

    /** One plain line for a row. */
    fun describe(r: DiagnosticRow): String = when (r.state) {
        DiagnosticState.Same -> "Same on both"
        DiagnosticState.PhoneBehind -> "The phone is missing ${r.gap}"
        DiagnosticState.ServerBehind -> "The server is missing ${r.gap}"
    }

    /** True when nothing differs. */
    fun allSame(rows: List<DiagnosticRow>): Boolean = rows.all { it.state == DiagnosticState.Same }

    fun waitingNote(waiting: Int): String? = when (waiting) {
        0 -> null
        1 -> "1 entry is waiting to go up. Tap Send now."
        else -> "$waiting entries are waiting to go up. Tap Send now."
    }
}
