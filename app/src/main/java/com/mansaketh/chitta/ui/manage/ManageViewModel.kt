package com.mansaketh.chitta.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.*
import com.mansaketh.chitta.core.domain.*
import com.mansaketh.chitta.core.model.*
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.data.RateFetcher
import com.mansaketh.chitta.data.RefreshResult
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.di.IdSource
import com.mansaketh.chitta.ui.EntryActions
import com.mansaketh.chitta.ui.UndoCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class ManageUi(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val balances: Map<String, Paise> = emptyMap(),
    val rates: List<ExchangeRateEntity> = emptyList(),
    val overrides: List<RateOverrideEntity> = emptyList(),
    val loading: Boolean = true,
    /** Card figures for accounts that have both days set (master section 12, credit card). */
    val cards: Map<String, com.mansaketh.chitta.core.domain.CardFigures> = emptyMap(),
)

@HiltViewModel
class ManageViewModel @Inject constructor(
    private val ledger: LedgerRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val categoryWriter: CategoryWriter,
    private val titleRepo: TitleRepository,
    private val ratesRepo: RatesRepository,
    private val fetcher: RateFetcher,
    private val actions: EntryActions,
    private val undo: UndoCenter,
    private val ids: IdSource,
    private val clock: Clock,
) : ViewModel() {
    private val entries = ledger.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val titles = titleRepo.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ui: StateFlow<ManageUi> = combine(accountRepo.observeAll(), categoryRepo.observeAll(), entries, ratesRepo.observeRates(), ratesRepo.observeOverrides()) { a, c, e, r, o ->
        val today = java.time.Instant.ofEpochMilli(clock.nowMs()).atZone(clock.zone()).toLocalDate()
        val cards = a.mapNotNull { acc -> cardSetup(acc)?.let { acc.id to com.mansaketh.chitta.core.domain.Cards.figures(it, e, today, clock.zone()) } }.toMap()
        ManageUi(a, c, Balances.balances(e), r, o, loading = false, cards = cards)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageUi())

    // ---- accounts
    fun addAccount(name: String, currency: String = "INR") = viewModelScope.launch {
        val n = name.trim().ifEmpty { return@launch }
        val order = (ui.value.accounts.maxOfOrNull { it.sortOrder } ?: -1) + 1
        accountRepo.save(Account(ids.newId(), n, null, null, clock.nowMs(), null, order, currency, null, 2, null, null))
    }

    fun renameAccount(a: Account, name: String) = viewModelScope.launch {
        val n = name.trim().ifEmpty { return@launch }
        accountRepo.save(a.copy(name = n, modifiedAtMs = clock.nowMs()))
    }

    /** Saves the card fields of an account. Any of them may be cleared with null; the two days switch the cockpit on. */
    fun saveCard(a: Account, limit: Long?, statementDay: Int?, dueDay: Int?, minimumDue: Long?, remindDaysBefore: Int?) = viewModelScope.launch {
        accountRepo.save(a.copy(creditLimit = limit, statementDay = statementDay, dueDay = dueDay, minimumDue = minimumDue, remindDaysBefore = remindDaysBefore, modifiedAtMs = clock.nowMs()))
    }

    fun archiveAccount(a: Account, archived: Boolean) = viewModelScope.launch { accountRepo.save(a.copy(archived = archived, modifiedAtMs = clock.nowMs())) }

    /** Moves the account's balance to [wantedRaw] with a correction entry that is not spending or income. Returns a message when it cannot. */
    fun correctBalance(a: Account, wantedRaw: String, onProblem: (String) -> Unit) {
        val wanted = AmountExpression.evaluate(wantedRaw)
        val negative = wantedRaw.trim().startsWith("-")
        val current = ui.value.balances[a.id] ?: Paise.ZERO
        // A negative balance (a card you owe on) is typed with a leading minus; the keypad evaluator rejects it, so handle it here.
        val target = when {
            wanted != null -> Paise(if (negative) -wanted.value else wanted.value)
            negative -> AmountExpression.evaluate(wantedRaw.trim().removePrefix("-"))?.let { Paise(-it.value) }
            else -> null
        } ?: return onProblem("Enter the balance as a number.")
        val entry = BalanceCorrection.create(a, current, target, clock.nowMs(), a.ownerUid, ids::newId) ?: return onProblem("The balance already matches.")
        viewModelScope.launch { actions.add(listOf(entry), "Balance of ${a.name} corrected") }
    }

    // ---- categories
    fun addCategory(name: String, income: Boolean, parent: Category?) = viewModelScope.launch {
        val n = name.trim().ifEmpty { return@launch }
        val siblings = ui.value.categories.filter { it.mainCategoryId == parent?.id && it.income == (parent?.income ?: income) }
        categoryRepo.save(Category(ids.newId(), n, null, null, null, clock.nowMs(), null, (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1, parent?.income ?: income, null, parent?.id))
    }

    fun renameCategory(c: Category, name: String) = viewModelScope.launch {
        val n = name.trim().ifEmpty { return@launch }
        categoryRepo.save(c.copy(name = n, modifiedAtMs = clock.nowMs()))
    }

    /** Sets (or clears, with null) the icon a category wears. The key is a Phosphor name from [CategoryIconCatalog]. */
    fun setCategoryIcon(c: Category, key: String?) = viewModelScope.launch {
        categoryRepo.save(c.copy(iconName = key?.let { com.mansaketh.chitta.core.domain.IconSearch.nameFor(it) }, emojiIconName = null, modifiedAtMs = clock.nowMs()))
    }

    fun archiveCategory(c: Category, archived: Boolean) = viewModelScope.launch { categoryRepo.save(c.copy(archived = archived, modifiedAtMs = clock.nowMs())) }

    fun move(c: Category, up: Boolean) = viewModelScope.launch {
        val siblings = ui.value.categories.filter { it.mainCategoryId == c.mainCategoryId && it.income == c.income }.sortedBy { it.sortOrder }
        categoryWriter.saveAll(CategoryOps.move(siblings, c.id, up, clock.nowMs()))
    }

    /** Returns a plain-language reason when the conversion is not allowed. */
    fun convertToSub(c: Category, parent: Category): String? {
        CategoryOps.cannotConvert(c, parent, ui.value.categories)?.let { return it }
        viewModelScope.launch { categoryWriter.saveAll(listOf(CategoryOps.convertToSub(c, parent, clock.nowMs()))) }
        return null
    }

    fun merge(source: Category, target: Category) = viewModelScope.launch {
        val r = CategoryOps.merge(source, target, ui.value.categories, entries.value, titles.value, clock.nowMs())
        // Entries go through the ledger so every change is in history; Undo restores the earlier versions.
        if (r.entries.isNotEmpty()) actions.save(r.entries, "Merged ${source.name} into ${target.name}")
        if (r.titles.isNotEmpty()) titleRepo.saveAll(r.titles)
        categoryWriter.delete(r.removedCategoryIds)
    }

    fun entriesIn(c: Category): Int = entries.value.count { it.categoryId == c.id || it.subCategoryId == c.id }

    // ---- rates
    fun refreshRates(onDone: (RefreshResult) -> Unit) = viewModelScope.launch { onDone(fetcher.refresh(clock.nowMs())) }

    fun setOverride(from: String, to: String, text: String): Boolean {
        val r = Exchange.parseRate(text) ?: return false
        viewModelScope.launch { ratesRepo.setOverride(from, to, r.toPlainString(), clock.nowMs()) }
        return true
    }

    fun clearOverride(from: String, to: String) = viewModelScope.launch { ratesRepo.removeOverride(from, to) }
}

/** The card setup of an account, or null until both days are set. */
internal fun cardSetup(a: Account): com.mansaketh.chitta.core.domain.CardSetup? = com.mansaketh.chitta.core.domain.Cards.setupOf(a)
