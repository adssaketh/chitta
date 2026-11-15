package com.mansaketh.chitta.cloud

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.mansaketh.chitta.core.database.ChittaDatabase
import com.mansaketh.chitta.core.database.SyncStore
import com.mansaketh.chitta.core.database.SyncTables
import com.mansaketh.chitta.core.domain.RestoreKind
import com.mansaketh.chitta.core.domain.SyncMerge
import com.mansaketh.chitta.core.domain.SyncRows
import com.mansaketh.chitta.core.domain.WireRow
import com.mansaketh.chitta.data.RestoreManager
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.diagnostics.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

sealed interface SyncResult {
    data class Done(val pulled: Int, val pushed: Int) : SyncResult
    data object NotSignedIn : SyncResult
    data object NeedsChoice : SyncResult
    data object Busy : SyncResult
    data class Failed(val message: String, val retryable: Boolean) : SyncResult
}

private class NewerAppException : Exception()
private class ClockSkewException : Exception()

/**
 * Sync between this person's own phones (Phase 9c part 2, step 2). Each row of the book is one Firestore document,
 * `users/{uid}/book/{table~id}`, holding its stamp (time, device, deleted) and its column values (DECISIONS D254 to D262).
 *
 * A round is: pull what changed in the cloud since the cursor and apply it (the newer stamp wins, [SyncMerge.decide]), then push what
 * changed here. Pull comes first so this phone never overwrites a newer cloud row with an older one it has just not heard about yet.
 * The published rules already allow the signed-in person to read and write everything under `users/{uid}`, so no rules change is needed.
 *
 * Every server step has a time limit: Firestore holds a write made without a connection for ever instead of failing. Before a push the
 * server is asked for its time once (a small write and read), which both proves the phone is online and checks its clock.
 */
@OptIn(FlowPreview::class)
@Singleton
class PersonalSync @Inject constructor(
    private val fb: FirebaseSession,
    private val store: SyncStore,
    private val prefs: SyncPrefs,
    private val restore: RestoreManager,
    private val clock: Clock,
) {
    private companion object {
        const val SERVER_TIMEOUT_MS = 25_000L
        const val PAGE = 400
        const val CLEAN_EVERY_MS = 24L * 3_600_000L
    }

    private val mutex = Mutex()

    private fun col(uid: String) = fb.db.collection("users/$uid/book")

    /** Runs a round when the pending-change count moves (a few seconds after an edit settles). Opening the app and the hourly job call [run] too. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            store.observePendingCount().debounce(4_000).filter { it > 0 }.collect { runCatching { run("edit") } }
        }
    }

    /** One round. Returns [SyncResult.Busy] when another round is already running. Never throws except to pass on cancellation. */
    suspend fun run(reason: String): SyncResult {
        if (!mutex.tryLock()) return SyncResult.Busy
        prefs.setRunning(true)
        try {
            val result = round()
            when (result) {
                is SyncResult.Failed -> { prefs.setError(result.message); Diagnostics.record("Sync ($reason)", RuntimeException(result.message)) }
                is SyncResult.Done -> prefs.done(clock.nowMs())
                else -> Unit
            }
            return result
        } finally {
            prefs.setRunning(false)
            mutex.unlock()
        }
    }

    private suspend fun round(): SyncResult {
        val uid = fb.uid ?: return SyncResult.NotSignedIn
        if (prefs.needsChoice.value) return SyncResult.NeedsChoice
        return try {
            if (prefs.syncUid != null && prefs.syncUid != uid) store.startOver()
            prefs.syncUid = uid
            if (prefs.bookReplaced) cloudFollowsThisPhone(uid)
            if (SyncMerge.tooStale(store.lastSyncMs(), clock.nowMs())) {
                prefs.setNeedsChoice(true)
                return SyncResult.NeedsChoice
            }
            store.seedIfFirstTime()
            val device = store.deviceId()
            val pulled = pull(uid, device)
            var pushed = 0
            if (store.pendingCount() > 0) {
                if (!clockIsRight(uid)) throw ClockSkewException()
                pushed = push(uid, device)
            }
            cleanOldMarkersIfDue(uid)
            store.setLastSyncMs(clock.nowMs())
            SyncResult.Done(pulled, pushed)
        } catch (e: TimeoutCancellationException) {
            SyncResult.Failed("No connection to the cloud right now. It will try again later.", true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            failure(e)
        }
    }

    private fun failure(e: Throwable): SyncResult.Failed = when {
        e is NewerAppException -> SyncResult.Failed("Another phone has a newer version of the app. Update this phone to keep syncing.", false)
        e is ClockSkewException -> SyncResult.Failed("This phone's clock is more than 2 minutes away from the real time. Set Date and time to automatic, then sync again.", false)
        e is FirebaseFirestoreException && e.code == Code.PERMISSION_DENIED ->
            SyncResult.Failed("The cloud refused this. Check that the Firestore rules let you read and write users/your id. (${e.message})", false)
        e is FirebaseFirestoreException && (e.code == Code.UNAVAILABLE || e.code == Code.DEADLINE_EXCEEDED) ->
            SyncResult.Failed("No connection to the cloud right now. It will try again later.", true)
        else -> SyncResult.Failed(e.message ?: "Sync failed.", true)
    }

    // ---- pull

    private suspend fun pull(uid: String, myDevice: String): Int {
        var applied = 0
        var newest: Timestamp? = parseCursor(store.cursor())
        var base: Query = col(uid).orderBy("srv")
        newest?.let { base = base.whereGreaterThan("srv", Timestamp(it.seconds - SyncMerge.CURSOR_OVERLAP_SECONDS, it.nanoseconds)) }
        var last: DocumentSnapshot? = null
        while (true) {
            var page = base.limit(PAGE.toLong())
            last?.let { page = page.startAfter(it) }
            val snap = withTimeout(SERVER_TIMEOUT_MS) { page.get(Source.SERVER).await() }
            if (snap.isEmpty) break
            val rows = ArrayList<WireRow>()
            for (d in snap.documents) {
                val w = parse(d)
                if (w != null) {
                    if (w.schema > ChittaDatabase.VERSION) throw NewerAppException()
                    if (w.deviceId != myDevice) rows.add(w) // what this phone sent itself comes back and needs no work
                }
                val at = d.getTimestamp("srv")
                if (at != null && (newest == null || at > newest)) newest = at
            }
            applied += store.applyRemote(rows, myDevice, fb.uid.orEmpty()).applied
            newest?.let { store.setCursor("${it.seconds}:${it.nanoseconds}") }
            last = snap.documents.last()
            if (snap.size() < PAGE) break
        }
        return applied
    }

    private fun parseCursor(text: String?): Timestamp? {
        val parts = text?.split(":") ?: return null
        val s = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val n = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return Timestamp(s, n)
    }

    private fun parse(d: DocumentSnapshot): WireRow? {
        val table = d.getString("t") ?: return null
        val rowId = d.getString("r") ?: return null
        val at = d.getLong("at") ?: return null
        val values = (d.get("d") as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to normalise(v) } ?: emptyMap()
        return WireRow(table, rowId, at, d.getString("dev").orEmpty(), d.getBoolean("del") == true, (d.getLong("v") ?: 0L).toInt(), values)
    }

    private fun normalise(v: Any?): Any? = when (v) {
        null -> null
        is Long, is Double, is String -> v
        is Int -> v.toLong()
        is Boolean -> if (v) 1L else 0L
        else -> v.toString()
    }

    // ---- push

    private fun doc(table: String, rowId: String, at: Long, device: String, deleted: Boolean, values: Map<String, Any?>?): HashMap<String, Any?> {
        val data = hashMapOf<String, Any?>(
            "t" to table, "r" to rowId, "at" to at, "dev" to device, "del" to deleted,
            "v" to ChittaDatabase.VERSION.toLong(), "srv" to FieldValue.serverTimestamp(),
        )
        if (deleted) data["delAt"] = at else data["d"] = values
        return data
    }

    private suspend fun push(uid: String, myDevice: String): Int {
        var sent = 0
        val me = fb.uid.orEmpty()
        while (true) {
            val changes = store.pending(PAGE)
            if (changes.isEmpty()) break
            val batch = fb.db.batch()
            val going = ArrayList<com.mansaketh.chitta.core.database.SyncChangeEntity>()
            for (ch in changes) {
                if (SyncTables.byName(ch.tbl) == null) { store.markSent(ch); continue }
                val data: HashMap<String, Any?>
                if (ch.deleted) {
                    if (!SyncRows.syncsRow(ch.tbl, ch.rowId, null, me)) { store.markSent(ch); continue }
                    data = doc(ch.tbl, ch.rowId, ch.atMs, myDevice, true, null)
                } else {
                    val row = store.readRow(ch.tbl, ch.rowId)
                    if (row == null) {
                        data = doc(ch.tbl, ch.rowId, ch.atMs, myDevice, true, null) // the row is gone: the marker is what to say
                    } else {
                        if (!SyncRows.syncsRow(ch.tbl, ch.rowId, row["ownerUid"] as? String, me)) { store.markSent(ch); continue }
                        data = doc(ch.tbl, ch.rowId, ch.atMs, myDevice, false, row)
                    }
                }
                batch.set(col(uid).document(SyncRows.docId(ch.tbl, ch.rowId)), data)
                going.add(ch)
            }
            if (going.isNotEmpty()) {
                withTimeout(SERVER_TIMEOUT_MS) { batch.commit().await() }
                // A change that was edited again while it was on its way stays waiting and goes up in the next lap.
                going.forEach { store.markSent(it) }
                sent += going.size
            }
        }
        return sent
    }

    /** One small write and read of the server's time. Fails (and so stops the push) when the phone is offline; false when the clock is too far off. */
    private suspend fun clockIsRight(uid: String): Boolean {
        val ref = fb.db.document("users/$uid/syncMeta/clock")
        val before = clock.nowMs()
        withTimeout(SERVER_TIMEOUT_MS) { ref.set(mapOf("t" to FieldValue.serverTimestamp())).await() }
        val snap = withTimeout(SERVER_TIMEOUT_MS) { ref.get(Source.SERVER).await() }
        val after = clock.nowMs()
        val serverMs = snap.getTimestamp("t")?.toDate()?.time ?: return true
        return !SyncMerge.skewTooBig((before + after) / 2, serverMs)
    }

    // ---- housekeeping

    /** Delete markers older than 180 days are cleared out of the cloud, at most once a day. A phone that stayed away longer is asked what to do. */
    private suspend fun cleanOldMarkersIfDue(uid: String) {
        val now = clock.nowMs()
        if (now - prefs.lastCleanMs < CLEAN_EVERY_MS) return
        val cutoff = now - SyncMerge.TOMBSTONE_DAYS * 86_400_000L
        while (true) {
            val snap = withTimeout(SERVER_TIMEOUT_MS) { col(uid).whereLessThan("delAt", cutoff).limit(PAGE.toLong()).get(Source.SERVER).await() }
            if (snap.isEmpty) break
            val batch = fb.db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            withTimeout(SERVER_TIMEOUT_MS) { batch.commit().await() }
            if (snap.size() < PAGE) break
        }
        prefs.lastCleanMs = now
    }

    // ---- after the whole book was replaced here

    /**
     * A restore or "Delete everything" made this phone's book the truth. This phone starts over as a new device, every row it has is marked
     * as changed now (so it beats the cloud's older copies), and every cloud row this phone does not have gets a delete marker.
     * Without this the other phones' rows would simply come back at the next pull.
     */
    private suspend fun cloudFollowsThisPhone(uid: String) {
        val device = store.startOver()
        store.markEverythingChanged(clock.nowMs())
        if (!clockIsRight(uid)) throw ClockSkewException()
        val now = clock.nowMs()
        val local = HashMap<String, Set<String>>()
        var last: DocumentSnapshot? = null
        while (true) {
            var q = col(uid).orderBy(FieldPath.documentId()).limit(PAGE.toLong())
            last?.let { q = q.startAfter(it) }
            val snap = withTimeout(SERVER_TIMEOUT_MS) { q.get(Source.SERVER).await() }
            if (snap.isEmpty) break
            val batch = fb.db.batch()
            var n = 0
            for (d in snap.documents) {
                val (table, rowId) = SyncRows.parseDocId(d.id) ?: continue
                if (d.getBoolean("del") == true || SyncTables.byName(table) == null) continue
                if (rowId in local.getOrPut(table) { store.localRowIds(table) }) continue
                batch.set(d.reference, doc(table, rowId, now, device, true, null))
                n++
            }
            if (n > 0) withTimeout(SERVER_TIMEOUT_MS) { batch.commit().await() }
            last = snap.documents.last()
            if (snap.size() < PAGE) break
        }
        prefs.bookReplaced = false
    }

    // ---- a phone that stayed away more than 180 days

    /** The person's answer: [merge] keeps both sides (this phone's rows the cloud lacks go up); otherwise this phone becomes a copy of the cloud. */
    suspend fun resolveStale(merge: Boolean): SyncResult {
        if (merge) {
            store.markMissingAtZero()
        } else {
            if (restore.create(RestoreKind.BeforeRestore) == null) return SyncResult.Failed("Could not make a restore point first, so nothing was changed.", false)
            store.wipeOwnRows(fb.uid.orEmpty())
        }
        store.clearCursor()
        store.setLastSyncMs(clock.nowMs())
        prefs.setNeedsChoice(false)
        return run("choice")
    }
}
