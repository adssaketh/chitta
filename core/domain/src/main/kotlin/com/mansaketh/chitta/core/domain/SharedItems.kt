package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.FixedDeposit
import com.mansaketh.chitta.core.model.LoanTerms
import com.mansaketh.chitta.core.model.Objective
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sharing loans and fixed deposits inside a household (Phase 9b, step 5). One shared item is one Firestore document, in the shape the old
 * Flutter app wrote (`households/{h}/sharedItems/{kind-ownerUid-localId}`), so both apps can live in one household:
 *
 *   kind, ownerUid, localId, visibleTo (uids besides the owner, at most 7), membersCanEdit, _u (ms, newer wins), payload
 *
 * Private by default: an item nobody has been chosen for is not on the server. Only the owner sees, deletes or re-shares it. People in
 * `visibleTo` can read it; with `membersCanEdit` they may also change its details (step 5b). A viewer's copy is never part of their Net
 * worth, loan totals or reminders (master section 7).
 */
object SharedItems {
    const val KIND_LOAN = "loan"
    const val KIND_FD = "fd"
    val KINDS = setOf(KIND_LOAN, KIND_FD)

    /** Everyone in a full household except the owner. */
    const val MAX_SHARED_WITH = Households.MAX_MEMBERS - 1

    /** What the owner chose for one item. [updatedAtMs] is `_u`. */
    data class Meta(
        val kind: String, val ownerUid: String, val localId: String,
        val visibleTo: List<String> = emptyList(), val membersCanEdit: Boolean = false, val updatedAtMs: Long = 0L,
    ) {
        val docId: String get() = docId(kind, ownerUid, localId)
    }

    /** Different owners can have the same local id, so the owner is part of the id. A "/" would split the path, so it becomes "_". */
    fun docId(kind: String, ownerUid: String, localId: String): String = "$kind-$ownerUid-${localId.replace('/', '_')}"

    /** The people an owner may pick: members only, never the owner, no duplicates or blanks, at most [MAX_SHARED_WITH]. Order kept. */
    fun cleanVisibleTo(ownerUid: String, requested: Iterable<String>, memberUids: Set<String>): List<String> {
        val out = ArrayList<String>()
        for (u in requested) {
            if (u.isEmpty() || u == ownerUid || u !in memberUids || u in out) continue
            if (out.size >= MAX_SHARED_WITH) break
            out += u
        }
        return out
    }

    /** True when [uid] may see the item. Items of somebody no longer in the household are seen by nobody. */
    fun visibleTo(item: Meta, uid: String, memberUids: Set<String>): Boolean {
        if (uid.isEmpty() || uid !in memberUids || item.ownerUid !in memberUids) return false
        return uid == item.ownerUid || uid in item.visibleTo
    }

    /** True when [uid] may change the item's details. Only the owner deletes it or changes who sees it or whether others may edit. */
    fun canEdit(item: Meta, uid: String, memberUids: Set<String>): Boolean =
        visibleTo(item, uid, memberUids) && (uid == item.ownerUid || item.membersCanEdit)

    fun canManage(item: Meta, uid: String): Boolean = uid.isNotEmpty() && uid == item.ownerUid

    data class AudienceChange(val added: List<String>, val removed: List<String>) {
        val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty()
    }

    fun audienceChange(old: List<String>, new: List<String>): AudienceChange = AudienceChange(new.filter { it !in old }, old.filter { it !in new })

    // ---- the document

    fun metaToFields(m: Meta): Map<String, Any?> = linkedMapOf(
        "kind" to m.kind, "ownerUid" to m.ownerUid, "localId" to m.localId, "visibleTo" to m.visibleTo,
        "membersCanEdit" to m.membersCanEdit, "_u" to m.updatedAtMs,
    )

    /** Reads the fields of a document. Anything missing or of the wrong type gives null (or the safe value: nobody sees, nobody edits). */
    fun metaFromDocument(doc: Map<String, Any?>): Meta? {
        val kind = doc["kind"] as? String ?: return null
        val owner = doc["ownerUid"] as? String ?: return null
        val local = doc["localId"] as? String ?: return null
        if (kind !in KINDS || owner.isEmpty() || local.isEmpty()) return null
        val visible = (doc["visibleTo"] as? List<*>)?.filterIsInstance<String>()?.filter { it.isNotEmpty() } ?: emptyList()
        return Meta(kind, owner, local, visible, doc["membersCanEdit"] == true, (doc["_u"] as? Number)?.toLong() ?: 0L)
    }

    /** Mirrors what the Firestore rules enforce when a document is written, so a malformed one is skipped instead of breaking a list. */
    fun isUsable(docId: String, doc: Map<String, Any?>): Boolean {
        val meta = metaFromDocument(doc) ?: return false
        if (doc["payload"] !is Map<*, *>) return false
        return docId.startsWith("${meta.kind}-${meta.ownerUid}-")
    }

    /** Which incoming documents are written to the phone: the ones it does not have, and the ones with a later `_u`. Same time = same change again. */
    fun idsToWrite(held: Map<String, Long>, incoming: Map<String, Long>): Set<String> =
        incoming.filter { (id, at) -> held[id]?.let { at > it } ?: true }.keys

    // ---- payloads (the old app's shapes: rupees as numbers, dates as ISO text without a zone)

    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

    private fun rupees(paise: Long): Double = HouseholdWire.rupeesOf(paise)

    private fun paise(v: Any?): Long? {
        val d = (v as? Number)?.toDouble() ?: return null
        if (d.isNaN() || d.isInfinite() || d < 0 || d > 1e12) return null
        return BigDecimal(d.toString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    private fun iso(ms: Long, zone: ZoneId): String = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone).format(ISO)

    private fun instant(text: Any?, zone: ZoneId): Long? {
        val s = (text as? String)?.takeIf { it.isNotBlank() } ?: return null
        return try {
            if (s.endsWith("Z") || Regex(".*[+-]\\d\\d:?\\d\\d$").matches(s)) OffsetDateTime.parse(s).toInstant().toEpochMilli()
            else LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
        } catch (e: Exception) { null }
    }

    private fun plain(v: Any?, default: String): String {
        val d = (v as? Number)?.toDouble() ?: return default
        return if (d.isNaN() || d.isInfinite()) default else BigDecimal(d.toString()).stripTrailingZeros().toPlainString()
    }

    /** A deposit without the account it points at (that account only exists on the owner's phone). */
    fun depositPayload(fd: FixedDeposit, zone: ZoneId): Map<String, Any?> = linkedMapOf(
        "id" to fd.id, "bank" to fd.bank, "no" to fd.fdNumber, "principal" to rupees(fd.principal),
        "rate" to (fd.ratePct.trim().toDoubleOrNull() ?: 0.0), "start" to iso(fd.startMs, zone), "maturity" to iso(fd.maturityMs, zone),
        "comp" to fd.compoundingPerYear, "maturityAmount" to fd.maturityAmount?.let { rupees(it) }, "closed" to fd.closed, "note" to fd.note,
        "sms" to fd.fromSms, "smsId" to fd.smsId,
    )

    /** The deposit in a copy, or null when it cannot be read. Never carries an account. */
    fun depositFromPayload(payload: Map<String, Any?>, zone: ZoneId): FixedDeposit? {
        val id = (payload["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        val start = instant(payload["start"], zone) ?: return null
        val maturity = instant(payload["maturity"], zone) ?: return null
        return FixedDeposit(
            id = id, bank = payload["bank"] as? String ?: "", fdNumber = payload["no"] as? String ?: "", principal = paise(payload["principal"]) ?: 0L,
            ratePct = plain(payload["rate"], "0"), startMs = start, maturityMs = maturity,
            compoundingPerYear = (payload["comp"] as? Number)?.toInt() ?: 4, maturityAmount = paise(payload["maturityAmount"]), accountId = null,
            closed = payload["closed"] == true, note = payload["note"] as? String ?: "", fromSms = payload["sms"] == true,
            smsId = payload["smsId"] as? String ?: "", updatedAtMs = null,
        )
    }

    /**
     * A loan: its row (without the account) and its terms (without the account), plus [paidSoFar], which the owner's phone works out (ticked
     * instalments plus payments recorded as entries) because those entries are not on the viewers' phones. The loan's amount comes from its terms.
     */
    fun loanPayload(o: Objective, t: LoanTerms, paidSoFarPaise: Long, zone: ZoneId): Map<String, Any?> = linkedMapOf(
        "objective" to linkedMapOf(
            "objectivePk" to o.id, "type" to 1, "name" to o.name, "amount" to rupees(o.amount), "order" to o.sortOrder, "colour" to o.colour,
            "dateCreated" to o.createdAtMs, "endDate" to o.endDateMs, "dateTimeModified" to o.modifiedAtMs, "iconName" to o.iconName,
            "emojiIconName" to o.emojiIconName, "income" to o.income, "pinned" to o.pinned, "archived" to o.archived,
        ),
        "terms" to linkedMapOf(
            "principal" to rupees(t.principal), "rate" to (t.annualRatePct.trim().toDoubleOrNull() ?: 0.0), "months" to t.months, "emi" to rupees(t.emi),
            "first" to iso(t.firstEmiMs, zone), "fee" to rupees(t.fee), "gst" to (t.gstPct.trim().toDoubleOrNull() ?: 18.0), "feePaid" to t.feePaid,
            "paid" to t.paidInstalments, "note" to t.note,
        ),
        "paidSoFar" to rupees(paidSoFarPaise),
    )

    /** What a viewer sees of a loan. Anything missing falls back to something harmless. */
    data class SharedLoan(val localId: String, val name: String, val lent: Boolean, val archived: Boolean, val terms: LoanTerms?, val paidSoFar: Long?)

    fun loanFromPayload(localId: String, payload: Map<String, Any?>, zone: ZoneId): SharedLoan {
        val o = payload["objective"] as? Map<*, *>
        val name = (o?.get("name") as? String)?.trim().orEmpty().ifEmpty { "Loan" }
        val t = payload["terms"] as? Map<*, *>
        val first = instant(t?.get("first"), zone)
        val months = (t?.get("months") as? Number)?.toInt() ?: 0
        val terms = if (t == null || first == null || months <= 0) null else LoanTerms(
            objectiveId = localId, principal = paise(t["principal"]) ?: 0L, annualRatePct = plain(t["rate"], "0"), months = months, emi = paise(t["emi"]) ?: 0L,
            firstEmiMs = first, fee = paise(t["fee"]) ?: 0L, gstPct = plain(t["gst"], "18"), feePaid = t["feePaid"] == true,
            paidInstalments = (t["paid"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.distinct()?.sorted() ?: emptyList(),
            accountId = null, note = t["note"] as? String ?: "", updatedAtMs = null,
        )
        return SharedLoan(localId, name, o?.get("income") == true, o?.get("archived") == true, terms, paise(payload["paidSoFar"]))
    }

    /** What the owner's phone sends again when the paid figure changes, so a payment recorded as an entry reaches the viewers. */
    fun paidChanged(sentPaise: Long?, nowPaise: Long): Boolean = sentPaise == null || sentPaise != nowPaise

    // ---- members' edits (step 5b)

    /** Who made the latest change, as a uid inside the payload. The rules let a member change only `payload` and `_u`, so it cannot be a field of its own. */
    const val PAYLOAD_EDITED_BY = "editedBy"

    fun editorOf(payload: Map<String, Any?>): String? = (payload[PAYLOAD_EDITED_BY] as? String)?.takeIf { it.isNotEmpty() }

    /** The payload a member writes: the edited item and who edited it. The old app ignores the extra key. */
    fun withEditor(payload: Map<String, Any?>, editorUid: String): Map<String, Any?> = payload + (PAYLOAD_EDITED_BY to editorUid)

    /** The owner's phone sends this only while the item has not been changed here since the member's edit was applied. */
    fun withoutEditor(payload: Map<String, Any?>): Map<String, Any?> = payload - PAYLOAD_EDITED_BY

    /** A member's `_u`: their clock, but always later than the copy they started from. */
    fun editTime(nowMs: Long, startedFromU: Long): Long = maxOf(nowMs, startedFromU + 1)

    /**
     * True when the server holds a change this phone has not seen: later than anything the owner changed here and later than what this phone
     * sent. Same rule as the old app: the newest `_u` wins. A tie means the same change.
     */
    fun memberEditWaiting(serverU: Long?, ownerNewestMs: Long, sentMs: Long?): Boolean =
        serverU != null && serverU > ownerNewestMs && serverU > (sentMs ?: 0L)

    /** Whether a member who started from the copy with `_u` [startedFromU] may still write: refused when the server copy is newer. */
    fun editStillValid(serverU: Long, startedFromU: Long): Boolean = serverU <= startedFromU

    /**
     * Which of the member's own edits the owner's newer change has replaced. [myEditU] is the `_u` the member wrote; [incomingU] and
     * [incomingEditor] describe the copy that arrived. A change by the member themselves, or an older one, replaces nothing.
     */
    fun editWasReplaced(myEditU: Long?, incomingU: Long, incomingEditor: String?, myUid: String): Boolean =
        myEditU != null && incomingU > myEditU && incomingEditor != myUid

    /**
     * What a member's loan edit changes in the shared payload: the name and the note, nothing else. Chitta's own loan editor changes only the
     * name, so there is no screen that edits the terms. The owner's "paid so far" and everything else in the payload stays as it was.
     */
    fun loanDetailsEdit(payload: Map<String, Any?>, name: String, note: String): Map<String, Any?> {
        fun asMap(v: Any?): Map<String, Any?> = (v as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty()
        return payload + ("objective" to (asMap(payload["objective"]) + ("name" to name.trim()))) + ("terms" to (asMap(payload["terms"]) + ("note" to note.trim())))
    }

    private const val MAX_RATE_PCT = 30
    private const val MAX_LOAN_RATE_PCT = 100
    private const val MAX_MONTHS = 600
    private const val MAX_NAME = 80

    /**
     * A member's edit applied to the owner's real deposit. The owner's account and the deposit's id stay; anything that could not be a
     * deposit (no bank, amount of zero, rate out of range, maturity not after booking) is refused with null so a bad copy never reaches the book.
     */
    fun applyDepositEdit(current: FixedDeposit, payload: Map<String, Any?>, zone: ZoneId, atMs: Long): FixedDeposit? {
        val d = depositFromPayload(payload, zone) ?: return null
        val rate = d.ratePct.toBigDecimalOrNull() ?: return null
        if (d.bank.isBlank() || d.principal <= 0L) return null
        if (rate.signum() < 0 || rate > BigDecimal(MAX_RATE_PCT)) return null
        if (d.maturityMs <= d.startMs) return null
        if (d.maturityAmount != null && d.maturityAmount < d.principal) return null
        return d.copy(id = current.id, accountId = current.accountId, smsId = current.smsId, fromSms = current.fromSms, updatedAtMs = atMs)
    }

    /**
     * A member's edit applied to the owner's real loan: the name, the terms and the ticked instalments. The loan's accounts and its amount
     * stay (the amount of an instalment loan comes from its terms, never from the server). Null when the terms could not be a loan.
     */
    fun applyLoanEdit(o: Objective, t: LoanTerms, payload: Map<String, Any?>, zone: ZoneId, atMs: Long): Pair<Objective, LoanTerms>? {
        val shared = loanFromPayload(o.id, payload, zone)
        val nt = shared.terms ?: return null
        val rate = nt.annualRatePct.toBigDecimalOrNull() ?: return null
        val gst = nt.gstPct.toBigDecimalOrNull() ?: return null
        if (nt.principal <= 0L || nt.emi <= 0L || nt.months <= 0 || nt.months > MAX_MONTHS) return null
        if (rate.signum() < 0 || rate > BigDecimal(MAX_LOAN_RATE_PCT) || gst.signum() < 0 || gst > BigDecimal(MAX_LOAN_RATE_PCT)) return null
        val sent = ((payload["objective"] as? Map<*, *>)?.get("name") as? String)?.trim().orEmpty()
        val name = sent.take(MAX_NAME).ifEmpty { o.name }
        val ticks = nt.paidInstalments.filter { it in 1..nt.months }
        return o.copy(name = name, modifiedAtMs = atMs) to nt.copy(objectiveId = t.objectiveId, paidInstalments = ticks, accountId = t.accountId, updatedAtMs = atMs)
    }
}
