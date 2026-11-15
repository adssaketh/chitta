package com.mansaketh.chitta.cloud

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.mansaketh.chitta.core.database.AssetsRepository
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.database.PlanRepository
import com.mansaketh.chitta.core.domain.Instalments
import com.mansaketh.chitta.core.domain.SharedItems
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.diagnostics.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Loans and fixed deposits shared inside the household (Phase 9b, step 5a). Reads and writes the old Flutter app's documents and the
 * published `firestore.rules` unchanged:
 *
 *   households/{h}/sharedItems/{kind-ownerUid-localId}   kind, ownerUid, localId, visibleTo, membersCanEdit, _u, payload
 *
 * How it behaves (DECISIONS D224 to D229):
 *  - Private by default. The owner chooses who sees an item (up to 7 others) and whether they may edit it; an empty choice makes it private
 *    again and the server copy is removed on the next round.
 *  - The upload queue is each item's newest change (the item, or the choice) against what was last sent from this phone. Offline, nothing is lost.
 *  - A loan's "paid so far" is worked out here, on the owner's phone, because the payments recorded as entries are not on the viewers' phones.
 *  - Two queries only, the ones the rules allow: `ownerUid == me` (read my choices back after a reinstall) and `visibleTo array-contains me`.
 *  - A viewer's copy is shown in its own list and is never part of Net worth, loan totals or reminders.
 *  - Step 5b adds members' edits. Until then a later edit found on the server (made in the old app) is not overwritten.
 *  - Needs only the household and the Google sign-in; it does not wait for the entry-sharing choice.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Singleton
class HouseholdSharedItems @Inject constructor(
    private val fb: FirebaseSession,
    private val household: HouseholdPrefs,
    private val service: HouseholdService,
    private val prefs: SharedItemsPrefs,
    private val assets: AssetsRepository,
    private val plan: PlanRepository,
    private val ledger: LedgerRepository,
    private val clock: Clock,
) {
    private companion object {
        const val SERVER_TIMEOUT_MS = 25_000L
        const val RETRY_MS = 2 * 60_000L
        const val ROUND_EVERY_MS = 3 * 60_000L
        const val QUIET_MS = 3_000L
        const val OFFLINE = "Could not reach the server."
        const val NOT_SIGNED_IN = "Sign in with Google to share."
        const val FALLBACK_NAME = "Member"
    }

    val state get() = prefs.state

    /** For the screens: whether this person is in a household, and who they are on the server. */
    val householdState get() = household.state
    val myUid: String? get() = fb.uid

    private val uploadLock = Mutex()
    private val kick = Channel<Unit>(Channel.CONFLATED)

    /** Called once from the Application. Follows the household for as long as the app runs. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            household.state.map { if (it.inHousehold) it.id else "" }.distinctUntilChanged().collectLatest { hid ->
                if (hid.isEmpty()) prefs.reset() else runSharing(hid)
            }
        }
    }

    private suspend fun runSharing(hid: String) = coroutineScope {
        val uid = fb.uid
        if (uid == null) { prefs.problem(NOT_SIGNED_IN); return@coroutineScope }
        launch {
            merge(
                assets.observeDeposits().map { }, plan.observeLoanTerms().map { }, plan.observeObjectives().map { },
                prefs.state.map { it.own to it.removals }.distinctUntilChanged().map { },
            ).debounce(QUIET_MS).collect { kick.trySend(Unit) }
        }
        launch { while (true) { delay(ROUND_EVERY_MS); kick.trySend(Unit) } }
        launch { download(hid, uid) }
        kick.trySend(Unit)
        for (ignored in kick) uploadRound(hid, uid)
    }

    // ---- what the screens call

    /**
     * Sets who sees one of my items and whether they may edit it. An empty [visibleTo] makes the item private again. Returns an error in
     * words, or null when it worked. Nothing is sent from here: the choice is saved and the next round sends it.
     */
    suspend fun setSharing(kind: String, localId: String, visibleTo: List<String>, membersCanEdit: Boolean): String? {
        val uid = fb.uid ?: return NOT_SIGNED_IN
        if (!household.state.value.inHousehold) return "Join or create a household first."
        if (kind !in SharedItems.KINDS || localId.isEmpty()) return "Unknown item."
        val docId = SharedItems.docId(kind, uid, localId)
        if (visibleTo.isEmpty()) {
            if (docId in prefs.state.value.own) prefs.dropOwn(docId)
            kick.trySend(Unit)
            return null
        }
        val members = prefs.state.value.members.keys
        if (members.isEmpty()) return "Could not read who is in the household. Try again when you are online."
        val clean = SharedItems.cleanVisibleTo(uid, visibleTo, members + uid)
        if (clean.isEmpty()) return "Pick people from the household."
        prefs.setOwn(SharedItems.Meta(kind, uid, localId, clean, membersCanEdit, clock.nowMs()))
        kick.trySend(Unit)
        return null
    }

    /** "Send now": one round. Returns an error in words, or null when it worked. */
    suspend fun sendNow(): String? {
        val uid = fb.uid ?: return NOT_SIGNED_IN
        if (!household.state.value.inHousehold) return null
        return uploadRound(household.state.value.id, uid)
    }

    // ---- reading the real item

    private data class Local(val payload: Map<String, Any?>, val updatedAtMs: Long, val paidPaise: Long?)

    private suspend fun localItem(kind: String, localId: String): Local? {
        val zone = clock.zone()
        if (kind == SharedItems.KIND_FD) {
            val fd = assets.deposits().firstOrNull { it.id == localId } ?: return null
            return Local(SharedItems.depositPayload(fd, zone), fd.updatedAtMs ?: 0L, null)
        }
        val o = plan.objective(localId) ?: return null
        val t = plan.observeLoanTerms().first().firstOrNull { it.objectiveId == localId } ?: return null // only instalment loans can be shared
        val linked = ledger.observeAllOnce().filter { it.objectiveLoanId == localId }.sumOf { it.amount }
        val paid = Instalments.progress(t, linked, zone).repaid.value
        return Local(SharedItems.loanPayload(o, t, paid, zone), maxOf(o.modifiedAtMs ?: 0L, t.updatedAtMs ?: 0L), paid)
    }

    // ---- upload

    private suspend fun uploadRound(hid: String, uid: String): String? = uploadLock.withLock {
        try {
            val db = fb.db
            // Is the server reachable? Without this, writes would wait in Firestore's own queue while we think they are on their way.
            withTimeout(SERVER_TIMEOUT_MS) { db.document("households/$hid").get(Source.SERVER).await() }
            var firstProblem: String? = null
            for ((docId, meta) in prefs.state.value.own) {
                try { sendOne(db.document("households/$hid/sharedItems/$docId"), docId, meta, uid) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    Diagnostics.record("Shared item upload", e)
                    if (firstProblem == null) firstProblem = friendly(e)
                }
            }
            for (docId in prefs.state.value.removals) {
                try {
                    withTimeout(SERVER_TIMEOUT_MS) { db.document("households/$hid/sharedItems/$docId").delete().await() }
                    prefs.removalDone(docId)
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    Diagnostics.record("Shared item removal", e)
                    if (firstProblem == null) firstProblem = friendly(e)
                }
            }
            if (firstProblem == null) prefs.synced(clock.nowMs()) else prefs.problem(firstProblem)
            firstProblem
        } catch (e: TimeoutCancellationException) {
            prefs.problem(OFFLINE); OFFLINE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Diagnostics.record("Shared items round", e)
            friendly(e).also { prefs.problem(it) }
        }
    }

    private suspend fun sendOne(ref: DocumentReference, docId: String, choice: SharedItems.Meta, uid: String) {
        val local = localItem(choice.kind, choice.localId)
        val sent = prefs.uploadedAt(docId)
        if (local == null) {
            // Gone here (deleted, or its loan no longer has terms). Remove it for the others only if this phone had sent it: after a
            // reinstall nothing is restored yet, and that must not wipe what others see.
            if (sent != null) { prefs.dropOwn(docId) }
            return
        }
        // The people must still be in the household, or the rules refuse the whole document.
        val members = prefs.state.value.members.keys + uid
        val clean = SharedItems.cleanVisibleTo(uid, choice.visibleTo, members)
        if (prefs.state.value.members.isEmpty()) return
        if (clean.isEmpty()) { prefs.dropOwn(docId); return }
        var meta = choice
        if (clean != choice.visibleTo) { meta = choice.copy(visibleTo = clean, updatedAtMs = clock.nowMs()); prefs.setOwn(meta) }

        val newest = maxOf(local.updatedAtMs, meta.updatedAtMs)
        val paidMoved = local.paidPaise != null && SharedItems.paidChanged(prefs.paidSent(docId), local.paidPaise)
        if (sent != null && newest <= sent && !paidMoved) return

        // Somebody with edit rights (the old app) changed it later than anything here: do not overwrite it. Step 5b applies such edits.
        val server = withTimeout(SERVER_TIMEOUT_MS) { ref.get(Source.SERVER).await() }
        if (server.exists()) {
            val serverU = server.getLong("_u") ?: 0L
            if (serverU > newest && serverU > (sent ?: 0L)) return
        }
        val sendU = if (paidMoved) maxOf(newest, clock.nowMs()) else newest
        val doc = SharedItems.metaToFields(meta.copy(updatedAtMs = sendU)) + ("payload" to local.payload)
        withTimeout(SERVER_TIMEOUT_MS) { ref.set(doc).await() }
        prefs.sent(docId, sendU, local.paidPaise)
    }

    private fun friendly(e: Throwable): String = when {
        e is FirebaseFirestoreException && e.code == Code.PERMISSION_DENIED -> "The household did not allow that. Open the Household page to check you are still in it."
        e is FirebaseFirestoreException && (e.code == Code.UNAVAILABLE || e.code == Code.DEADLINE_EXCEEDED) -> OFFLINE
        else -> "Sync problem. ${e.message.orEmpty()}".trim()
    }

    // ---- download

    private fun Query.snapshots(): Flow<QuerySnapshot> = callbackFlow {
        val registration = addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snap, error ->
            if (error != null) close(error) else if (snap != null) trySend(snap)
        }
        awaitClose { registration.remove() }
    }

    /** Keeps one listener alive: after an error it waits and starts again; a refused read means this person may have been removed. */
    private suspend fun watch(name: String, block: suspend () -> Unit) {
        while (true) {
            try { block(); return } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Diagnostics.record("Shared items $name", e)
                if (e is FirebaseFirestoreException && e.code == Code.PERMISSION_DENIED) {
                    service.refresh()
                    if (!household.state.value.inHousehold) return
                }
                delay(RETRY_MS)
            }
        }
    }

    private suspend fun download(hid: String, uid: String) = coroutineScope {
        val doc = fb.db.document("households/$hid")
        launch {
            watch("members") {
                doc.collection("members").snapshots().collect { snap ->
                    prefs.setMembers(snap.documents.associate { it.id to it.getString("displayName").orEmpty().trim().ifEmpty { FALLBACK_NAME }.take(40) })
                }
            }
        }
        val items = doc.collection("sharedItems")
        launch { watch("my items") { items.whereEqualTo("ownerUid", uid).snapshots().collect { applyOwn(it) } } }
        launch { watch("shared with me") { items.whereArrayContains("visibleTo", uid).snapshots().collect { applyShared(it, uid) } } }
    }

    /** My own documents: read my choices back (after a reinstall), never the other way round. */
    private fun applyOwn(snap: QuerySnapshot) {
        val st = prefs.state.value
        for (d in snap.documents) {
            val data = d.data ?: continue
            if (!SharedItems.isUsable(d.id, data) || d.id in st.removals) continue
            val meta = SharedItems.metaFromDocument(data) ?: continue
            val mine = st.own[d.id]
            if (mine == null || meta.updatedAtMs > mine.updatedAtMs) prefs.adoptOwn(meta)
        }
    }

    /** What others share with me. A document that is gone or no longer lists me leaves my phone; a later `_u` replaces the copy. */
    private fun applyShared(snap: QuerySnapshot, myUid: String) {
        val held = prefs.state.value.copies.mapValues { it.value.meta.updatedAtMs }
        val incoming = LinkedHashMap<String, SharedCopy>()
        for (d in snap.documents) {
            val data = d.data ?: continue
            val meta = SharedItems.metaFromDocument(data) ?: continue
            if (!SharedItems.isUsable(d.id, data) || meta.ownerUid == myUid || myUid !in meta.visibleTo) continue
            @Suppress("UNCHECKED_CAST")
            val payload = data["payload"] as Map<String, Any?>
            incoming[d.id] = SharedCopy(meta, payload)
        }
        val write = SharedItems.idsToWrite(held, incoming.mapValues { it.value.meta.updatedAtMs })
        prefs.putCopies(write.associateWith { incoming.getValue(it) })
        val gone = snap.documentChanges.filter { it.type == DocumentChange.Type.REMOVED }.map { it.document.id }.toMutableSet()
        // Only a full server answer may say what is missing; the first answer from the cache can be partial.
        if (!snap.metadata.isFromCache) gone += held.keys - incoming.keys
        prefs.removeCopies(gone)
    }
}
