package com.mansaketh.chitta.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mansaketh.chitta.core.model.CardDue
import com.mansaketh.chitta.core.model.FixedDeposit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AssetsRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: ChittaDatabase
    private lateinit var repo: AssetsRepository

    private fun fd(id: String) = FixedDeposit(id, "Axis Bank", "5521", 15_000_000, "7.1", 1_000, 2_000, 4, 16_090_000, null, false, "", true, "s1", null)
    private fun due(id: String, amount: Long = 500_000) = CardDue(id, "Card", "spend", amount, 1_000, "", null)

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.databaseBuilder(context, ChittaDatabase::class.java, ChittaDatabase.FILE_NAME).allowMainThreadQueries().build()
        repo = AssetsRepository(db)
    }

    @After fun close() { if (db.isOpen) db.close(); context.deleteDatabase(ChittaDatabase.FILE_NAME) }

    @Test fun depositsSaveReadAndDeleteWithUndo() = runBlocking {
        repo.saveDeposit(fd("a"))
        assertEquals(listOf("a"), repo.observeDeposits().first().map { it.id })
        assertEquals(fd("a"), repo.deposits().single())
        val gone = repo.deleteDeposit("a")
        assertEquals(fd("a"), gone)
        assertTrue(repo.deposits().isEmpty())
        assertNull(repo.deleteDeposit("a"))
        repo.saveDeposit(gone!!)
        assertEquals(1, repo.deposits().size)
    }

    @Test fun aDepositWithTheSameIdIsAddedOnce() = runBlocking {
        assertTrue(repo.addDepositIfNew(fd("sms-1")))
        assertFalse(repo.addDepositIfNew(fd("sms-1").copy(principal = 1)))
        assertEquals(15_000_000, repo.deposits().single().principal)
    }

    @Test fun aCardMessageCountsOnceAndOnlyTheNewOnesComeBack() = runBlocking {
        val first = repo.addCardDues(listOf(due("cd-1"), due("cd-2"), due("cd-1")))
        assertEquals(listOf("cd-1", "cd-2"), first.map { it.id })
        val again = repo.addCardDues(listOf(due("cd-2"), due("cd-3")))
        assertEquals(listOf("cd-3"), again.map { it.id })
        assertEquals(3, repo.cardDues().size)
        repo.deleteCardDues(again.map { it.id })
        assertEquals(listOf("cd-1", "cd-2"), repo.observeCardDues().first().map { it.id })
    }

    @Test fun fundsSaveReadAndDeleteWithUndo() = runBlocking {
        val f = com.mansaketh.chitta.core.model.MutualFund("m1", "Sample", "INF000A00000", "", "100.500", null, "manual", 1_000L, "52.3456", "2026-11-12", "", null)
        repo.saveFund(f)
        assertEquals(listOf("m1"), repo.observeFunds().first().map { it.id })
        assertEquals(f, repo.funds().single())
        val gone = repo.deleteFund("m1")
        assertEquals(f, gone)
        assertTrue(repo.funds().isEmpty())
        assertNull(repo.deleteFund("m1"))
        repo.saveFund(gone!!)
        assertEquals(1, repo.funds().size)
    }
}
