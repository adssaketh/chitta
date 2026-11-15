package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.MethodAdded
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Entry sharing for a household (Phase 9b, step 3). Plain Kotlin, no Firebase: the app turns these maps into Firestore documents.
 * Ported from the old Flutter app's pure rules (`householdMergeRules`, `householdViewRules`, `householdWalletRules`) so both apps
 * read the same server data. See DECISIONS D212 to D220.
 *
 * Who owns an entry on this phone (`ownerUid`): null = new, not claimed yet; "" = personal, stays on this phone; a uid = shared by that person.
 */
object HouseholdSharing {
    // ---- placeholder accounts: one per other member, never shown in pickers (master section 7)

    const val PLACEHOLDER_PREFIX = "hh_"

    fun placeholderAccountId(uid: String): String = PLACEHOLDER_PREFIX + uid
    fun isPlaceholderAccountId(accountId: String): Boolean = accountId.startsWith(PLACEHOLDER_PREFIX)

    // ---- who owns what

    /** True when [ownerUid] means "this person": not claimed yet (null), personal (""), or their own uid. */
    fun ownerIsMe(ownerUid: String?, myUid: String): Boolean = ownerUid == null || ownerUid == "" || ownerUid == myUid

    /** True when [ownerUid] is somebody else in the household (not this person, not "personal"). */
    fun isForeignOwner(ownerUid: String?, myUid: String): Boolean = !ownerUid.isNullOrEmpty() && ownerUid != myUid

    /** Members change only their own entries. The household owner may not edit other people's entries either. */
    fun canEdit(inHousehold: Boolean, ownerUid: String?, myUid: String): Boolean = !inHousehold || ownerIsMe(ownerUid, myUid)

    /** Their own, or anybody's when the owner of the household granted `canDelete`. */
    fun canDelete(inHousehold: Boolean, ownerUid: String?, myUid: String, memberCanDelete: Boolean): Boolean =
        !inHousehold || ownerIsMe(ownerUid, myUid) || memberCanDelete

    // ---- Everyone / Mine

    const val VIEW_EVERYONE = "everyone"
    const val VIEW_MINE = "mine"

    /** Anything unknown counts as Everyone. */
    fun cleanView(view: String?): String = if (view == VIEW_MINE) VIEW_MINE else VIEW_EVERYONE

    /**
     * Whether an entry appears in the lists and totals of [view]. Outside a household nothing is filtered.
     * Mine: everything this person owns, including private and personal ones.
     * Everyone: the shared ledger only. Private and personal entries are left out so the totals match on every phone.
     */
    fun visible(inHousehold: Boolean, view: String, ownerUid: String?, isPrivate: Boolean, myUid: String): Boolean {
        if (!inHousehold) return true
        if (cleanView(view) == VIEW_MINE) return ownerIsMe(ownerUid, myUid)
        if (isPrivate) return false
        if (ownerUid == "") return false
        return true
    }

    // ---- choosing what to share (PDF p.33)

    /** What to do with the entries already on the phone when someone joins or creates a household. */
    enum class ShareChoice { SHARE_EXISTING, ONLY_NEW }

    /**
     * The owner an unclaimed or personal entry gets after the choice, or null to leave it as it is.
     * Share: both unclaimed and personal ones become mine and go up. Only new: unclaimed ones become personal ("") so they stay here.
     * Entries that already carry a uid (shared before, or imported from the old app) are never touched.
     */
    fun ownerAfterChoice(choice: ShareChoice, current: String?, myUid: String): String? = when {
        !current.isNullOrEmpty() -> null
        choice == ShareChoice.SHARE_EXISTING -> myUid
        current == null -> ""
        else -> null
    }

    // ---- merging what arrives from the server

    /** True when [remoteMs] is a later edit than [localMs]. A missing time counts as the oldest; a missing remote time is never newer. */
    fun remoteIsNewer(remoteMs: Long?, localMs: Long?): Boolean = when {
        remoteMs == null -> false
        localMs == null -> true
        else -> remoteMs > localMs
    }

    /** The server decides who owns a row: true when the local row must be replaced by the document, whatever its sync state. */
    fun serverOwnerWins(localOwnerUid: String?, serverOwnerId: String): Boolean = localOwnerUid != serverOwnerId

    data class LocalRow(val id: String, val ownerUid: String?, val syncedAtMs: Long?)

    /**
     * What to correct after a full server snapshot. [deleteIds]: other members' rows no longer on the server, remove them here.
     * [reuploadIds]: my own rows that are missing on the server, send them again. [undeleteIds]: my rows that are still here but carry
     * an old "deleted" record, so the record is stale: drop it and send the row again.
     */
    data class ReconcilePlan(val deleteIds: List<String>, val reuploadIds: List<String>, val undeleteIds: List<String>)

    const val GRACE_MS = 2 * 60 * 1000L

    /**
     * Compares what this phone holds with the ids the server returned (shared rows plus my own private rows) and corrects both sides.
     * A document deleted while the listener was off never produces a "removed" event, so without this a ghost row would stay for ever.
     * A row uploaded less than [GRACE_MS] ago is left alone: the snapshot may simply be older than the upload.
     */
    fun reconcile(local: List<LocalRow>, serverIds: Set<String>, deletedHere: Set<String>, myUid: String, nowMs: Long): ReconcilePlan {
        val delete = mutableListOf<String>()
        val reupload = mutableListOf<String>()
        val undelete = mutableListOf<String>()
        for (row in local) {
            if (row.id in serverIds) continue
            if (isForeignOwner(row.ownerUid, myUid)) {
                delete += row.id
            } else if (row.ownerUid == myUid) {
                if (row.id in deletedHere) {
                    undelete += row.id
                    if (row.syncedAtMs != null) reupload += row.id
                } else if (row.syncedAtMs != null && nowMs - row.syncedAtMs > GRACE_MS) {
                    reupload += row.id
                }
            }
        }
        return ReconcilePlan(delete, reupload, undelete)
    }
}

/**
 * The Firestore document of an entry or a category. The field names and number formats are those of the old Flutter app
 * (`Transaction.toJson()` of the Drift generator): times in epoch milliseconds, `amount` as a double in rupees, enums as their stored codes.
 * Chitta adds exact fields next to them (`amountPaise` and the Chitta-only entry fields); the old app ignores keys it does not know.
 *
 * The old app kept some links on the phone and never uploaded them, so they are not uploaded here either:
 * budget exclusions, goal and loan links, the paired transfer entry, and the old shared-budget fields.
 */
object HouseholdWire {
    /** Rupees as the old app writes them: paise / 100 as a double. */
    fun rupeesOf(paise: Long): Double = BigDecimal(paise).movePointLeft(2).toDouble()

    /** Paise from a document: the exact `amountPaise` when present (written by Chitta), else the old rupee double, rounded half up on its shortest text. */
    fun paiseOf(doc: Map<String, Any?>): Long? {
        (doc["amountPaise"] as? Number)?.let { return it.toLong() }
        val rupees = (doc["amount"] as? Number)?.toDouble() ?: return null
        if (rupees.isNaN() || rupees.isInfinite()) return null
        return BigDecimal(rupees.toString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    private fun long(v: Any?): Long? = (v as? Number)?.toLong()
    private fun int(v: Any?): Int? = (v as? Number)?.toInt()
    private fun bool(v: Any?): Boolean? = v as? Boolean
    private fun str(v: Any?): String? = v as? String

    /** The entry as a document. [uid] is the sharing person; the rules read `ownerId`. */
    fun entryToDocument(t: Transaction, uid: String): Map<String, Any?> = linkedMapOf(
        "transactionPk" to t.id,
        "name" to t.name,
        "amount" to rupeesOf(t.amount),
        "note" to t.note,
        "categoryFk" to t.categoryId,
        "subCategoryFk" to t.subCategoryId,
        "walletFk" to t.accountId,
        "dateCreated" to t.dateMs,
        "dateTimeModified" to t.modifiedAtMs,
        "originalDateDue" to t.originalDueMs,
        "income" to t.income,
        "periodLength" to t.periodLength,
        "reoccurrence" to t.repeat?.code,
        "endDate" to t.endDateMs,
        "upcomingTransactionNotification" to t.upcomingNotification,
        "type" to t.kind?.code,
        "paid" to t.paid,
        "createdAnotherFutureTransaction" to t.createdAnotherFuture,
        "skipPaid" to t.skipPaid,
        "methodAdded" to t.methodAdded?.code,
        "isPrivate" to t.isPrivate,
        "ownerId" to uid, // the name the Firestore rules look at
        // Chitta only
        "amountPaise" to t.amount,
        "currency" to t.currency,
        "originalAmountPaise" to t.originalAmount,
        "originalCurrency" to t.originalCurrency,
        "exchangeRate" to t.exchangeRate,
        "anchorDay" to t.anchorDay,
        "splitGroupId" to t.splitGroupId,
    )

    /**
     * An entry from a document, or null when it cannot be used (a missing id, name, amount, category, account or date).
     * The caller sets the owner, the account (another member's entries go to their placeholder account) and the sync time.
     * Other members' repeating entries are stored as already handled with no reminder: their owner's phone does that work.
     */
    fun entryFromDocument(doc: Map<String, Any?>): Transaction? {
        val id = str(doc["transactionPk"])?.takeIf { it.isNotEmpty() } ?: return null
        val amount = paiseOf(doc) ?: return null
        val categoryId = str(doc["categoryFk"]) ?: return null
        val accountId = str(doc["walletFk"]) ?: return null
        val dateMs = long(doc["dateCreated"]) ?: return null
        return Transaction(
            id = id, pairedTransactionId = null, name = str(doc["name"]).orEmpty(), amount = amount, currency = str(doc["currency"]),
            note = str(doc["note"]).orEmpty(), categoryId = categoryId, subCategoryId = str(doc["subCategoryFk"]), accountId = accountId,
            dateMs = dateMs, modifiedAtMs = long(doc["dateTimeModified"]), originalDueMs = long(doc["originalDateDue"]),
            income = bool(doc["income"]) ?: (amount > 0), periodLength = int(doc["periodLength"]), repeat = Repeat.fromCode(int(doc["reoccurrence"])),
            endDateMs = long(doc["endDate"]), upcomingNotification = bool(doc["upcomingTransactionNotification"]),
            kind = TransactionKind.fromCode(int(doc["type"])), paid = bool(doc["paid"]) ?: true,
            createdAnotherFuture = bool(doc["createdAnotherFutureTransaction"]), skipPaid = bool(doc["skipPaid"]) ?: false,
            methodAdded = MethodAdded.fromCode(int(doc["methodAdded"])), objectiveId = null, objectiveLoanId = null, excludeFromBudgetIds = emptyList(),
            ownerUid = str(doc["ownerId"]), isPrivate = bool(doc["isPrivate"]) ?: false, householdSyncedAtMs = null,
            originalAmount = long(doc["originalAmountPaise"]), originalCurrency = str(doc["originalCurrency"]), exchangeRate = str(doc["exchangeRate"]),
            anchorDay = int(doc["anchorDay"]), splitGroupId = str(doc["splitGroupId"]),
        )
    }

    /** What another member's entry looks like on this phone: in their placeholder account, handled, no reminders, shared, synced. */
    fun asMemberEntry(t: Transaction, ownerUid: String): Transaction = t.copy(
        ownerUid = ownerUid, accountId = HouseholdSharing.placeholderAccountId(ownerUid), isPrivate = false, householdSyncedAtMs = t.modifiedAtMs ?: t.dateMs,
        createdAnotherFuture = true, upcomingNotification = false,
    )

    /**
     * My own entry changed on another phone of mine (the server copy is a later edit): take the uploaded fields, keep what the old app
     * never uploaded and never moves (links to goals, loans and budgets, the paired transfer entry) and my own account choice.
     */
    fun mergeOwnRow(local: Transaction, remote: Transaction, remoteAccountExistsHere: Boolean): Transaction = remote.copy(
        ownerUid = local.ownerUid,
        accountId = if (remoteAccountExistsHere && !HouseholdSharing.isPlaceholderAccountId(remote.accountId)) remote.accountId else local.accountId,
        pairedTransactionId = local.pairedTransactionId, objectiveId = local.objectiveId, objectiveLoanId = local.objectiveLoanId,
        excludeFromBudgetIds = local.excludeFromBudgetIds, methodAdded = local.methodAdded ?: remote.methodAdded,
        householdSyncedAtMs = remote.modifiedAtMs ?: remote.dateMs,
    )

    /** My own entry that is missing on this phone, restored from the household. [accountId] is its account here, or the fallback. */
    fun restoreOwnRow(remote: Transaction, myUid: String, accountId: String): Transaction =
        remote.copy(ownerUid = myUid, accountId = accountId, householdSyncedAtMs = remote.modifiedAtMs ?: remote.dateMs)

    /**
     * A fingerprint of the categories as they would be uploaded. `modifiedAtMs` changes whenever an entry uses the category, which is not
     * a real edit, so it is left out. Equal fingerprints mean there is nothing to upload.
     */
    fun categoriesFingerprint(categories: List<Category>): String =
        categories.sortedBy { it.id }.joinToString("\n") { c ->
            listOf(c.id, c.name, c.colour, c.iconName, c.emojiIconName, c.createdAtMs, c.sortOrder, c.income, c.methodAdded?.code, c.mainCategoryId, c.archived).joinToString("|")
        }.hashCode().toString()

    fun categoryToDocument(c: Category): Map<String, Any?> = linkedMapOf(
        "categoryPk" to c.id,
        "name" to c.name,
        "colour" to c.colour,
        "iconName" to c.iconName,
        "emojiIconName" to c.emojiIconName,
        "dateCreated" to c.createdAtMs,
        "dateTimeModified" to c.modifiedAtMs,
        "order" to c.sortOrder,
        "income" to c.income,
        "methodAdded" to c.methodAdded?.code,
        "mainCategoryPk" to c.mainCategoryId,
        "archived" to c.archived, // Chitta only
    )

    fun categoryFromDocument(doc: Map<String, Any?>): Category? {
        val id = str(doc["categoryPk"])?.takeIf { it.isNotEmpty() } ?: return null
        val name = str(doc["name"]) ?: return null
        return Category(
            id = id, name = name, colour = str(doc["colour"]), iconName = str(doc["iconName"]), emojiIconName = str(doc["emojiIconName"]),
            createdAtMs = long(doc["dateCreated"]) ?: 0L, modifiedAtMs = long(doc["dateTimeModified"]), sortOrder = int(doc["order"]) ?: 0,
            income = bool(doc["income"]) ?: false, methodAdded = MethodAdded.fromCode(int(doc["methodAdded"])), mainCategoryId = str(doc["mainCategoryPk"]),
            archived = bool(doc["archived"]) ?: false,
        )
    }

    /** What counts as the same category content. `modifiedAtMs` also changes when an entry merely uses the category, so it does not count. */
    fun sameCategoryContent(a: Category, b: Category): Boolean =
        a.name == b.name && a.colour == b.colour && a.iconName == b.iconName && a.emojiIconName == b.emojiIconName &&
            a.sortOrder == b.sortOrder && a.income == b.income && a.mainCategoryId == b.mainCategoryId && a.archived == b.archived
}
