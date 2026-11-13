package com.mansaketh.chitta.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.database.PinnedRepository
import com.mansaketh.chitta.core.database.RatesRepository
import com.mansaketh.chitta.core.database.TitleRepository
import com.mansaketh.chitta.core.domain.*
import com.mansaketh.chitta.core.model.*
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.data.RateFetcher
import com.mansaketh.chitta.data.RefreshResult
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.di.IdSource
import com.mansaketh.chitta.ui.EntryActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class QuickAddUi(
    val draft: QuickAddDraft,
    val accounts: List<Account> = emptyList(),
    val categories: List<CategoryChoice> = emptyList(),
    val suggestions: List<TitleMemory> = emptyList(),
    val pinned: List<PinnedEntry> = emptyList(),
    val knownCurrencies: List<String> = emptyList(),
    val ready: Boolean = false,
    val problem: String? = null,
    val preview: Paise? = null,
    val rateText: String? = null,
)

data class CategoryChoice(val category: Category, val parentName: String? = null)

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val ledger: LedgerRepository,
    accountRepo: AccountRepository,
    categoryRepo: CategoryRepository,
    private val titleRepo: TitleRepository,
    private val pinnedRepo: PinnedRepository,
    private val ratesRepo: RatesRepository,
    private val fetcher: RateFetcher,
    private val actions: EntryActions,
    private val ids: IdSource,
    private val clock: Clock,
) : ViewModel() {
    private val draft = MutableStateFlow(QuickAddDraft(dateMs = clock.nowMs()))
    private val entries = ledger.observeAll()
    private val memory = titleRepo.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun plain(paise: Long): String {
        val whole = paise / 100; val frac = paise % 100
        return if (frac == 0L) whole.toString() else "$whole." + frac.toString().padStart(2, '0').trimEnd('0')
    }
    private val rates = combine(ratesRepo.observeRates(), ratesRepo.observeOverrides()) { r, o -> r to o }

    private data class Base(val accounts: List<Account>, val categories: List<Category>, val titles: List<TitleMemory>, val entries: List<Transaction>, val pinned: List<PinnedEntry>)

    private val base = combine(accountRepo.observeAll(), categoryRepo.observeAll(), titleRepo.observeAll(), entries, pinnedRepo.observeAll()) { a, c, t, e, p -> Base(a, c, t, e, p) }

    val ui: StateFlow<QuickAddUi> = combine(draft, base, rates) { d, b, (r, o) ->
        val live = b.accounts.filter { !it.archived }
        val accountId = d.accountId ?: b.entries.maxByOrNull { it.dateMs }?.accountId?.takeIf { id -> live.any { it.id == id } } ?: live.firstOrNull()?.id
        val d2 = d.copy(accountId = accountId)
        val account = live.firstOrNull { it.id == accountId }
        val accCurrency = account?.currency ?: "INR"
        val perBase = r.mapNotNull { row -> row.perBase.toBigDecimalOrNull()?.let { row.currency to it } }.toMap() + ("INR" to BigDecimal.ONE)
        val overrides = o.mapNotNull { row -> row.rate.toBigDecimalOrNull()?.let { (row.fromCurrency to row.toCurrency) to it } }.toMap()
        val orig = d2.originalCurrency?.takeIf { it != accCurrency }
        val rate = orig?.let { Exchange.rateBetween(it, accCurrency, perBase, overrides) }
        val rateText = d2.rate ?: rate?.stripTrailingZeros()?.toPlainString()
        val effective = d2.copy(originalCurrency = orig, rate = if (orig != null) rateText else null)
        val result = QuickAdd.build(effective, live, ids::newId)
        val typed = AmountExpression.evaluate(d2.amountRaw)
        val preview = if (orig != null && typed != null && rateText != null) Exchange.parseRate(rateText)?.let { Exchange.convert(typed, it) } else null
        val income = d2.type == EntryType.Income
        val cats = CategoryOrder.recentFirst(b.categories, b.entries, income)
        QuickAddUi(
            draft = d2, accounts = live,
            categories = cats.map { CategoryChoice(it) },
            suggestions = TitleSuggest.suggest(d2.title, b.titles),
            pinned = b.pinned.sortedBy { it.sortOrder },
            knownCurrencies = (perBase.keys + accCurrency).sorted(),
            ready = result is QuickAddResult.Ready,
            problem = (result as? QuickAddResult.Invalid)?.reason,
            preview = preview, rateText = rateText,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickAddUi(QuickAddDraft(dateMs = clock.nowMs())))

    /** Opens a clean sheet, optionally of a given type or filled from a pinned entry. */
    fun reset(type: EntryType = EntryType.Expense, pin: PinnedEntry? = null) {
        draft.value = if (pin == null) QuickAddDraft(type = type, dateMs = clock.nowMs())
        else QuickAddDraft(
            type = if (pin.income) EntryType.Income else EntryType.Expense,
            amountRaw = pin.amount?.let { plain(it) } ?: "",
            title = pin.title, categoryId = pin.categoryId, accountId = pin.accountId, dateMs = clock.nowMs(),
        )
    }

    fun key(k: com.mansaketh.chitta.core.design.components.KeypadKey) = draft.update { d ->
        val raw = when (k) {
            is com.mansaketh.chitta.core.design.components.KeypadKey.Digit -> AmountExpression.digit(d.amountRaw, k.digit)
            com.mansaketh.chitta.core.design.components.KeypadKey.Dot -> AmountExpression.dot(d.amountRaw)
            is com.mansaketh.chitta.core.design.components.KeypadKey.Operator -> AmountExpression.operator(d.amountRaw, k.symbol)
            com.mansaketh.chitta.core.design.components.KeypadKey.Backspace -> AmountExpression.backspace(d.amountRaw)
        }
        d.copy(amountRaw = raw)
    }

    fun setType(t: EntryType) = draft.update { it.copy(type = t, categoryId = if (t == EntryType.Transfer) null else it.categoryId.takeIf { c -> t == it.type } ) }
    fun setTitle(s: String) = draft.update { d ->
        val remembered = TitleSuggest.categoryFor(s, memory.value)
        d.copy(title = s, categoryId = remembered ?: d.categoryId)
    }
    fun pickSuggestion(m: TitleMemory) = draft.update { it.copy(title = m.title, categoryId = m.categoryId) }
    fun setCategory(id: String) = draft.update { it.copy(categoryId = id) }
    fun setAccount(id: String) = draft.update { it.copy(accountId = id, rate = null) }
    fun setToAccount(id: String) = draft.update { it.copy(toAccountId = id) }
    fun setDate(ms: Long) = draft.update { it.copy(dateMs = ms) }
    fun setNote(s: String) = draft.update { it.copy(note = s) }
    fun setCurrency(code: String?) = draft.update { it.copy(originalCurrency = code, rate = null) }
    fun setRate(text: String) = draft.update { it.copy(rate = text.trim().ifEmpty { null }) }
    fun refreshRates(onDone: (RefreshResult) -> Unit) = viewModelScope.launch { onDone(fetcher.refresh(clock.nowMs())) }

    fun pinCurrent() {
        val u = ui.value; val d = u.draft
        val cat = d.categoryId ?: return
        val acc = d.accountId ?: return
        viewModelScope.launch {
            pinnedRepo.save(PinnedEntry(ids.newId(), d.title.trim().ifEmpty { "Pinned" }, AmountExpression.evaluate(d.amountRaw)?.value, d.type == EntryType.Income, cat, acc, u.pinned.size, clock.nowMs()))
        }
    }

    /** Saves and closes. Returns false (and does nothing) when the draft is not ready. */
    fun save(onSaved: () -> Unit) {
        val u = ui.value
        if (!u.ready) return
        val d = u.draft.copy(originalCurrency = u.draft.originalCurrency, rate = u.rateText.takeIf { u.draft.originalCurrency != null })
        val built = QuickAdd.build(d, u.accounts, ids::newId) as? QuickAddResult.Ready ?: return
        viewModelScope.launch {
            val first = built.entries.first()
            val label = if (d.type == EntryType.Transfer) "Saved · transfer ${IndianFormat.rupees(Paise(Math.abs(first.amount)))}"
            else "Saved · ${first.name.ifBlank { "entry" }} ${IndianFormat.rupees(Paise(Math.abs(first.amount)))}"
            actions.add(built.entries, label)
            if (d.type != EntryType.Transfer && d.title.isNotBlank() && d.categoryId != null) titleRepo.remember(d.title, d.categoryId!!, clock.nowMs(), ids.newId())
            onSaved()
        }
    }
}
