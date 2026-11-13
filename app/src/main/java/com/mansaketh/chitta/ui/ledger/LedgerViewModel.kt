package com.mansaketh.chitta.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.domain.BulkChange
import com.mansaketh.chitta.core.domain.DeleteImpact
import com.mansaketh.chitta.core.domain.LedgerFilter
import com.mansaketh.chitta.core.domain.LedgerGrouping
import com.mansaketh.chitta.core.domain.LedgerSearch
import com.mansaketh.chitta.core.domain.LedgerTab
import com.mansaketh.chitta.core.domain.MonthGroup
import com.mansaketh.chitta.core.domain.Totals
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.ui.EntryActions
import com.mansaketh.chitta.ui.accountName
import com.mansaketh.chitta.ui.categoryName
import com.mansaketh.chitta.ui.dayAndTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LedgerUi(
    val loading: Boolean = true,
    val months: List<MonthGroup> = emptyList(),
    val byId: Map<String, Transaction> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val filter: LedgerFilter = LedgerFilter(),
    val selected: Set<String> = emptySet(),
    val totalEntries: Int = 0,
    val matching: Int = 0,
)

@HiltViewModel
class LedgerViewModel @Inject constructor(
    ledger: LedgerRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val actions: EntryActions,
    private val clock: Clock,
) : ViewModel() {
    private val filter = MutableStateFlow(LedgerFilter())
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val _pendingDelete = MutableStateFlow<DeleteImpact?>(null)
    val pendingDelete: StateFlow<DeleteImpact?> = _pendingDelete

    private data class Data(val entries: List<Transaction>, val accounts: List<Account>, val categories: List<Category>)

    private val data = combine(ledger.observeAll(), accounts.observeAll(), categories.observeAll()) { e, a, c -> Data(e, a, c) }

    val ui: StateFlow<LedgerUi> = combine(data, filter, selected) { d, f, sel ->
        val shown = LedgerSearch.apply(d.entries, f, d.categories, d.accounts)
        LedgerUi(
            loading = false,
            months = LedgerGrouping.group(shown, d.categories, clock.zone()),
            byId = d.entries.associateBy { it.id },
            categories = d.categories, accounts = d.accounts, filter = f,
            selected = sel.intersect(d.entries.map { it.id }.toSet()),
            totalEntries = d.entries.size, matching = shown.size,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUi())

    fun setTab(tab: LedgerTab) = filter.update { it.copy(tab = tab) }
    fun setText(text: String) = filter.update { it.copy(text = text) }
    fun setAccounts(ids: Set<String>) = filter.update { it.copy(accountIds = ids) }
    fun setCategories(ids: Set<String>) = filter.update { it.copy(categoryIds = ids) }
    fun clearFilters() = filter.update { LedgerFilter(tab = it.tab) }

    fun toggle(id: String) = selected.update { if (id in it) it - id else it + id }
    fun startSelecting(id: String) = selected.update { it + id }
    fun clearSelection() { selected.value = emptySet() }

    private fun selectedEntries(): List<Transaction> = ui.value.let { u -> u.selected.mapNotNull { u.byId[it] } }

    fun requestDelete() {
        val u = ui.value
        _pendingDelete.value = actions.impact(selectedEntries(), u.byId.values.toList(), u.accounts)
    }

    fun cancelDelete() { _pendingDelete.value = null }

    fun confirmDelete() {
        val impact = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch { actions.delete(impact); clearSelection() }
    }

    /** Applies a bulk change. Returns how many entries were skipped (transfer halves that cannot move). */
    fun bulk(change: BulkChange, onDone: (changed: Int, skipped: Int) -> Unit) {
        val u = ui.value
        val result = actions.bulk(selectedEntries(), u.byId.values.toList(), change)
        viewModelScope.launch {
            actions.save(result.changed, "Changed ${result.changed.size} entries")
            clearSelection()
            onDone(result.changed.size, result.skipped.size)
        }
    }

    /** One line per selected entry, for the clipboard. */
    fun copyText(): String {
        val u = ui.value
        return selectedEntries().sortedBy { it.dateMs }.joinToString("\n") { t ->
            listOf(dayAndTime(t.dateMs, clock.zone()), t.name.ifBlank { "(no title)" }, IndianFormat.rupees(Paise(t.amount)), categoryName(t.categoryId, u.categories), accountName(t.accountId, u.accounts)).joinToString(" · ")
        }
    }

    /** Totals of the selection for sharing: in, out and count. Transfers and balance corrections are left out, as everywhere. */
    fun totalsText(): String {
        val u = ui.value
        val sel = selectedEntries()
        val flow = Totals.of(sel, u.categories)
        return "${sel.size} entries · in ${IndianFormat.rupees(flow.moneyIn)} · out ${IndianFormat.rupees(flow.moneyOut)} · net ${IndianFormat.rupees(flow.net)}"
    }
}
