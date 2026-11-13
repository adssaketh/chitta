package com.mansaketh.chitta.ui.imports

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.*
import com.mansaketh.chitta.core.domain.Balances
import com.mansaketh.chitta.core.importer.ImportOutcome
import com.mansaketh.chitta.core.importer.PaddhuImporter
import com.mansaketh.chitta.core.importer.Report
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Book
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.data.AppPrefs
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.di.IdSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed interface ImportState {
    data object Idle : ImportState
    data object Reading : ImportState
    data class Preview(val book: Book, val report: Report, val balances: List<Pair<Account, Paise>>, val cachedFile: File) : ImportState
    data class Refused(val reason: String) : ImportState
    data object Importing : ImportState
    data class Done(val rows: Int) : ImportState
    data class Blocked(val message: String) : ImportState
    data class Failed(val message: String) : ImportState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ChittaDatabase,
    private val books: BookRepository,
    private val accounts: AccountRepository,
    private val categoryWriter: CategoryWriter,
    private val prefs: AppPrefs,
    private val ids: IdSource,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state

    /** Days left in which "Undo import" is offered, or 0. */
    fun undoDaysLeft(): Long {
        val at = prefs.importedAtMs
        if (at == 0L) return 0
        val left = AppPrefs.UNDO_IMPORT_DAYS - TimeUnit.MILLISECONDS.toDays(clock.nowMs() - at)
        return left.coerceAtLeast(0)
    }

    fun reset() { _state.value = ImportState.Idle }

    /** Copies the picked file into the app's cache (the original is never touched), then reads it without saving anything. */
    fun pick(uri: Uri) {
        _state.value = ImportState.Reading
        viewModelScope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "import").apply { mkdirs() }
                    val file = File(dir, "backup.sqlite").also { if (it.exists()) it.delete() }
                    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: error("Could not open that file.")
                    AndroidBackupSource(file).use { src -> PaddhuImporter().read(src) } to file
                }
                val (result, file) = outcome
                _state.value = when (result) {
                    is ImportOutcome.Refused -> ImportState.Refused(result.reason)
                    is ImportOutcome.Ready -> {
                        val bal = Balances.balances(result.book.transactions)
                        ImportState.Preview(result.book, result.report, result.book.accounts.sortedBy { it.sortOrder }.map { it to (bal[it.id] ?: Paise.ZERO) }, file)
                    }
                }
            } catch (e: Exception) {
                _state.value = ImportState.Failed("That file could not be read (${e.message ?: e.javaClass.simpleName}). Nothing was changed.")
            }
        }
    }

    /** Keeps a copy of the backup (the restore point), writes the book in one transaction, and starts the 30-day Undo window. */
    fun import(preview: ImportState.Preview) {
        _state.value = ImportState.Importing
        viewModelScope.launch {
            try {
                val kept = withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "restore").apply { mkdirs() }
                    File(dir, "paddhu-backup-${clock.nowMs()}.sqlite").also { preview.cachedFile.copyTo(it, overwrite = true) }
                }
                when (val r = books.writeFresh(preview.book)) {
                    is WriteResult.Written -> {
                        prefs.importedAtMs = clock.nowMs(); prefs.importBackupPath = kept.absolutePath; prefs.firstRunDone = true
                        _state.value = ImportState.Done(r.rows)
                    }
                    is WriteResult.NotEmpty -> { kept.delete(); _state.value = ImportState.Blocked("This book already has ${r.accounts} accounts and ${r.transactions} entries, so nothing was imported. Use Undo import first if you want to start over.") }
                }
            } catch (e: Exception) {
                _state.value = ImportState.Failed("The import stopped and nothing was saved (${e.message ?: e.javaClass.simpleName}).")
            }
        }
    }

    /** Wipes the book back to empty. Only offered for 30 days after an import, and it also removes anything added since. */
    fun undoImport(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.clearAllTables() }
            prefs.clearImportMarker(); prefs.firstRunDone = false
            onDone()
        }
    }

    /** First run, fresh start: one Cash account and a plain set of categories the person can rename. */
    fun startFresh(onDone: () -> Unit) {
        viewModelScope.launch {
            val now = clock.nowMs()
            accounts.save(Account("acc-cash", "Cash", null, null, now, null, 0, "INR", null, 2, null, null))
            val spend = listOf("Food", "Groceries", "Transport", "Bills", "Rent", "Shopping", "Health", "Entertainment", "Travel", "Other")
            val earn = listOf("Salary", "Other income")
            fun cat(i: Int, n: String, income: Boolean) = Category("cat-${if (income) "in" else "out"}-$i", n, null, null, null, now, null, i, income, null, null)
            categoryWriter.saveAll(spend.mapIndexed { i, n -> cat(i, n, false) } + earn.mapIndexed { i, n -> cat(i, n, true) })
            prefs.firstRunDone = true
            onDone()
        }
    }
}
