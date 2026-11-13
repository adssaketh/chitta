package com.mansaketh.chitta.ui.intake

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.RuleRepository
import com.mansaketh.chitta.core.domain.RuleSuggest
import com.mansaketh.chitta.core.intake.IntakeChoice
import com.mansaketh.chitta.core.intake.IntakeClaim
import com.mansaketh.chitta.core.intake.IntakeFormatException
import com.mansaketh.chitta.core.intake.IntakePlan
import com.mansaketh.chitta.core.intake.IntakeReason
import com.mansaketh.chitta.core.intake.UpiEntry
import com.mansaketh.chitta.core.intake.UpiParser
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.intake.IntakeResult
import com.mansaketh.chitta.intake.IntakeSignals
import com.mansaketh.chitta.intake.IntakeStore
import com.mansaketh.chitta.intake.TextReaders
import com.mansaketh.chitta.ui.imports.CategoryPick
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ReviewSource { Waiting, Payments }

sealed interface IntakeStage {
    data class Home(val message: String? = null) : IntakeStage
    data class Working(val what: String) : IntakeStage
    /** Rows read and checked against the book. Nothing is saved until [IntakeViewModel.save]. */
    data class Review(
        val source: ReviewSource, val choices: List<IntakeChoice>, val claims: List<IntakeClaim>, val picks: List<CategoryPick>,
        val accounts: List<Account>, val alreadyImported: Int, val skipped: Int,
    ) : IntakeStage
    data class Saved(val count: Int, val matched: Int) : IntakeStage
    data class Failed(val message: String) : IntakeStage
}

/**
 * Bank SMS from Ping and UPI payments (PhonePe, Google Pay, Paytm, BHIM, CRED, MobiKwik; screenshot, statement PDF or CSV). Confident bank
 * messages were already saved (with Undo) when they arrived; everything else waits here. Nothing from a payment page is saved until the last tap.
 */
@HiltViewModel
class IntakeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: IntakeStore,
    private val categoryRepo: CategoryRepository,
    private val ruleRepo: RuleRepository,
    private val clock: Clock,
    val signals: IntakeSignals,
) : ViewModel() {
    private val _stage = MutableStateFlow<IntakeStage>(IntakeStage.Home())
    val stage: StateFlow<IntakeStage> = _stage
    val pendingCount: StateFlow<Int> = store.pendingCount
    val knownCount: StateFlow<Int> = store.knownCount

    fun reset(message: String? = null) { _stage.value = IntakeStage.Home(message) }

    private fun fail(e: Exception) {
        _stage.value = IntakeStage.Failed(if (e is IntakeFormatException) e.message.orEmpty() else "That could not be read (${e.message ?: e.javaClass.simpleName}). Nothing was changed.")
    }

    /** Reads the inbox. Called when Chitta opens and when something arrives. */
    fun processInbox() { viewModelScope.launch { try { store.processInbox() } catch (e: Exception) { /* the files stay in the inbox for the next time */ } } }

    private fun summary(r: IntakeResult): String {
        val parts = ArrayList<String>()
        if (r.autoSaved > 0) parts.add("${r.autoSaved} saved (Undo is on the bar)")
        if (r.toReview > 0) parts.add("${r.toReview} waiting for your look")
        if (r.matchedToEntries > 0) parts.add("${r.matchedToEntries} matched to entries you already have")
        if (r.alreadyImported > 0) parts.add("${r.alreadyImported} already in your book")
        if (r.notTransactions > 0) parts.add("${r.notTransactions} not payments")
        if (r.cardsUpdated > 0) parts.add("what you owe was updated on ${r.cardsUpdated} ${if (r.cardsUpdated == 1) "card" else "cards"} (no entries)")
        if (r.addedDeposits > 0) parts.add("${r.addedDeposits} fixed ${if (r.addedDeposits == 1) "deposit" else "deposits"} added, check the details")
        if (r.parkedDeposits > 0) parts.add("${r.parkedDeposits} fixed deposit ${if (r.parkedDeposits == 1) "message" else "messages"} could not be read in full, so nothing was added")
        return if (parts.isEmpty()) "Nothing new was found in that file." else parts.joinToString(". ") + "."
    }

    /** A date-range file (or any Ping JSON) the person picked. */
    fun pickPingFile(uri: Uri) {
        _stage.value = IntakeStage.Working("Reading the file")
        viewModelScope.launch {
            try {
                val text = TextReaders.readText(context, uri)
                _stage.value = IntakeStage.Home(summary(store.ingestText(text)))
            } catch (e: Exception) { fail(e) }
        }
    }

    fun importKnownAccounts(uri: Uri) {
        viewModelScope.launch {
            try {
                val n = store.importKnownAccounts(TextReaders.readText(context, uri))
                _stage.value = IntakeStage.Home("$n accounts are known now. New bank messages for them can be saved without a look.")
            } catch (e: Exception) { fail(e) }
        }
    }

    fun clearKnownAccounts() { viewModelScope.launch { store.clearKnownAccounts(); _stage.value = IntakeStage.Home("Your list of known accounts was removed.") } }

    // ---------------------------------------------------------------- the review list
    private fun picks(categories: List<Category>): List<CategoryPick> {
        val live = categories.filter { !it.archived && it.id != "0" }
        val mains = live.filter { it.mainCategoryId == null }.sortedBy { it.sortOrder }
        return mains.flatMap { m ->
            listOf(CategoryPick(m.id, null, m.name)) + live.filter { it.mainCategoryId == m.id }.sortedBy { it.sortOrder }.map { CategoryPick(m.id, it.id, m.name + " > " + it.name) }
        }
    }

    private suspend fun review(source: ReviewSource, plan: IntakePlan, skipped: Int) {
        val ctx = store.planContext()
        val rules = ruleRepo.all()
        val choices = plan.candidates.map { cand ->
            // Rules only fill in a category that is still empty, and only as a suggestion: the row keeps its own ticks and is shown
            // for review as before (D94). The category must exist, be live, and be the right kind (expense vs income).
            var cat = cand.categoryId; var sub = cand.subCategoryId
            if (cat == null && rules.isNotEmpty()) {
                val r = cand.row
                RuleSuggest.forRow(rules, r.title.ifBlank { r.merchant }, r.extraNote, r.amountPaise, cand.accountId, !r.isExpense, ctx.categories)
                    ?.let { cat = it.categoryId; sub = it.subCategoryId }
            }
            IntakeChoice(cand, cand.ready, cand.accountId, cat, sub)
        }
        _stage.value = IntakeStage.Review(source, choices, plan.claims, picks(ctx.categories), ctx.accounts.filter { !it.archived }, plan.alreadyImported, skipped)
    }

    /** The messages that were not confident enough to save. */
    fun openWaiting() {
        _stage.value = IntakeStage.Working("Checking the messages")
        viewModelScope.launch {
            try {
                store.processInbox() // anything that arrived a moment ago
                val rows = store.pendingRows()
                review(ReviewSource.Waiting, store.plan(rows), 0)
            } catch (e: Exception) { fail(e) }
        }
    }

    private fun payments(entries: List<UpiEntry>) {
        viewModelScope.launch {
            try {
                if (entries.isEmpty()) { _stage.value = IntakeStage.Failed("No payments were found in that file. Nothing was changed."); return@launch }
                val intake = UpiParser.toIntake(entries, store.knownAccounts(), clock.zone())
                // waiting bank messages are included so a payment's bank SMS and its UPI page are recognised as one
                val waiting = store.pendingRows()
                val plan = store.plan(intake.rows + waiting)
                fun fromPayments(r: com.mansaketh.chitta.core.intake.IntakeRow) = r.isUpi || r.extraNote.contains("UPI pp-")
                val upiOnly = plan.copy(candidates = plan.candidates.filter { fromPayments(it.row) }, claims = plan.claims.filter { fromPayments(it.row) })
                review(ReviewSource.Payments, upiOnly, intake.skipped)
            } catch (e: Exception) { fail(e) }
        }
    }

    fun readScreenshot(uri: Uri) {
        _stage.value = IntakeStage.Working("Reading the picture on this phone")
        viewModelScope.launch { try { payments(listOf(TextReaders.readScreenshot(context, uri))) } catch (e: Exception) { fail(e) } }
    }

    fun readPdf(uri: Uri) {
        _stage.value = IntakeStage.Working("Reading the statement")
        viewModelScope.launch { try { payments(UpiParser.parseStatementText(TextReaders.readPdf(context, uri))) } catch (e: Exception) { fail(e) } }
    }

    fun readCsv(uri: Uri) {
        _stage.value = IntakeStage.Working("Reading the file")
        viewModelScope.launch { try { payments(UpiParser.parseCsv(TextReaders.readText(context, uri))) } catch (e: Exception) { fail(e) } }
    }

    /** A screenshot, PDF or CSV shared to Chitta from another app: the share receiver kept a copy in the cache. */
    fun readShared(kind: String) {
        val f = File(context.cacheDir, IntakeStore.sharedName(kind))
        if (!f.isFile) { _stage.value = IntakeStage.Failed("The shared file is gone. Share it again."); return }
        val uri = Uri.fromFile(f)
        when (kind) { "image" -> readScreenshot(uri); "pdf" -> readPdf(uri); else -> readCsv(uri) }
    }

    private fun update(f: (IntakeStage.Review) -> IntakeStage.Review) { (_stage.value as? IntakeStage.Review)?.let { _stage.value = f(it) } }
    private fun List<IntakeChoice>.at(i: Int, f: (IntakeChoice) -> IntakeChoice) = mapIndexed { n, c -> if (n == i) f(c) else c }
    private fun duplicate(c: IntakeChoice) = IntakeReason.PossibleDuplicate in c.candidate.reasons

    fun toggle(i: Int) = update { s -> s.copy(choices = s.choices.at(i) { c -> if (c.ticked || c.saveable) c.copy(ticked = !c.ticked) else c }) }

    fun setAccount(i: Int, accountId: String) = update { s ->
        s.copy(choices = s.choices.at(i) { c -> c.copy(accountId = accountId, ticked = c.ticked || (c.categoryId != null && !duplicate(c))) })
    }

    fun setCategory(i: Int, p: CategoryPick) = update { s ->
        s.copy(choices = s.choices.at(i) { c -> c.copy(categoryId = p.categoryId, subCategoryId = p.subCategoryId, ticked = c.ticked || (c.accountId != null && !duplicate(c))) })
    }

    /** One category for every row that has none, ticking the rows that can now be saved (not possible duplicates). */
    fun setCategoryForMissing(p: CategoryPick) = update { s ->
        s.copy(choices = s.choices.map { c -> if (c.categoryId == null) c.copy(categoryId = p.categoryId, subCategoryId = p.subCategoryId, ticked = c.accountId != null && !duplicate(c)) else c })
    }

    /** Makes the account this row's bank or wallet name stands for, and puts every row of that name into it. */
    fun createAccountFor(i: Int) {
        val s = _stage.value as? IntakeStage.Review ?: return
        val name = s.choices.getOrNull(i)?.candidate?.row?.walletName?.takeIf { it.isNotBlank() && it != "Unassigned" } ?: return
        viewModelScope.launch {
            val account = store.createAccount(name)
            update { cur ->
                cur.copy(
                    accounts = (cur.accounts.filter { it.id != account.id } + account),
                    choices = cur.choices.map { c ->
                        if (c.accountId == null && c.candidate.row.walletName.equals(name, ignoreCase = true)) c.copy(accountId = account.id, ticked = c.categoryId != null && !duplicate(c)) else c
                    },
                )
            }
        }
    }

    /** Takes a message out of the list for good. */
    fun dismiss(i: Int) {
        val s = _stage.value as? IntakeStage.Review ?: return
        val c = s.choices.getOrNull(i) ?: return
        viewModelScope.launch {
            if (s.source == ReviewSource.Waiting) store.dismiss(listOf(c.candidate.row.sourceId))
            update { cur -> cur.copy(choices = cur.choices.filterIndexed { n, _ -> n != i }) }
        }
    }

    fun save() {
        val s = _stage.value as? IntakeStage.Review ?: return
        viewModelScope.launch {
            try {
                val n = store.saveReviewed(s.choices, s.accounts, s.claims)
                _stage.value = IntakeStage.Saved(n, s.claims.size)
            } catch (e: Exception) {
                _stage.value = IntakeStage.Failed("It stopped and nothing was saved (${e.message ?: e.javaClass.simpleName}).")
            }
        }
    }
}
