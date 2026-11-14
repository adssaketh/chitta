package com.mansaketh.chitta.intake

import android.content.Context
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.core.database.AssetsRepository
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.database.TitleRepository
import com.mansaketh.chitta.core.domain.RestoreKind
import com.mansaketh.chitta.core.intake.IntakeChoice
import com.mansaketh.chitta.core.intake.IntakeClaim
import com.mansaketh.chitta.core.intake.IntakeContext
import com.mansaketh.chitta.core.intake.FdSms
import com.mansaketh.chitta.core.intake.IntakeFormatException
import com.mansaketh.chitta.core.intake.IntakePlan
import com.mansaketh.chitta.core.intake.IntakePlanner
import com.mansaketh.chitta.core.intake.IntakeRow
import com.mansaketh.chitta.core.intake.KnownAccounts
import com.mansaketh.chitta.core.intake.PingContract
import com.mansaketh.chitta.core.intake.PingExport
import com.mansaketh.chitta.core.domain.FixedDeposits
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.CardDue
import com.mansaketh.chitta.data.RestoreManager
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.di.IdSource
import com.mansaketh.chitta.ui.UndoCenter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** What one read of the inbox or a Ping file did. */
data class IntakeResult(val autoSaved: Int = 0, val toReview: Int = 0, val alreadyImported: Int = 0, val notTransactions: Int = 0, val parkedDeposits: Int = 0, val unreadable: Int = 0, val matchedToEntries: Int = 0,
    val addedDeposits: Int = 0, val cardsUpdated: Int = 0) {
    val anything: Boolean get() = autoSaved + toReview + alreadyImported + notTransactions + parkedDeposits + unreadable + matchedToEntries + addedDeposits + cardsUpdated > 0
}

/**
 * Holds what Ping sent. Three small files in the app's private folder, no database change:
 *  - ping_inbox/: one JSON file per message handed over by Ping (written by [PingInboxProvider] and the share receiver),
 *  - intake/pending.json: messages that were not confident enough to save, waiting in the review list,
 *  - intake/parked-fd.json: fixed deposit messages that could not be read completely (amount or maturity missing); nothing is guessed (D100).
 * [processInbox] reads the inbox when Chitta opens: confident messages are saved at once (with Undo), the rest go to pending.
 */
@Singleton
class IntakeStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val titleRepo: TitleRepository,
    private val ledger: LedgerRepository,
    private val assets: AssetsRepository,
    private val restore: RestoreManager,
    private val undo: UndoCenter,
    private val ids: IdSource,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val dir = File(context.filesDir, "intake")
    private val _pending = MutableStateFlow(readItems(File(dir, PENDING)).size)
    val pendingCount: StateFlow<Int> = _pending
    private val _known = MutableStateFlow(loadKnown().size)
    val knownCount: StateFlow<Int> = _known

    // ---------------------------------------------------------------- known accounts
    private fun loadKnown(): KnownAccounts = try {
        val f = File(dir, KNOWN)
        if (f.isFile) KnownAccounts.parse(f.readText()) else KnownAccounts.EMPTY
    } catch (e: Exception) { KnownAccounts.EMPTY }

    fun knownAccounts(): KnownAccounts = loadKnown()

    /** Checks the file, then keeps it. Throws [IntakeFormatException] with a plain message when it is not a known-accounts file. */
    suspend fun importKnownAccounts(text: String): Int = withContext(Dispatchers.IO) {
        val parsed = KnownAccounts.parse(text)
        dir.mkdirs()
        File(dir, KNOWN).writeText(parsed.toJson())
        _known.value = parsed.size
        parsed.size
    }

    suspend fun clearKnownAccounts() = withContext(Dispatchers.IO) { File(dir, KNOWN).delete(); _known.value = 0 }

    // ---------------------------------------------------------------- pending and parked
    private fun readItems(f: File): List<JsonObject> = try { if (f.isFile) PingExport.items(f.readText()) else emptyList() } catch (e: Exception) { emptyList() }
    private fun idOf(o: JsonObject) = (o["id"] as? JsonPrimitive)?.content.orEmpty()
    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.content.orEmpty()

    private fun writeItems(f: File, items: List<JsonObject>) {
        dir.mkdirs()
        if (items.isEmpty()) { f.delete(); return }
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(PingExport.exportOf(items))
        if (!tmp.renameTo(f)) { f.writeText(PingExport.exportOf(items)); tmp.delete() }
    }

    private fun addTo(name: String, items: List<JsonObject>) {
        if (items.isEmpty()) return
        val f = File(dir, name)
        val have = readItems(f)
        val haveIds = have.map(::idOf).toHashSet()
        writeItems(f, have + items.filter { idOf(it) !in haveIds })
    }

    private fun removeFromPending(sourceIds: Collection<String>) {
        if (sourceIds.isEmpty()) return
        val f = File(dir, PENDING)
        writeItems(f, readItems(f).filter { idOf(it) !in sourceIds })
        _pending.value = readItems(f).size
    }

    suspend fun pendingRows(): List<IntakeRow> = withContext(Dispatchers.IO) {
        val items = readItems(File(dir, PENDING))
        if (items.isEmpty()) emptyList() else PingExport.parse(PingExport.exportOf(items), loadKnown(), clock.zone()).rows
    }

    /** Takes messages out of the review list for good (the person said they are not wanted). */
    suspend fun dismiss(sourceIds: Collection<String>) = mutex.withLock { withContext(Dispatchers.IO) { removeFromPending(sourceIds) } }

    // ---------------------------------------------------------------- reading
    suspend fun planContext(): IntakeContext = IntakeContext(
        accountRepo.observeAll().first(), categoryRepo.observeAll().first(), ledger.observeAllOnce(), titleRepo.observeAll().first(), clock.zone(),
    )

    suspend fun plan(rows: List<IntakeRow>): IntakePlan = IntakePlanner.plan(rows, planContext())

    /** Reads every file in the inbox. Called when Chitta opens. Files that cannot be read are removed, so they do not come back every time. */
    suspend fun processInbox(): IntakeResult = mutex.withLock {
        val files = withContext(Dispatchers.IO) { inboxDir(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedBy { it.name }.orEmpty() }
        if (files.isEmpty()) return@withLock IntakeResult()
        var bad = 0
        val items = ArrayList<JsonObject>()
        withContext(Dispatchers.IO) {
            for (f in files) {
                try { items.addAll(PingExport.items(f.readText())) } catch (e: Exception) { bad++ }
            }
        }
        val result = ingest(items).copy(unreadable = bad)
        withContext(Dispatchers.IO) { files.forEach { it.delete() } }
        result
    }

    /** Reads a Ping file the person picked. Throws [IntakeFormatException] when it is not a Ping export. */
    suspend fun ingestText(text: String): IntakeResult = mutex.withLock { ingest(PingExport.items(text)) }

    private suspend fun ingest(rawItems: List<JsonObject>): IntakeResult {
        if (rawItems.isEmpty()) return IntakeResult()
        val byId = LinkedHashMap<String, JsonObject>()
        rawItems.forEach { byId.putIfAbsent(idOf(it), it) }
        val items = byId.values.toList()
        val intake = withContext(Dispatchers.Default) { PingExport.parse(PingExport.exportOf(items), loadKnown(), clock.zone()) }
        val ctx = planContext()
        val plan = withContext(Dispatchers.Default) { IntakePlanner.plan(intake.rows, ctx) }
        applyClaims(plan.claims)
        val auto = IntakePlanner.autoEntries(plan, ctx.accounts, clock.nowMs(), ids::newId)
        val autoIds = plan.toAutoSave.filter { it.saveable }.map { it.row.sourceId }
        val reviewIds = plan.candidates.map { it.row.sourceId }.toSet() - autoIds.toSet()
        // One Undo for everything this read saved: entries, deposits and card dues (the bar holds a single item).
        val undoSteps = ArrayList<suspend () -> Unit>()
        val said = ArrayList<String>()
        if (auto.isNotEmpty()) {
            if (auto.size >= RESTORE_POINT_FROM) restore.create(RestoreKind.BeforeImport)
            ledger.add(auto)
            val saved = auto.map { it.id }
            val back = autoIds.mapNotNull { byId[it] }
            said += "Saved ${auto.size} from your bank messages"
            // Undo puts them back in the review list, so they are not saved again behind your back.
            undoSteps += {
                ledger.delete(saved)
                mutex.withLock { withContext(Dispatchers.IO) { addTo(PENDING, back); _pending.value = readItems(File(dir, PENDING)).size } }
            }
        }

        // Card dues (Phase 8b): spend, payment and refund messages change what a card owes. They write no entries.
        val now = clock.nowMs()
        val addedDues = assets.addCardDues(intake.cardDues.map { CardDue(it.id, it.card, it.kind, it.amountPaise, it.dateMs, it.note, now) })
        if (addedDues.isNotEmpty()) {
            val cards = addedDues.map { it.card }.distinct().size
            said += "Updated what you owe on $cards ${if (cards == 1) "card" else "cards"}"
            val dueIds = addedDues.map { it.id }
            undoSteps += { assets.deleteCardDues(dueIds) }
        }

        // Fixed deposits (Phase 8b): read completely, or kept parked. A deposit never creates an entry; the bank's debit message does.
        val have = assets.deposits().toMutableList()
        val addedFds = ArrayList<String>()
        val unreadFds = ArrayList<JsonObject>()
        for (id in intake.fixedDepositIds) {
            val item = byId[id] ?: continue
            val d = FdSms.parse(item.text("body"), item.text("address"), item.text("date").toLongOrNull() ?: now, id, clock.zone())
            if (d == null) { unreadFds += item; continue }
            if (have.any { FixedDeposits.same(it, d, clock.zone()) }) continue
            val stamped = d.copy(updatedAtMs = now)
            if (assets.addDepositIfNew(stamped)) { have += stamped; addedFds += stamped.id }
        }
        if (addedFds.isNotEmpty()) {
            said += if (addedFds.size == 1) "Added a fixed deposit. Check its details" else "Added ${addedFds.size} fixed deposits. Check their details"
            undoSteps += { addedFds.forEach { assets.deleteDeposit(it) } }
        }
        if (said.isNotEmpty()) undo.post(said.joinToString(". ")) { undoSteps.forEach { it() } }

        withContext(Dispatchers.IO) {
            addTo(PENDING, reviewIds.mapNotNull { byId[it] })
            removeFromPending(autoIds)
            addTo(PARKED, unreadFds)
            _pending.value = readItems(File(dir, PENDING)).size
        }
        return IntakeResult(auto.size, reviewIds.size, plan.alreadyImported, intake.skipped, unreadFds.size, 0, plan.claims.size, addedFds.size, addedDues.map { it.card }.distinct().size)
    }

    /**
     * A payment that is already in the book as the other half of a row (its bank SMS, or its UPI page) only gets the row's details added to its
     * note, so neither half is imported again. It goes through the ledger, so the change is in the entry's history.
     */
    suspend fun applyClaims(claims: List<IntakeClaim>) {
        if (claims.isEmpty()) return
        val now = clock.nowMs()
        val updated = claims.mapNotNull { c -> ledger.get(c.entryId)?.let { it.copy(note = it.note + "\n" + c.row.claimNote, modifiedAtMs = now) } }
        if (updated.isNotEmpty()) ledger.update(updated)
    }

    // ---------------------------------------------------------------- saving what the person reviewed
    /** Saves the ticked, saveable rows. A restore point is made first; the entries can be undone for 10 seconds. Returns how many were saved. */
    suspend fun saveReviewed(choices: List<IntakeChoice>, accounts: List<Account>, claims: List<IntakeClaim>): Int = mutex.withLock {
        val entries = IntakePlanner.toEntries(choices, accounts, clock.nowMs(), ids::newId)
        if (entries.isEmpty() && claims.isEmpty()) return@withLock 0
        restore.create(RestoreKind.BeforeImport)
        applyClaims(claims)
        if (entries.isNotEmpty()) {
            ledger.add(entries)
            val saved = entries.map { it.id }
            undo.post("Saved ${entries.size} ${if (entries.size == 1) "entry" else "entries"}") { ledger.delete(saved) }
        }
        // what was saved (or matched to an entry already in the book) no longer waits for review
        val doneIds = choices.filter { it.ticked && it.saveable }.map { it.candidate.row.sourceId } + claims.map { it.row.sourceId }
        withContext(Dispatchers.IO) { removeFromPending(doneIds) }
        entries.size
    }

    /** A new account for rows whose account is not in the book yet. */
    suspend fun createAccount(name: String): Account {
        val existing = accountRepo.observeAll().first()
        existing.firstOrNull { !it.archived && it.name.equals(name.trim(), ignoreCase = true) }?.let { return it }
        val order = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val account = Account(ids.newId(), name.trim(), null, null, clock.nowMs(), null, order, "INR", null, 2, null, null)
        accountRepo.save(account)
        return account
    }

    companion object {
        private const val PENDING = "pending.json"
        private const val PARKED = "parked-fd.json"
        private const val KNOWN = "known_accounts.json"
        const val MAX_FILE_BYTES = 8 * 1024 * 1024
        /** From this many automatic saves in one go, a restore point is made first (a date-range file can hold hundreds). */
        private const val RESTORE_POINT_FROM = 10
        private val counter = AtomicInteger()
        private val inboxLock = Any()

        fun inboxDir(context: Context) = File(context.filesDir, "ping_inbox")

        /** Name of the copy of a shared screenshot, PDF or CSV in the cache. */
        fun sharedName(kind: String) = "upi-share." + when (kind) { "image" -> "img"; "pdf" -> "pdf"; else -> "csv" }

        fun readLimited(input: InputStream, max: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16384)
            while (out.size() < max) {
                val n = input.read(buf, 0, minOf(buf.size, max - out.size()))
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }

        /** One file per message, written under a temporary name and then renamed, so nobody reads a half-written file. */
        fun writeToInbox(context: Context, text: String) = synchronized(inboxLock) {
            val dir = inboxDir(context)
            if (!dir.isDirectory && !dir.mkdirs()) error("could not create the inbox folder")
            if ((dir.list()?.size ?: 0) >= PingContract.MAX_INBOX_FILES) error("the inbox is full, open Paddhu Pusthakam to empty it")
            val name = "${System.currentTimeMillis()}-${counter.incrementAndGet()}-${Integer.toHexString(text.hashCode())}"
            val tmp = File(dir, "$name.tmp")
            val done = File(dir, "$name.json")
            tmp.writeBytes(text.toByteArray(Charsets.UTF_8))
            if (!tmp.renameTo(done)) { tmp.delete(); error("could not save the message") }
        }
    }
}
