package com.mansaketh.chitta.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mansaketh.chitta.core.domain.BackupVerdict
import com.mansaketh.chitta.core.model.Account
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Restore points and backup files: a snapshot of the live book reads back as a valid Chitta backup, and a staged restore replaces the book. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseFilesTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var db: ChittaDatabase

    private fun account(id: String) = Account(id, id, null, null, 0, null, 0, "INR", null, 2, null, null)

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.databaseBuilder(context, ChittaDatabase::class.java, ChittaDatabase.FILE_NAME).allowMainThreadQueries().build()
    }

    @After fun close() { if (db.isOpen) db.close(); context.deleteDatabase(ChittaDatabase.FILE_NAME) }

    @Test fun snapshotReadsBackAsAValidBackupWithTheSameCounts() {
        runBlocking { AccountRepository(db).save(account("a1")); AccountRepository(db).save(account("a2")) }
        val file = File(tmp.root, "snap.db")
        DatabaseFiles.snapshot(db, file)
        val facts = assertNotNull(DatabaseFiles.inspect(file))
        assertEquals(BackupVerdict.Ok(ChittaDatabase.VERSION), facts.verdict)
        assertEquals(2, facts.accounts)
        assertEquals(0, facts.entries)
    }

    @Test fun aFileThatIsNotADatabaseOrNotChittaIsRefused() {
        val junk = File(tmp.root, "junk.db").apply { writeText("this is not sqlite at all") }
        val facts = DatabaseFiles.inspect(junk)
        assertTrue(facts == null || facts.verdict is BackupVerdict.Refused)
        assertNull(DatabaseFiles.inspect(File(tmp.root, "missing.db")))
    }

    @Test fun aStagedRestoreReplacesTheBookOnTheNextStart() {
        runBlocking { AccountRepository(db).save(account("old")) }
        val snap = File(tmp.root, "snap.db")
        DatabaseFiles.snapshot(db, snap)
        runBlocking { AccountRepository(db).save(account("added-later")) }
        db.close()

        assertFalse(DatabaseFiles.hasPending(context))
        DatabaseFiles.stageRestore(context, snap)
        assertTrue(DatabaseFiles.hasPending(context))
        assertTrue(DatabaseFiles.applyPending(context))
        assertFalse(DatabaseFiles.hasPending(context))

        val reopened = Room.databaseBuilder(context, ChittaDatabase::class.java, ChittaDatabase.FILE_NAME).allowMainThreadQueries().build()
        try {
            val ids = runBlocking { reopened.accounts().getAll() }.map { it.id }
            assertEquals(listOf("old"), ids)
        } finally { reopened.close() }
    }

    @Test fun applyingWithNothingStagedDoesNothing() = assertFalse(DatabaseFiles.applyPending(context))
}
