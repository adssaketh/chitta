package com.mansaketh.chitta.cloud

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
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
import java.util.concurrent.ConcurrentHashMap
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
 *  - Step 5b, members' edits (D230 to D235): a member writes only `payload` and `_u` in one transaction, refused when the server copy is newer
 *    than the one they started from. The owner's phone applies a later `_u` to the real loan or deposit (newest wins, the owner's accounts stay).
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
        const val NEWER = "newer copy on the server"
    }

    val state get() = prefs.state

    /** For the screens: whether this person is in a household, and who they are on the server. */
    val householdState get() = household.state
    val myUid: String? get() = fb.uid

    private val uploadLock = Mutex()
    private val kick = Channel<Unit>(Channel.CONFLATED)

    /** Owner side: the newest `_u` the server showed for each of my documents (not counting my own writes still on their way). */
    private val serverSeen = ConcurrentHashMap<String, Long>()

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

    /**
     * A member's edit (step 5b). [payload] is the edited item in the old app's shape; the editor's uid is added here. One transaction writes only
     * `payload` and `_u`, as the rules require, and is refused when the server copy is newer than the one this person started from. It needs the
     * connection: offline nothing is saved and nothing is queued. Returns an error in words, or null when it worked.
     */
    suspend fun editShared(docId: String, payload: Map<String, Any?>): String? {
        val uid = fb.uid ?: return NOT_SIGNED_IN
        val hid = household.state.value.id
        if (!household.state.value.inHousehold || hid.isEmpty()) return "Join or create a household first."
        val copy = prefs.state.value.copies[docId] ?: return "This item is no longer shared with you."
        val members = prefs.state.value.members.keys + uid
        if (!SharedItems.canEdit(copy.meta, uid, members)) return "The owner has not let you edit this."
        val startedFrom = copy.meta.updatedAtMs
        val u = SharedItems.editTime(clock.nowMs(), startedFrom)
        val written = SharedItems.withEditor(payload, uid)
        return try {
            val db = fb.db
            val ref = db.document("households/$hid/sharedItems/$docId")
            withTimeout(SERVER_TIMEOUT_MS) {
                db.runTransaction(Transaction.Function<Boolean> { tx ->
                    val snap = tx.get(ref)
                    if (!snap.exists()) throw FirebaseFirestoreException("gone", Code.NOT_FOUND)
                    if (!SharedItems.editStillValid(snap.getLong("_u") ?: 0L, startedFrom)) throw FirebaseFirestoreException(NEWER, Code.ABORTED)
                    tx.update(ref, mapOf("payload" to written, "_u" to u))
                    true
                }).await()
            }
            prefs.putCopies(mapOf(docId to SharedCopy(copy.meta.copy(updatedAtMs = u), written)))
            prefs.setMyEdit(docId, u)
            null
        } catch (e: TimeoutCancellationException) {
            "Nothing was saved. $OFFLINE"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Diagnostics.record("Shared item edit", e)
            editProblem(e)
        }
    }

    private fun editProblem(e: Throwable): String = when {
        e is FirebaseFirestoreException && e.code == Code.ABORTED && e.message == NEWER -> "Nothing was saved. Somebody changed it first. Look at the new version, then edit again."
        e is FirebaseFirestoreException && e.code == Code.NOT_FOUND -> "Nothing was saved. The owner has taken this item back."
        e is FirebaseFirestoreException && e.code == Code.PERMISSION_DENIED -> "Nothing was saved. The owner no longer lets you edit this."
        e is FirebaseFirestoreException && (e.code == Code.UNAVAILABLE || e.code == Code.DEADLINE_EXCEEDED) -> "Nothing was saved. $OFFLINE"
        else -> "Nothing was saved. ${e.message.orEmpty()}".trim()
    }

    /** Lets the screens say the note about a replaced edit has been read. */
    fun acknowledgeReplaced(docId: String) = prefs.ackReplaced(docId)

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
        val editSeen = SharedItems.memberEditWaiting(serverSeen[docId], newest, sent)
        if (sent != null && newest <= sent && !paidMoved && !editSeen) return

        // Somebody with edit rights changed it later than anything here: the newest `_u` wins, so their edit is applied to the real item.
        val server = withTimeout(SERVER_TIMEOUT_MS) { ref.get(Source.SERVER).await() }
        var refusedEditU: Long? = null
        if (server.exists()) {
            val serverU = server.getLong("_u") ?: 0L
            if (SharedItems.memberEditWaiting(serverU, newest, sent)) {
                if (applyMemberEdit(docId, choice, server, serverU)) return
                refusedEditU = serverU // could not be a real item: the owner's own copy goes back up over it
            }
        }
        val sendU = when {
            refusedEditU != null -> SharedItems.editTime(clock.nowMs(), refusedEditU)
            paidMoved -> maxOf(newest, clock.nowMs())
            else -> newest
        }
        val editor = prefs.state.value.editors[docId]
        val payload = when {
            editor != null && local.updatedAtMs <= editor.atMs && refusedEditU == null -> SharedItems.withEditor(local.payload, editor.uid)
            else -> { if (editor != null) prefs.clearEditor(docId); local.payload }
        }
        val doc = SharedItems.metaToFields(meta.copy(updatedAtMs = sendU)) + ("payload" to payload)
        withTimeout(SERVER_TIMEOUT_MS) { ref.set(doc).await() }
        prefs.sent(docId, sendU, local.paidPaise)
    }

    /**
     * Writes a member's edit into the owner's real deposit or loan (never its accounts). Returns false when the edit could not be a deposit or
     * a loan, so the caller puts the owner's own copy back instead; true when applied, or when the item is not here to apply it to.
     */
    private suspend fun applyMemberEdit(docId: String, choice: SharedItems.Meta, server: DocumentSnapshot, serverU: Long): Boolean {
        @Suppress("UNCHECKED_CAST")
        val payload = server.get("payload") as? Map<String, Any?> ?: return false
        val zone = clock.zone()
        if (choice.kind == SharedItems.KIND_FD) {
            val current = assets.deposits().firstOrNull { it.id == choice.localId } ?: return true
            assets.saveDeposit(SharedItems.applyDepositEdit(current, payload, zone, serverU) ?: return false)
        } else {
            val o = plan.objective(choice.localId) ?: return true
            val t = plan.observeLoanTerms().first().firstOrNull { it.objectiveId == choice.localId } ?: return true
            val (no, nt) = SharedItems.applyLoanEdit(o, t, payload, zone, serverU) ?: return false
            plan.saveObjective(no)
            plan.saveTerms(nt)
        }
        val editor = SharedItems.editorOf(payload)
        if (editor != null) prefs.setEditor(docId, editor, serverU) else prefs.clearEditor(docId)
        prefs.sent(docId, serverU, localItem(choice.kind, choice.localId)?.paidPaise)
        return true
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

    /**
     * My own documents: read my choices back (after a reinstall), never the other way round. A later `_u` that changes nobody's access is a
     * member's edit: it is noted here and a round applies it. It must not be adopted as a choice, or it would look like my own newest change.
     */
    private fun applyOwn(snap: QuerySnapshot) {
        val st = prefs.state.value
        var edited = false
        for (d in snap.documents) {
            val data = d.data ?: continue
            if (!SharedItems.isUsable(d.id, data) || d.id in st.removals) continue
            val meta = SharedItems.metaFromDocument(data) ?: continue
            val mine = st.own[d.id]
            val sameChoice = mine != null && mine.visibleTo == meta.visibleTo && mine.membersCanEdit == meta.membersCanEdit
            if (mine == null || (!sameChoice && meta.updatedAtMs > mine.updatedAtMs)) prefs.adoptOwn(meta)
            if (!d.metadata.hasPendingWrites()) {
                serverSeen[d.id] = meta.updatedAtMs
                if (meta.updatedAtMs > (prefs.uploadedAt(d.id) ?: 0L)) edited = true
            }
        }
        if (edited) kick.trySend(Unit)
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
        for (id in write) {
            val copy = incoming.getValue(id)
            if (SharedItems.editWasReplaced(prefs.myEditU(id), copy.meta.updatedAtMs, SharedItems.editorOf(copy.payload), myUid)) prefs.markReplaced(id)
        }
        prefs.putCopies(write.associateWith { incoming.getValue(it) })
        val gone = snap.documentChanges.filter { it.type == DocumentChange.Type.REMOVED }.map { it.document.id }.toMutableSet()
        // Only a full server answer may say what is missing; the first answer from the cache can be partial.
        if (!snap.metadata.isFromCache) gone += held.keys - incoming.keys
        prefs.removeCopies(gone)
    }
}
