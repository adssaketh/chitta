package com.mansaketh.chitta.data

import android.content.Context
import com.mansaketh.chitta.core.database.BackupFacts
import com.mansaketh.chitta.core.database.ChittaDatabase
import com.mansaketh.chitta.core.database.DatabaseFiles
import com.mansaketh.chitta.core.domain.RestoreKind
import com.mansaketh.chitta.core.domain.RestorePoint
import com.mansaketh.chitta.core.domain.RestorePoints
import com.mansaketh.chitta.di.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data Recovery: copies of the whole book kept on this phone. One automatic point a day, one before every restore, CSV import and
 * delete-everything, and any you make by hand. Only the newest few of each kind are kept ([RestorePoints.toDelete]).
 */
@Singleton
class RestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ChittaDatabase,
    private val clock: Clock,
) {
    private val dir: File get() = DatabaseFiles.restoreDir(context)

    fun list(): List<RestorePoint> =
        RestorePoints.sorted(dir.listFiles().orEmpty().mapNotNull { RestorePoints.parse(it.name, it.length()) })

    fun fileOf(point: RestorePoint): File = File(dir, point.fileName)

    /** Makes a restore point of the live book. Returns null when it could not be made (nothing else is changed). */
    suspend fun create(kind: RestoreKind): RestorePoint? = withContext(Dispatchers.IO) {
        try {
            val name = RestorePoints.fileName(kind, clock.nowMs())
            val file = File(dir, name)
            DatabaseFiles.snapshot(db, file)
            val point = RestorePoints.parse(name, file.length())
            prune()
            point
        } catch (e: Exception) {
            null
        }
    }

    /** Today's automatic point, if the book has anything in it and none exists for today. */
    suspend fun autoIfDue() {
        val hasData = withContext(Dispatchers.IO) { db.accounts().count() > 0 }
        if (hasData && RestorePoints.isAutoDue(list(), clock.nowMs(), clock.zone())) create(RestoreKind.Auto)
    }

    fun delete(point: RestorePoint) { fileOf(point).delete() }

    private fun prune() { RestorePoints.toDelete(list()).forEach { fileOf(it).delete() } }

    suspend fun inspect(point: RestorePoint): BackupFacts? = withContext(Dispatchers.IO) { DatabaseFiles.inspect(fileOf(point)) }

    /**
     * Keeps [file] to replace the book at the next start. Makes a restore point of the current book first, so a restore can itself be
     * undone. If the book has data and that point cannot be made, nothing is staged.
     */
    suspend fun stageRestore(file: File): Boolean {
        val hasData = withContext(Dispatchers.IO) { db.accounts().count() > 0 }
        if (hasData && create(RestoreKind.BeforeRestore) == null) return false
        return withContext(Dispatchers.IO) { try { DatabaseFiles.stageRestore(context, file); true } catch (e: Exception) { false } }
    }
}
