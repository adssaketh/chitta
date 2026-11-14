package com.mansaketh.chitta.ui.imports

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.database.TitleRepository
import com.mansaketh.chitta.core.domain.Csv
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.intake.ZerodhaLedger
import com.mansaketh.chitta.core.intake.XlsxReader
import com.mansaketh.chitta.core.domain.CsvContext
import com.mansaketh.chitta.core.domain.CsvImport
import com.mansaketh.chitta.core.domain.CsvMapping
import com.mansaketh.chitta.core.domain.CsvParse
import com.mansaketh.chitta.core.domain.CsvReason
import com.mansaketh.chitta.core.domain.CsvTemplate
import com.mansaketh.chitta.core.domain.DatePattern
import com.mansaketh.chitta.core.domain.RestoreKind
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.data.AppPrefs
import com.mansaketh.chitta.data.RestoreManager
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.di.IdSource
import com.mansaketh.chitta.ui.UndoCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A category (or subcategory) the person can pick in the review list. */
data class CategoryPick(val categoryId: String, val subCategoryId: String?, val label: String)

sealed interface CsvStage {
    data object Idle : CsvStage
    data object Reading : CsvStage
    /** The file is read; the person confirms which column is which. */
    data class Mapping(val fileName: String, val rows: List<List<String>>, val mapping: CsvMapping, val templates: List<CsvTemplate>, val notice: String? = null) : CsvStage
    /** Rows are read and checked against the book. Nothing is saved until [CsvImportViewModel.save]. */
    data class Review(val parse: CsvParse, val choices: List<CsvImport.Choice>, val picks: List<CategoryPick>, val accounts: List<Account>) : CsvStage
    data object Saving : CsvStage
    data class Saved(val count: Int) : CsvStage
    data class Failed(val message: String) : CsvStage
}

/**
 * CSV import: pick a file, confirm the columns, review what was found, save only what is ticked. Nothing is written until the last step,
 * the save is one transaction, a restore point is made first, and the saved entries can be undone for 10 seconds (master sections 2 and 13).
 */
@HiltViewModel
class CsvImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val ruleRepo: com.mansaketh.chitta.core.database.RuleRepository,
    private val titleRepo: TitleRepository,
    private val ledger: LedgerRepository,
    private val restore: RestoreManager,
    private val prefs: AppPrefs,
    private val undo: UndoCenter,
    private val ids: IdSource,
    private val clock: Clock,
) : ViewModel() {
    /** The accounts that can be chosen for rows that name none. */
    val accountsForPicker: StateFlow<List<Account>> =
        accountRepo.observeAll().map { list -> list.filter { !it.archived } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _stage = MutableStateFlow<CsvStage>(CsvStage.Idle)
    val stage: StateFlow<CsvStage> = _stage

    fun reset() { _stage.value = CsvStage.Idle }

    private fun templates(): List<CsvTemplate> = prefs.csvTemplates.mapNotNull { CsvTemplate.decode(it) }.sortedBy { it.name.lowercase() }

    /** Reads the picked file (a copy, never changing the original) and guesses the columns. */
    fun pick(uri: Uri, fileName: String) {
        _stage.value = CsvStage.Reading
        viewModelScope.launch {
            try {
                val (rows, notice) = withContext(Dispatchers.IO) { readRows(uri) }
                if (rows.size < 2) { _stage.value = CsvStage.Failed("That file has no rows to import."); return@launch }
                val accounts = accountRepo.observeAll().first().filter { !it.archived }
                val guessed = CsvImport.guessMapping(rows[0])
                val samples = guessed.date?.let { col -> rows.drop(1).take(20).mapNotNull { it.getOrNull(col) } }.orEmpty()
                val pattern = DatePattern.detect(samples, clock.zone()) ?: DatePattern.Iso
                _stage.value = CsvStage.Mapping(fileName, rows, guessed.copy(datePattern = pattern, defaultAccountId = accounts.firstOrNull()?.id), templates(), notice)
            } catch (e: Exception) {
                _stage.value = CsvStage.Failed("That file could not be read (${e.message ?: e.javaClass.simpleName}). Nothing was changed.")
            }
        }
    }

    /**
     * The rows of the picked file and an optional note for the mapping screen. A Zerodha Console ledger (.xlsx or .csv) is checked first (opening
     * balance, every row's running balance, closing balance) and turned into plain Date / Particulars / Debit / Credit rows; a file that fails
     * the check is refused with the reason. Anything else is read as CSV.
     */
    private fun readRows(uri: Uri): Pair<List<List<String>>, String?> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not open that file.")
        require(bytes.size <= MAX_BYTES) { "That file is larger than 8 MB." }
        val isXlsx = XlsxReader.looksLikeXlsx(bytes)
        val rows = if (isXlsx) XlsxReader.firstSheet(bytes) ?: error("That Excel file could not be opened.") else Csv.parse(String(bytes, Charsets.UTF_8))
        return when (val r = ZerodhaLedger.read(rows)) {
            is ZerodhaLedger.Result.Ready -> {
                val l = r.ledger
                l.toCsvRows() to "Zerodha ledger: opening ${IndianFormat.rupees(Paise(l.opening))}, closing ${IndianFormat.rupees(Paise(l.closing))}, every row adds up. The opening balance is the first row; untick it if the account already holds that money."
            }
            is ZerodhaLedger.Result.Refused ->
                if (r.looksLikeLedger) error("This looks like a Zerodha ledger but ${r.reason.replaceFirstChar { it.lowercase() }}")
                else if (isXlsx) error("That Excel file is not a Zerodha ledger. Save it as CSV and choose it again.")
                else rows to null
        }
    }

    fun setMapping(m: CsvMapping) { (_stage.value as? CsvStage.Mapping)?.let { _stage.value = it.copy(mapping = m) } }

    fun applyTemplate(t: CsvTemplate) {
        val s = _stage.value as? CsvStage.Mapping ?: return
        _stage.value = s.copy(mapping = t.mapping.copy(defaultAccountId = s.mapping.defaultAccountId))
    }

    fun saveTemplate(name: String) {
        val s = _stage.value as? CsvStage.Mapping ?: return
        val clean = name.trim().replace('\n', ' ')
        if (clean.isEmpty()) return
        val keep = prefs.csvTemplates.filter { CsvTemplate.decode(it)?.name?.equals(clean, ignoreCase = true) != true }
        prefs.csvTemplates = (keep + CsvTemplate(clean, s.mapping.copy(defaultAccountId = null)).encode()).toSet()
        _stage.value = s.copy(templates = templates())
    }

    /** Reads every row against the book and opens the review list. */
    fun toReview() {
        val s = _stage.value as? CsvStage.Mapping ?: return
        viewModelScope.launch {
            try {
                val accounts = accountRepo.observeAll().first()
                val categories = categoryRepo.observeAll().first()
                val ctx = CsvContext(accounts, categories, ledger.observeAllOnce(), titleRepo.observeAll().first(), clock.zone())
                val parse = withContext(Dispatchers.Default) { CsvImport.read(s.rows, s.mapping, ctx) }
                val rules = ruleRepo.all()
                // Rules only fill an empty category, as a suggestion shown for review; ticks stay as the importer set them (D94).
                val choices = CsvImport.initialChoices(parse).map { c ->
                    if (c.categoryId != null) c
                    else com.mansaketh.chitta.core.domain.RuleSuggest.forRow(rules, c.row.title, c.row.note, c.row.amountPaise, c.accountId, c.row.income, categories)
                        ?.let { c.copy(categoryId = it.categoryId, subCategoryId = it.subCategoryId) } ?: c
                }
                _stage.value = CsvStage.Review(parse, choices, picks(categories), accounts.filter { !it.archived })
            } catch (e: Exception) {
                _stage.value = CsvStage.Failed("The file could not be checked (${e.message ?: e.javaClass.simpleName}). Nothing was changed.")
            }
        }
    }

    private fun picks(categories: List<Category>): List<CategoryPick> {
        val live = categories.filter { !it.archived && it.id != "0" }
        val mains = live.filter { it.mainCategoryId == null }.sortedBy { it.sortOrder }
        return mains.flatMap { m ->
            listOf(CategoryPick(m.id, null, m.name)) +
                live.filter { it.mainCategoryId == m.id }.sortedBy { it.sortOrder }.map { CategoryPick(m.id, it.id, m.name + " > " + it.name) }
        }
    }

    private fun updateChoices(f: (List<CsvImport.Choice>) -> List<CsvImport.Choice>) {
        val s = _stage.value as? CsvStage.Review ?: return
        _stage.value = s.copy(choices = f(s.choices))
    }

    fun toggle(index: Int) = updateChoices { list ->
        list.mapIndexed { i, c -> if (i == index && (c.ticked || c.saveable)) c.copy(ticked = !c.ticked) else c }
    }

    fun setAccount(index: Int, accountId: String) = updateChoices { list ->
        list.mapIndexed { i, c -> if (i == index) c.copy(accountId = accountId, ticked = c.ticked || (c.categoryId != null && CsvReason.PossibleDuplicate !in c.row.reasons)) else c }
    }

    fun setCategory(index: Int, pick: CategoryPick) = updateChoices { list ->
        list.mapIndexed { i, c -> if (i == index) c.copy(categoryId = pick.categoryId, subCategoryId = pick.subCategoryId, ticked = c.ticked || (c.accountId != null && CsvReason.PossibleDuplicate !in c.row.reasons)) else c }
    }

    /** Gives every row that has no category the same one, and ticks those that can now be saved (except possible duplicates). */
    fun setCategoryForMissing(pick: CategoryPick) = updateChoices { list ->
        list.map { c ->
            if (c.categoryId == null) c.copy(categoryId = pick.categoryId, subCategoryId = pick.subCategoryId, ticked = c.accountId != null && CsvReason.PossibleDuplicate !in c.row.reasons) else c
        }
    }

    fun save() {
        val s = _stage.value as? CsvStage.Review ?: return
        val entries = CsvImport.toEntries(s.choices, s.accounts, clock.nowMs(), ids::newId)
        if (entries.isEmpty()) return
        _stage.value = CsvStage.Saving
        viewModelScope.launch {
            try {
                // A restore point first, then one transaction for all the entries.
                restore.create(RestoreKind.BeforeImport)
                ledger.add(entries)
                val saved = entries.map { it.id }
                undo.post("Saved ${entries.size} ${if (entries.size == 1) "entry" else "entries"}") { ledger.delete(saved) }
                _stage.value = CsvStage.Saved(entries.size)
            } catch (e: Exception) {
                _stage.value = CsvStage.Failed("The import stopped and nothing was saved (${e.message ?: e.javaClass.simpleName}).")
            }
        }
    }

    companion object { const val MAX_BYTES = 8 * 1024 * 1024 }
}
