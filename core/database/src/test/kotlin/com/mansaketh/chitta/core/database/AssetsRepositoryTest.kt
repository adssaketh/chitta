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

    @Test fun npsAccountDeletesWithItsSchemesAndComesBackTogether() = runBlocking {
        val acc = com.mansaketh.chitta.core.model.NpsAccount("acc", "NPS Tier I", 1_367_700, 5_000L, "", null)
        val scheme = com.mansaketh.chitta.core.model.MutualFund("s1", "Sample Pension Scheme E", "", "", "19.1467", null, "manual", 5_000L, "70.1151", "2026-10-31", "", null, "", "acc")
        val fund = com.mansaketh.chitta.core.model.MutualFund("m1", "Sample", "", "", "1", null, "manual", 5_000L, null, null, "", null, "", "")
        repo.saveNps(acc); repo.replaceFunds(emptyList(), listOf(scheme, fund))
        val (gone, schemes) = repo.deleteNps("acc")!!
        assertEquals(acc, gone); assertEquals(listOf(scheme), schemes)
        assertEquals(listOf("m1"), repo.funds().map { it.id })   // the mutual fund is untouched
        assertTrue(repo.nps().isEmpty())
        repo.saveNps(gone); repo.replaceFunds(emptyList(), schemes)
        assertEquals(listOf(acc), repo.nps())
        assertEquals(setOf("m1", "s1"), repo.funds().map { it.id }.toSet())
    }

    @Test fun replaceFundsRemovesAndSavesInOneGo() = runBlocking {
        fun f(id: String) = com.mansaketh.chitta.core.model.MutualFund(id, "F$id", "", "", "1", null, "cas", 1L, null, null, "", null, "1", "")
        repo.replaceFunds(emptyList(), listOf(f("a"), f("b")))
        repo.replaceFunds(listOf("a"), listOf(f("c").copy(units = "2")))
        assertEquals(listOf("b", "c"), repo.funds().map { it.id })
        assertEquals("2", repo.funds().first { it.id == "c" }.units)
    }

    private fun asset(id: String, kind: com.mansaketh.chitta.core.model.AssetKind = com.mansaketh.chitta.core.model.AssetKind.EPF) =
        com.mansaketh.chitta.core.model.ManualAsset(id, kind, "Asset $id", "note", 1_000L)
    private fun value(id: String, assetId: String, asOf: Long, paise: Long) = com.mansaketh.chitta.core.model.AssetValue(id, assetId, asOf, paise, asOf)

    @Test fun assetsSaveAddValuesAndReadBackInOrder() = runBlocking {
        repo.addAsset(asset("e1"), value("v1", "e1", 3_000, 5_000_000))
        repo.saveAssetValue(value("v2", "e1", 2_000, 4_000_000))      // an older value typed later still sorts by its day
        repo.saveAsset(asset("e1").copy(name = "EPF main"))
        assertEquals("EPF main", repo.observeAssets().first().single().name)
        assertEquals(listOf("v2", "v1"), repo.assetValues("e1").map { it.id })
        assertEquals(listOf("v2", "v1"), repo.observeAssetValues().first().map { it.id })
        repo.deleteAssetValue("v2")
        assertEquals(listOf("v1"), repo.assetValues("e1").map { it.id })
    }

    @Test fun deletingAnAssetTakesItsValuesAndUndoBringsThemBack() = runBlocking {
        repo.addAsset(asset("p1", com.mansaketh.chitta.core.model.AssetKind.PPF), value("v1", "p1", 1_000, 100))
        repo.addAsset(asset("p2", com.mansaketh.chitta.core.model.AssetKind.PPF), value("v2", "p2", 1_000, 200))
        val gone = repo.deleteAsset("p1")!!
        assertEquals(listOf("p2"), repo.assets().map { it.id })
        assertTrue(repo.assetValues("p1").isEmpty())
        assertEquals(listOf("v2"), repo.observeAssetValues().first().map { it.id })     // the other asset's values are untouched
        assertNull(repo.deleteAsset("p1"))
        repo.restoreAsset(gone.first, gone.second)
        assertEquals(listOf("p1", "p2"), repo.assets().map { it.id }.sorted())
        assertEquals(listOf("v1"), repo.assetValues("p1").map { it.id })
    }

    @Test fun anUnknownKindWrittenByANewerBuildIsSkippedNotGuessed() = runBlocking {
        db.misc().upsertAsset(ManualAssetEntity("x1", "BITCOIN", "Mystery", "", 1L))
        repo.saveAsset(asset("e1"))
        assertEquals(listOf("e1"), repo.assets().map { it.id })
    }
}
