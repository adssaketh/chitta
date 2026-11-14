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
    /** Phase 8b: fixed deposits, card messages read from SMS, and the two Net worth switches. */
    val deposits: List<FixedDeposit> = emptyList(),
    val dues: List<CardDue> = emptyList(),
    /** Phase 8c: mutual fund holdings. */
    val funds: List<MutualFund> = emptyList(),
    /** NPS is its own tab: accounts and their schemes, never mixed into [funds]. */
    val nps: List<NpsAccount> = emptyList(),
    val npsSchemes: List<MutualFund> = emptyList(),
    val nwDeposits: Boolean = false,
    val nwInstalments: Boolean = true,
    /** Phase 8d: the fund and NPS Net worth switches, the daily price fetch switch, and what the last fetch said. */
    val nwFunds: Boolean = true,
    val nwNps: Boolean = true,
    val pricesAuto: Boolean = true,
    val priceStatus: com.mansaketh.chitta.data.FundPriceStatus = com.mansaketh.chitta.data.FundPriceStatus(),
    val pricesRunning: Boolean = false,
    val nowMs: Long = 0L,
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
)

@HiltViewModel
class ManageViewModel @Inject constructor(
    private val ledger: LedgerRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val categoryWriter: CategoryWriter,
    private val titleRepo: TitleRepository,
    private val ratesRepo: RatesRepository,
    private val assets: AssetsRepository,
    private val prefs: com.mansaketh.chitta.data.UiPrefs,
    private val fetcher: RateFetcher,
    private val prices: com.mansaketh.chitta.data.FundPriceService,
    private val actions: EntryActions,
    private val undo: UndoCenter,
    private val ids: IdSource,
    private val clock: Clock,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {
    private val entries = ledger.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val titles = titleRepo.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private data class Base(val accounts: List<Account>, val categories: List<Category>, val entries: List<Transaction>, val rates: List<ExchangeRateEntity>, val overrides: List<RateOverrideEntity>)

    private val base = combine(accountRepo.observeAll(), categoryRepo.observeAll(), entries, ratesRepo.observeRates(), ratesRepo.observeOverrides()) { a, c, e, r, o -> Base(a, c, e, r, o) }

    private data class Assets(val deposits: List<FixedDeposit>, val dues: List<CardDue>, val funds: List<MutualFund>, val nps: List<NpsAccount>)

    private val assetsFlow = combine(assets.observeDeposits(), assets.observeCardDues(), assets.observeFunds(), assets.observeNps()) { d, du, f, n -> Assets(d, du, f, n) }

    private val pricesRunning = MutableStateFlow(false)

    private data class FundSwitches(val funds: Boolean, val nps: Boolean, val auto: Boolean, val status: com.mansaketh.chitta.data.FundPriceStatus, val running: Boolean)

    private val fundSwitches = combine(prefs.netWorthFunds, prefs.netWorthNps, prefs.fundPricesAuto, prefs.fundPriceStatus, pricesRunning) { f, n, auto, st, run -> FundSwitches(f, n, auto, st, run) }

    val ui: StateFlow<ManageUi> = combine(base, assetsFlow, prefs.netWorthDeposits, prefs.netWorthInstalments, fundSwitches) { b, a, nd, ni, fs ->
        val d = a.deposits; val du = a.dues
        val now = clock.nowMs()
        val today = java.time.Instant.ofEpochMilli(now).atZone(clock.zone()).toLocalDate()
        // Card messages feed the cockpit, minus the ones an entry on the card already covers (CardDues.flowsFor).
        val cards = b.accounts.mapNotNull { acc ->
            cardSetup(acc)?.let { acc.id to com.mansaketh.chitta.core.domain.Cards.figures(it, b.entries, today, clock.zone(), CardDues.flowsFor(acc, du, b.entries)) }
        }.toMap()
        ManageUi(b.accounts, b.categories, Balances.balances(b.entries), b.rates, b.overrides, loading = false, cards = cards, deposits = d, dues = du, funds = a.funds.filter { !it.isNps }, nps = a.nps, npsSchemes = a.funds.filter { it.isNps }, nwDeposits = nd, nwInstalments = ni, nwFunds = fs.funds, nwNps = fs.nps, pricesAuto = fs.auto, priceStatus = fs.status, pricesRunning = fs.running, nowMs = now, zone = clock.zone())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageUi())

    // ---- fixed deposits (Phase 8b)
    /** Saves a new or edited deposit; Undo puts the old one back (or removes the new one). */
    fun saveDeposit(entered: FixedDeposit) = viewModelScope.launch {
        val d = if (entered.id.isEmpty()) entered.copy(id = ids.newId()) else entered
        val old = ui.value.deposits.firstOrNull { it.id == d.id }
        assets.saveDeposit(d.copy(updatedAtMs = clock.nowMs()))
        undo.post(if (old == null) "Fixed deposit added" else "Fixed deposit saved") { if (old == null) assets.deleteDeposit(d.id) else assets.saveDeposit(old) }
    }

    fun deleteDeposit(id: String) = viewModelScope.launch {
        val old = assets.deleteDeposit(id) ?: return@launch
        undo.post("Fixed deposit deleted") { assets.saveDeposit(old) }
    }

    fun setNetWorthDeposits(v: Boolean) = prefs.setNetWorthDeposits(v)
    fun setNetWorthInstalments(v: Boolean) = prefs.setNetWorthInstalments(v)
    fun setNetWorthFunds(v: Boolean) = prefs.setNetWorthFunds(v)
    fun setNetWorthNps(v: Boolean) = prefs.setNetWorthNps(v)

    // ---- fund prices (Phase 8d)
    /** Fetches the price file now. The screen shows the result from the saved status. */
    fun refreshPrices() = viewModelScope.launch {
        if (pricesRunning.value) return@launch
        pricesRunning.value = true
        try { prices.refresh() } finally { pricesRunning.value = false }
    }

    fun setFundPricesAuto(v: Boolean) {
        prefs.setFundPricesAuto(v)
        com.mansaketh.chitta.data.FundPriceScheduler.sync(appContext, v)
    }

    /** The schemes to offer for a fund (from the saved price file), best first. Empty before the first fetch. */
    suspend fun priceCandidates(f: MutualFund): List<com.mansaketh.chitta.core.intake.AmfiScheme> = prices.candidates(f)

    /** Links the fund to the chosen scheme and prices it. Undo restores the fund as it was. */
    fun linkFund(f: MutualFund, scheme: com.mansaketh.chitta.core.intake.AmfiScheme) = viewModelScope.launch {
        prices.link(f, scheme)
        undo.post("Fund linked to ${scheme.name}") { assets.saveFund(f) }
    }

    // ---- mutual funds (Phase 8c)
    /**
     * Saves a new or edited fund; Undo puts the old one back (or removes the new one).
     * SIP bookkeeping (Phase 8d, D122): the estimate belongs to the units, so typing new units or a new "units as of" date drops the estimate and
     * counts instalments from that date; a SIP set for the first time counts from today (nothing is ever credited back in time).
     */
    fun saveFund(entered: MutualFund) = viewModelScope.launch {
        val base = if (entered.id.isEmpty()) entered.copy(id = ids.newId()) else entered
        val old = ui.value.funds.firstOrNull { it.id == base.id } ?: ui.value.npsSchemes.firstOrNull { it.id == base.id }
        val today = Periods.date(clock.nowMs(), clock.zone())
        val f = when {
            base.isNps -> base
            base.sipAmountPaise != null && (old == null || old.sipAmountPaise == null) -> Sip.reset(base, today)
            old != null && (unitsDiffer(old.units, base.units) || old.unitsAsOfMs != base.unitsAsOfMs) -> Sip.reset(base, Periods.date(base.unitsAsOfMs, clock.zone()))
            else -> base
        }
        assets.saveFund(f.copy(updatedAtMs = clock.nowMs()))
        undo.post(if (old == null) "Mutual fund added" else "Mutual fund saved") { if (old == null) assets.deleteFund(f.id) else assets.saveFund(old) }
    }

    /** "100.500" and "100.5" are the same units; only a different number counts as new units. */
    private fun unitsDiffer(a: String, b: String): Boolean {
        val x = a.trim().toBigDecimalOrNull(); val y = b.trim().toBigDecimalOrNull()
        return if (x == null || y == null) a != b else x.compareTo(y) != 0
    }

    fun deleteFund(id: String) = viewModelScope.launch {
        val old = assets.deleteFund(id) ?: return@launch
        undo.post("Mutual fund deleted") { assets.saveFund(old) }
    }

    /** What a statement import would do. Nothing is written until [applyCas]. */
    fun planCas(reading: com.mansaketh.chitta.core.intake.CasReading.Ready): com.mansaketh.chitta.core.intake.CasPlan =
        com.mansaketh.chitta.core.intake.CasImport.plan(ui.value.funds + ui.value.npsSchemes, reading.holdings, clock.nowMs(), ids::newId)

    /** Applies the plan: only statement-sourced funds change. One Undo item puts every old copy back and removes what was added. */
    fun applyCas(plan: com.mansaketh.chitta.core.intake.CasPlan) = viewModelScope.launch {
        val oldUpdated = ui.value.funds.filter { f -> plan.update.any { it.id == f.id } }
        assets.replaceFunds(plan.remove.map { it.id }, plan.add + plan.update)
        undo.post("Statement imported") { assets.replaceFunds(plan.add.map { it.id }, oldUpdated + plan.remove) }
        // Best effort: link the new funds to AMFI schemes and price them now. A failure only leaves the statement's own prices.
        prices.refresh()
    }

    // ---- NPS (Phase 8c)
    /** What an NPS statement import would do. Nothing is written until [applyNps]. */
    fun planNps(reading: com.mansaketh.chitta.core.intake.NpsReading.Ready): com.mansaketh.chitta.core.intake.NpsPlan =
        com.mansaketh.chitta.core.intake.NpsImport.plan(ui.value.nps, ui.value.npsSchemes, reading, clock.nowMs(), clock.zone(), ids::newId)

    /** Creates or updates the NPS account and replaces only statement-sourced schemes. One Undo item restores everything as it was. */
    fun applyNps(plan: com.mansaketh.chitta.core.intake.NpsPlan) = viewModelScope.launch {
        val oldAccount = ui.value.nps.firstOrNull { it.id == plan.account.id }
        val oldUpdated = ui.value.npsSchemes.filter { s -> plan.update.any { it.id == s.id } }
        assets.saveNps(plan.account)
        assets.replaceFunds(plan.remove.map { it.id }, plan.add + plan.update)
        undo.post("NPS statement imported") {
            assets.replaceFunds(plan.add.map { it.id }, oldUpdated + plan.remove)
            if (oldAccount == null) assets.deleteNps(plan.account.id) else assets.saveNps(oldAccount)
        }
    }

    fun saveNps(entered: NpsAccount) = viewModelScope.launch {
        val a = if (entered.id.isEmpty()) entered.copy(id = ids.newId()) else entered
        val old = ui.value.nps.firstOrNull { it.id == a.id }
        assets.saveNps(a.copy(updatedAtMs = clock.nowMs()))
        undo.post(if (old == null) "NPS account added" else "NPS account saved") { if (old == null) assets.deleteNps(a.id) else assets.saveNps(old) }
    }

    /** Deletes the account with its schemes; Undo puts all of them back. */
    fun deleteNps(id: String) = viewModelScope.launch {
        val (acc, schemes) = assets.deleteNps(id) ?: return@launch
        undo.post("NPS account deleted") { assets.saveNps(acc); assets.replaceFunds(emptyList(), schemes) }
    }

    // ---- card messages (Phase 8b)
    /** Makes [card] show [targetOwed] by adding a hand-made "opening" row for the difference. Undo removes it. Returns false when nothing needed changing. */
    fun setCardOwed(card: String, targetOwed: Long): Boolean {
        val current = CardDues.owedByCard(ui.value.dues)[card] ?: 0L
        val row = CardDues.correctionFor(card, current, targetOwed, clock.nowMs(), ids::newId) ?: return false
        viewModelScope.launch {
            assets.saveCardDue(row)
            undo.post("Set what is owed on $card") { assets.deleteCardDues(listOf(row.id)) }
        }
        return true
    }

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
