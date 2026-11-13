package com.mansaketh.chitta.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Builds a real version-1 database from the committed schema file (the exact SQL Room used for version 1),
 * puts an entry in it, opens it with the current database and the migrations, and checks nothing was lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun createVersion(file: File, version: Int) {
        val schema = Json.parseToJsonElement(File("schemas/com.mansaketh.chitta.core.database.ChittaDatabase/$version.json").readText()).jsonObject["database"]!!.jsonObject
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            schema["entities"]!!.jsonArray.forEach { e ->
                val o = e as JsonObject
                val table = o["tableName"]!!.jsonPrimitive.content
                db.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                (o["indices"] as? JsonArray)?.forEach { i -> db.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL("INSERT INTO account (id,name,createdAtMs,sortOrder,decimals,archived) VALUES ('a1','HDFC',0,0,2,0)")
            db.execSQL(
                "INSERT INTO txn (id,name,amountPaise,note,categoryId,accountId,dateMs,income,paid,skipPaid,isPrivate) " +
                    "VALUES ('t1','Swiggy',-48600,'',  'food','a1',1000,0,1,0,0)"
            )
            db.version = version
        } finally {
            db.close()
        }
    }

    @Test fun versionOneEntriesSurviveAndGetTheNewColumns() = runBlocking {
        val file = File(tmp.root, "old.db")
        createVersion(file, 1)
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ChittaDatabase::class.java, file.absolutePath)
            .addMigrations(Migrations.V1_TO_V2, Migrations.V2_TO_V3).allowMainThreadQueries().build()
        try {
            val t = db.transactions().get("t1")!!
            assertEquals(-48_600L, t.amountPaise)
            assertNull(t.originalAmountPaise)
            assertNull(t.exchangeRate)
            assertNull(t.anchorDay)
            assertEquals("HDFC", db.accounts().get("a1")!!.name)
            // The new tables work after migrating.
            val repo = LedgerRepository(db) { 5L }
            repo.add(listOf(Snapshots.decode(Snapshots.encode(t.toModel()))).map { it.copy(id = "t2") })
            assertEquals(1, repo.history("t2").size)
        } finally {
            db.close()
        }
    }

    @Test fun versionTwoGainsAnchorDayAndKeepsEntries() = runBlocking {
        val file = File(tmp.root, "v2.db")
        createVersion(file, 2)
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ChittaDatabase::class.java, file.absolutePath)
            .addMigrations(Migrations.V1_TO_V2, Migrations.V2_TO_V3).allowMainThreadQueries().build()
        try {
            val t = db.transactions().get("t1")!!
            assertEquals(-48_600L, t.amountPaise)
            assertNull(t.anchorDay)
            // The new column can be written and read back.
            db.transactions().upsert(t.copy(id = "t3", anchorDay = 31))
            assertEquals(31, db.transactions().get("t3")!!.anchorDay)
        } finally {
            db.close()
        }
    }
}
