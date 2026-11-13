package com.mansaketh.chitta.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mansaketh.chitta.core.importer.AccountCheck
import com.mansaketh.chitta.core.importer.ImportOutcome
import com.mansaketh.chitta.core.importer.JdbcBackupSource
import com.mansaketh.chitta.core.importer.PaddhuImporter
import com.mansaketh.chitta.core.importer.TestDb
import com.mansaketh.chitta.core.model.Book
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The whole path: Paddhu file -> importer -> Room -> back out, with nothing lost or changed on the way. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportToRoomTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: ChittaDatabase
    private val importer = PaddhuImporter(ZoneId.of("Asia/Kolkata"))

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ChittaDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun backupFile() = TestDb.create(File(tmp.root, "paddhu.sqlite"))

    private fun importViaAndroidReader(file: File): Book =
        AndroidBackupSource(file).use { assertIs<ImportOutcome.Ready>(importer.read(it)).book }

    private fun Book.sorted() = copy(
        accounts = accounts.sortedWith(compareBy({ it.sortOrder }, { it.name })),
        categories = categories.sortedWith(compareBy({ it.sortOrder }, { it.name })),
        transactions = transactions.sortedWith(compareByDescending<com.mansaketh.chitta.core.model.Transaction> { it.dateMs }.thenBy { it.id }),
        budgets = budgets.sortedWith(compareBy({ it.sortOrder }, { it.name })),
        categoryLimits = categoryLimits.sortedBy { it.id },
        objectives = objectives.sortedWith(compareBy({ it.sortOrder }, { it.name })),
        titleMemory = titleMemory.sortedWith(compareBy({ it.sortOrder }, { it.id })),
        scanTemplates = scanTemplates.sortedBy { it.id },
        activityLog = activityLog.sortedWith(compareBy({ it.atMs }, { it.id })),
        settings = settings.sortedBy { it.key },
        loanTerms = loanTerms.sortedBy { it.objectiveId },
        fixedDeposits = fixedDeposits.sortedBy { it.id },
        cardDues = cardDues.sortedWith(compareBy({ it.dateMs }, { it.id })),
    )

    @Test fun androidReaderAndJdbcReaderSeeTheSameBook() {
        val file = backupFile()
        val viaJdbc = JdbcBackupSource(file).use { assertIs<ImportOutcome.Ready>(importer.read(it)).book }
        assertEquals(viaJdbc, importViaAndroidReader(file))
    }

    @Test fun importedBookComesBackIdentical() = runBlocking<Unit> {
        val book = importViaAndroidReader(backupFile())
        val result = BookRepository(db).writeFresh(book)
        assertIs<WriteResult.Written>(result)
        assertEquals(book.sorted(), BookRepository(db).readAll())
        Unit
    }

    @Test fun rowCountsAreInTheDatabase() = runBlocking<Unit> {
        val book = importViaAndroidReader(backupFile())
        BookRepository(db).writeFresh(book)
        assertEquals(2, db.accounts().count())
        assertEquals(3, db.categories().count())
        assertEquals(8, db.transactions().count())
        Unit
    }

    @Test fun databaseSumsMatchTheImportersConversionCheck() = runBlocking<Unit> {
        val file = backupFile()
        val outcome = AndroidBackupSource(file).use { assertIs<ImportOutcome.Ready>(importer.read(it)) }
        BookRepository(db).writeFresh(outcome.book)
        val fromDb = AccountRepository(db).sums().associateBy { it.accountId }
        outcome.report.accountChecks.forEach { check: AccountCheck ->
            assertEquals(check.sourceSumPaise, fromDb.getValue(check.accountId).sumPaise, "account ${check.accountId}")
            assertEquals(check.transactions, fromDb.getValue(check.accountId).count)
        }
    }

    @Test fun aSecondImportIntoAFilledDatabaseChangesNothing() = runBlocking<Unit> {
        val book = importViaAndroidReader(backupFile())
        val repo = BookRepository(db)
        repo.writeFresh(book)
        val second = repo.writeFresh(book)
        assertEquals(WriteResult.NotEmpty(accounts = 2, transactions = 8), second)
        assertEquals(8, db.transactions().count())
    }

    @Test fun anInterruptedWriteRollsEverythingBack() = runBlocking<Unit> {
        val book = importViaAndroidReader(backupFile())
        // two transactions with the same id: the insert fails halfway through the write
        val broken = book.copy(transactions = book.transactions + book.transactions.first())
        assertFailsWith<Exception> { BookRepository(db).writeFresh(broken) }
        assertEquals(0, db.accounts().count())
        assertEquals(0, db.categories().count())
        assertEquals(0, db.transactions().count())
        // and a clean retry still works
        assertIs<WriteResult.Written>(BookRepository(db).writeFresh(book))
    }

    @Test fun theBackupFileIsLeftExactlyAsItWas() {
        val file = backupFile()
        val before = file.readBytes()
        importViaAndroidReader(file)
        assertTrue(before.contentEquals(file.readBytes()))
    }

    @Test fun anEmptyFileIsRefusedNotCrashed() {
        val empty = File(tmp.root, "chitta.sqlite").also { it.createNewFile() } // like the 0-byte file seen on the phone
        val outcome = AndroidBackupSource(empty).use { importer.read(it) }
        assertIs<ImportOutcome.Refused>(outcome)
    }
}
