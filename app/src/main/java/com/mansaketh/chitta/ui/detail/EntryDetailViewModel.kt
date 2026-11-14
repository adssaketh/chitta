package com.mansaketh.chitta.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.database.PinnedRepository
import com.mansaketh.chitta.core.domain.*
import com.mansaketh.chitta.core.model.*
import com.mansaketh.chitta.core.intake.ReceiptParser
import com.mansaketh.chitta.core.intake.ReceiptReading
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.intake.TextReaders
import com.mansaketh.chitta.receipts.ReceiptStore
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.di.IdSource
import com.mansaketh.chitta.ui.EntryActions
import com.mansaketh.chitta.ui.UndoCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUi(
    val entry: Transaction? = null,
    val twin: Transaction? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val history: List<EntryVersion> = emptyList(),
    /** Other lines of the same split, for the detail screen. */
    val siblings: List<Transaction> = emptyList(),
    val loading: Boolean = true,
)

/** The receipt photo of the open entry. [reading] is what the text on it seemed to say, shown as suggestions until closed. */
data class ReceiptUi(
    val has: Boolean = false,
    val busy: Boolean = false,
    val reading: ReceiptReading? = null,
    val notice: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    private val ledger: LedgerRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val pinned: PinnedRepository,
    private val actions: EntryActions,
    private val undo: UndoCenter,
    private val ids: IdSource,
    private val clock: Clock,
    private val receipts: ReceiptStore,
) : ViewModel() {
    private val id = MutableStateFlow<String?>(null)
    fun load(entryId: String) {
        if (id.value != entryId) receipt.value = ReceiptUi()
        id.value = entryId
        viewModelScope.launch { receipt.value = receipt.value.copy(has = withContext(Dispatchers.IO) { receipts.has(entryId) }) }
    }

    val receipt = MutableStateFlow(ReceiptUi())

    /** A fresh address for the camera app to write its picture to. */
    fun newCaptureUri() = receipts.newCaptureUri()
    /** The camera reported success: keep its picture as the receipt. */
    fun attachCaptured() = attachReceipt(receipts.capturedUri())
    fun setReceiptNotice(text: String) { receipt.value = receipt.value.copy(notice = text) }

    /** Keeps [source] as this entry's receipt, then reads its text for suggestions. Nothing on the entry changes until a suggestion is tapped. */
    fun attachReceipt(source: android.net.Uri) {
        val eid = id.value ?: return
        if (receipt.value.busy) return
        viewModelScope.launch {
            receipt.value = receipt.value.copy(busy = true, notice = null, reading = null)
            if (!receipts.attach(eid, source)) {
                receipt.value = receipt.value.copy(busy = false, notice = "That picture could not be read. Nothing was changed.")
                return@launch
            }
            receipt.value = receipt.value.copy(has = true)
            readReceipt(eid)
        }
    }

    /** Reads the stored photo again (for example after an update of the reader). */
    fun readAgain() {
        val eid = id.value ?: return
        if (receipt.value.busy) return
        viewModelScope.launch { receipt.value = receipt.value.copy(busy = true, notice = null, reading = null); readReceipt(eid) }
    }

    private suspend fun readReceipt(eid: String) {
        val bitmap = receipts.load(eid)
        if (bitmap == null) { receipt.value = receipt.value.copy(busy = false, has = false, notice = "The photo is not on this phone any more."); return }
        val today = java.time.Instant.ofEpochMilli(clock.nowMs()).atZone(clock.zone()).toLocalDate()
        val merged = try {
            ReceiptParser.merge(TextReaders.readReceiptTexts(bitmap).map { ReceiptParser.parse(it, today) })
        } catch (e: Exception) { ReceiptReading() } finally { bitmap.recycle() }
        receipt.value = receipt.value.copy(
            busy = false, reading = merged.takeUnless { it.isEmpty },
            notice = if (merged.isEmpty) "The photo is saved, but no amount, date or shop name could be read from it." else null,
        )
    }

    fun closeReading() { receipt.value = receipt.value.copy(reading = null) }
    fun clearReceiptNotice() { receipt.value = receipt.value.copy(notice = null) }
    suspend fun receiptImage(): androidx.compose.ui.graphics.ImageBitmap? = id.value?.let { receipts.load(it) }?.asImageBitmap()

    /** Takes the photo off the entry; Undo for 10 seconds brings it back. */
    fun removeReceipt() {
        val eid = id.value ?: return
        if (!receipts.removeKeepingCopy(eid)) return
        receipt.value = receipt.value.copy(has = false, reading = null)
        undo.post("Receipt removed") { receipts.restoreRemoved(eid); receipt.value = receipt.value.copy(has = receipts.has(eid)) }
    }

    /** One tapped suggestion: the receipt's total as the entry's amount. Same rules as typing it (above zero, sign kept). */
    fun useReceiptAmount(paise: Long) {
        if (paise <= 0) return
        edit { e -> e.copy(amount = if (e.amount < 0) -paise else paise, originalAmount = null, originalCurrency = null, exchangeRate = null) }
    }

    /** One tapped suggestion: the receipt's date, keeping the entry's time of day. */
    fun useReceiptDate(day: java.time.LocalDate) {
        val e = ui.value.entry ?: return
        val zone = clock.zone()
        val time = java.time.Instant.ofEpochMilli(e.dateMs).atZone(zone).toLocalTime()
        setDate(day.atTime(time).atZone(zone).toInstant().toEpochMilli())
    }

    val ui: StateFlow<DetailUi> = id.filterNotNull().flatMapLatest { eid ->
        combine(ledger.observe(eid), ledger.observeHistory(eid), accounts.observeAll(), categories.observeAll(), ledger.observeAll()) { e, h, a, c, all ->
            val twin = e?.pairedTransactionId?.let { ledger.get(it) }
            DetailUi(e, twin, a, c, h, siblings = e?.let { Splits.siblings(it, all) } ?: emptyList(), loading = false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUi())

    private fun edit(change: (Transaction) -> Transaction) {
        val e = ui.value.entry ?: return
        val next = change(e).copy(modifiedAtMs = clock.nowMs())
        viewModelScope.launch { actions.save(listOf(next), "Saved changes") }
    }

    /**
     * Replaces this entry with one entry per split line (DECISIONS D85). One Undo puts the original back and removes the lines.
     * Returns false when the lines are not balanced (the sheet keeps Save off until they are).
     */
    fun split(lines: List<SplitLine>, onDone: () -> Unit): Boolean {
        val e = ui.value.entry ?: return false
        val built = Splits.build(e.copy(modifiedAtMs = clock.nowMs()), Paise(Math.abs(e.amount)), lines, ids::newId) ?: return false
        viewModelScope.launch {
            val removed = ledger.delete(listOf(e.id))
            ledger.add(built)
            // The photo belongs to the whole payment; it goes to the first line, and back again on Undo.
            receipts.move(e.id, built[0].id)
            undo.post("Split into ${built.size} lines") { ledger.delete(built.map { it.id }); ledger.restoreDeleted(removed); receipts.move(built[0].id, e.id) }
            onDone()
        }
        return true
    }

    val isTransfer get() = ui.value.entry?.let(Totals::isNeutral) == true

    fun setTitle(s: String) = edit { it.copy(name = s.trim()) }
    fun setNote(s: String) = bulk(BulkChange(note = s))
    fun setDate(ms: Long) = bulk(BulkChange(dateMs = ms))
    fun setCategory(cid: String) = edit { it.copy(categoryId = cid, subCategoryId = null) }
    fun setAccount(aid: String, currency: String?) = edit { it.copy(accountId = aid, currency = currency ?: it.currency) }

    /** Typed amount (a calculation is fine). Keeps the sign the entry already has. */
    fun setAmount(raw: String): Boolean {
        val p = AmountExpression.evaluate(raw) ?: return false
        if (p.isZero) return false
        edit { e -> e.copy(amount = if (e.amount < 0) -p.value else p.value, originalAmount = null, originalCurrency = null, exchangeRate = null) }
        return true
    }

    private fun bulk(change: BulkChange) {
        val e = ui.value.entry ?: return
        val all = listOfNotNull(e, ui.value.twin)
        val result = actions.bulk(listOf(e), all, change)
        viewModelScope.launch { actions.save(result.changed, "Saved changes") }
    }

    fun duplicate(onDone: () -> Unit) {
        val e = ui.value.entry ?: return
        if (Totals.isNeutral(e)) return
        val copy = e.copy(id = ids.newId(), dateMs = clock.nowMs(), modifiedAtMs = clock.nowMs(), pairedTransactionId = null)
        viewModelScope.launch { actions.add(listOf(copy), "Duplicated · ${e.name.ifBlank { "entry" }}"); onDone() }
    }

    fun pin() {
        val e = ui.value.entry ?: return
        viewModelScope.launch {
            pinned.save(PinnedEntry(ids.newId(), e.name.ifBlank { "Pinned" }, Math.abs(e.amount), e.income, e.categoryId, e.accountId, 0, clock.nowMs()))
            undo.post("Pinned to Quick Add") { }
        }
    }

    fun deleteImpact(): DeleteImpact? {
        val u = ui.value; val e = u.entry ?: return null
        return Deletion.impact(listOf(e), listOfNotNull(e, u.twin), u.accounts)
    }

    fun confirmDelete(impact: DeleteImpact, onDone: () -> Unit) = viewModelScope.launch { actions.delete(impact); onDone() }

    fun restore(version: EntryVersion) = viewModelScope.launch {
        val before = ledger.get(version.entryId)
        if (!ledger.restoreVersion(version.entryId, version.id)) return@launch
        undo.post("Restored an earlier version") { if (before != null) ledger.update(listOf(before)) else ledger.delete(listOf(version.entryId)) }
    }
}
