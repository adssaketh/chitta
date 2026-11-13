package com.mansaketh.chitta.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.database.PinnedRepository
import com.mansaketh.chitta.core.domain.*
import com.mansaketh.chitta.core.model.*
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.di.IdSource
import com.mansaketh.chitta.ui.EntryActions
import com.mansaketh.chitta.ui.UndoCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUi(
    val entry: Transaction? = null,
    val twin: Transaction? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val history: List<EntryVersion> = emptyList(),
    val loading: Boolean = true,
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
) : ViewModel() {
    private val id = MutableStateFlow<String?>(null)
    fun load(entryId: String) { id.value = entryId }

    val ui: StateFlow<DetailUi> = id.filterNotNull().flatMapLatest { eid ->
        combine(ledger.observe(eid), ledger.observeHistory(eid), accounts.observeAll(), categories.observeAll()) { e, h, a, c ->
            val twin = e?.pairedTransactionId?.let { ledger.get(it) }
            DetailUi(e, twin, a, c, h, loading = false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUi())

    private fun edit(change: (Transaction) -> Transaction) {
        val e = ui.value.entry ?: return
        val next = change(e).copy(modifiedAtMs = clock.nowMs())
        viewModelScope.launch { actions.save(listOf(next), "Saved changes") }
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
