package com.mansaketh.chitta.cloud

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.mansaketh.chitta.core.database.HouseholdStore
import com.mansaketh.chitta.core.domain.HouseholdSharing
import com.mansaketh.chitta.core.domain.HouseholdWire
import com.mansaketh.chitta.core.domain.PhoneHolds
import com.mansaketh.chitta.core.domain.ServerHolds
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.diagnostics.Diagnostics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Entry sharing for the household (Phase 9b, step 3): the entries this person shares go up to Firestore, and the other members'
 * shared entries and the shared categories come down. It reads and writes the shapes of the old Flutter app (see [HouseholdWire]) and
 * the published `firestore.rules` unchanged:
 *
 *   households/{h}/transactions/{id}   one document per shared entry (ownerId, isPrivate and the entry fields)
 *   households/{h}/categories/{id}     shared categories
 *
 * How it behaves (DECISIONS D212 to D222):
 *  - Nothing goes up or down until the person has chosen what to share (PDF p.33).
 *  - The upload queue is the entries themselves: a shared entry whose `householdSyncedAtMs` is empty is waiting. A local edit empties it,
 *    so nothing is lost while the phone is offline; it goes up on the next round. Rounds run 3 seconds after a change and every 3 minutes.
 *  - A sent entry is marked uploaded only if it did not change while it was on its way.
 *  - What arrives is never sent back, and my own entries are never deleted from here (only another member's can be removed).
 *  - After every full server snapshot the phone compares what it holds with what the server returned and corrects both sides.
 *  - Another member's entries sit in one placeholder account per member, which is never listed as an account.
 *  - Every server step has a time limit: a Firestore write made without a connection waits for ever instead of failing.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Singleton
class HouseholdSync @Inject constructor(
    private val fb: FirebaseSession,
    private val household: HouseholdPrefs,
    private val sharing: HouseholdSharingPrefs,
    private val service: HouseholdService,
    private val store: HouseholdStore,
    private val clock: Clock,
) {
    private companion object {
        const val SERVER_TIMEOUT_MS = 25_000L
        const val FIRST_SNAPSHOT_WAIT_MS = 10_000L
        const val RETRY_MS = 2 * 60_000L
        const val ROUND_EVERY_MS = 3 * 60_000L
        const val QUIET_MS = 3_000L
        const val BATCH = 300
        const val OFFLINE = "Could not reach the server."
        const val NOT_SIGNED_IN = "Sign in with Google to keep sharing."
        const val FALLBACK_NAME = "Member"
    }

    private val uploadLock = Mutex()
    private val applyLock = Mutex()
    @Volatile private var memberNames: Map<String, String> = emptyMap()
    @Volatile private var publicIds: Set<String>? = null
    @Volatile private var privateIds: Set<String>? = null

    // ---- start / stop

    /** Called once from the Application. Follows the household and the sharing choice for as long as the app runs. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(household.state, sharing.state) { h, s -> if (h.inHousehold && s.ready) h.id else "" }
                .distinctUntilChanged()
                .collectLatest { hid -> if (hid.isEmpty()) onNotSharing() else runSharing(hid) }
        }
    }

    /** Left, removed or signed out while sharing was on: what came from members goes, what I shared stays as personal entries. */
    private suspend fun onNotSharing() {
        if (household.state.value.inHousehold || !sharing.state.value.ready) return
        applyLock.withLock {
            runCatching {
                store.removeMembersData(fb.uid.orEmpty())
                store.makeAllPersonal()
            }.onFailure { Diagnostics.record("Household: leaving", it) }
        }
        sharing.reset()
    }

    private suspend fun runSharing(hid: String) = coroutineScope {
        val uid = fb.uid
        if (uid == null) {
            sharing.problem(NOT_SIGNED_IN)
            return@coroutineScope
        }
        publicIds = null
        privateIds = null
        val rounds = Channel<Unit>(Channel.CONFLATED)
        launch {
            merge(store.observeWaiting(uid).map { }, store.observeDeleteCount().map { }, store.observeCategories().map { })
                .debounce(QUIET_MS).collect { rounds.trySend(Unit) }
        }
        launch { while (true) { delay(ROUND_EVERY_MS); rounds.trySend(Unit) } }
        launch { download(hid, uid) }
        rounds.trySend(Unit)
        for (ignored in rounds) uploadRound(hid, uid)
    }

    // ---- what the screens call

    /** The person's choice on PDF p.33. Entries already on the phone are shared or kept personal; from now on new entries are shared. */
    suspend fun adopt(choice: HouseholdSharing.ShareChoice): String? {
        val uid = fb.uid ?: return NOT_SIGNED_IN
        if (!household.state.value.inHousehold) return "Join or create a household first."
        return try {
            when (choice) {
                HouseholdSharing.ShareChoice.SHARE_EXISTING -> store.shareExisting(uid)
                HouseholdSharing.ShareChoice.ONLY_NEW -> store.keepExistingPersonal()
            }
            sharing.markReady(clock.nowMs())
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Diagnostics.record("Household: choosing what to share", e)
            "Something went wrong. ${e.message.orEmpty()}".trim()
        }
    }

    /** Later, from the Household page: share the entries that were kept personal when joining. Entries already shared are not touched. */
    suspend fun shareEarlierEntries(): String? {
        val uid = fb.uid ?: return NOT_SIGNED_IN
        if (!household.state.value.inHousehold || !sharing.state.value.ready) return "Choose what to share first."
        return try {
            store.shareExisting(uid)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Diagnostics.record("Household: sharing earlier entries", e)
            "Something went wrong. ${e.message.orEmpty()}".trim()
        }
    }

    /** "Send now": one upload round. Returns an error in words, or null when it worked. */
    suspend fun syncNow(): String? {
        val uid = fb.uid ?: return NOT_SIGNED_IN
        if (!household.state.value.inHousehold || !sharing.state.value.ready) return null
        return uploadRound(household.state.value.id, uid)
    }

    suspend fun counts(): HouseholdStore.Counts? = fb.uid?.let { runCatching { store.counts(it) }.getOrNull() }

    /** What this phone holds, for the diagnostics view. Null when nobody is signed in. */
    suspend fun phoneHolds(): PhoneHolds? = fb.uid?.let { runCatching { store.phoneHolds(it) }.getOrNull() }

    /**
     * What the server holds, counted by the server itself (not from the phone's cache): entries not marked private, my own Mine-only entries,
     * and members. Equality-only queries like the ones the sync listens to, so the published rules already allow them. Returns the counts or a
     * short reason it could not be read.
     */
    suspend fun serverHolds(): HResult<ServerHolds> {
        val uid = fb.uid ?: return HResult.Fail(NOT_SIGNED_IN)
        val hid = household.state.value.id.takeIf { household.state.value.inHousehold } ?: return HResult.Fail("Join or create a household first.")
        return try {
            withTimeout(SERVER_TIMEOUT_MS) {
                val doc = fb.db.document("households/$hid")
                val entries = doc.collection("transactions")
                val shared = async { entries.whereEqualTo("isPrivate", false).count().get(AggregateSource.SERVER).await().count }
                val mine = async { entries.whereEqualTo("ownerId", uid).whereEqualTo("isPrivate", true).count().get(AggregateSource.SERVER).await().count }
                val members = async { doc.collection("members").count().get(AggregateSource.SERVER).await().count }
                HResult.Ok(ServerHolds(shared.await().toInt(), mine.await().toInt(), members.await().toInt()))
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) HResult.Fail(OFFLINE) else throw e
        } catch (e: Exception) {
            Diagnostics.record("household diagnostics", e)
            HResult.Fail(if (e is FirebaseFirestoreException && e.code == Code.PERMISSION_DENIED) "The server refused the count." else OFFLINE)
        }
    }

    // ---- upload

    private suspend fun uploadRound(hid: String, uid: String): String? = uploadLock.withLock {
        try {
            val db = fb.db
            // Is the server reachable? Without this, writes would wait in Firestore's own queue while we think they are on their way.
            withTimeout(SERVER_TIMEOUT_MS) { db.document("households/$hid").get(Source.SERVER).await() }
            store.claimNew(uid)
            uploadCategories(db, hid)
            uploadEntries(db, hid, uid)
            uploadDeletions(db, hid, uid)
            sharing.synced(clock.nowMs())
            null
        } catch (e: TimeoutCancellationException) {
            sharing.problem(OFFLINE)
            OFFLINE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val text = friendly(e)
            Diagnostics.record("Household upload", e)
            sharing.problem(text)
            text
        }
    }

    private fun friendly(e: Throwable): String = when {
        e is FirebaseFirestoreException && e.code == Code.PERMISSION_DENIED -> "The household did not allow that. Open the Household page to check you are still in it."
        e is FirebaseFirestoreException && (e.code == Code.UNAVAILABLE || e.code == Code.DEADLINE_EXCEEDED) -> OFFLINE
        else -> "Sync problem. ${e.message.orEmpty()}".trim()
    }

    private suspend fun uploadEntries(db: FirebaseFirestore, hid: String, uid: String) {
        repeat(100) {
            val waiting = store.waiting(uid, BATCH)
            if (waiting.isEmpty()) return
            // An entry I edited on another phone of mine meanwhile may have a later edit on the server. That one is not overwritten: it
            // comes down through the listener, which takes the newer edit.
            val newerOnServer = rowsEditedLaterOnServer(db, hid, waiting)
            val send = waiting.filter { it.id !in newerOnServer }
            if (send.isNotEmpty()) {
                val batch = db.batch()
                for (t in send) batch.set(db.document("households/$hid/transactions/${t.id}"), HouseholdWire.entryToDocument(t, uid))
                withTimeout(SERVER_TIMEOUT_MS) { batch.commit().await() }
            }
            val at = clock.nowMs()
            var marked = 0
            for (t in waiting) if (store.markSynced(t, at)) marked++
            if (marked == 0) return // everything changed meanwhile: the next round sends the new versions
        }
    }

    /**
     * Which of [waiting] have a later edit on the server. Only entries that were edited are looked up (a brand-new entry has no
     * server copy); one document at a time, so only the rule for reading one's own document applies. A failed lookup means "upload as usual".
     */
    private suspend fun rowsEditedLaterOnServer(db: FirebaseFirestore, hid: String, waiting: List<Transaction>): Set<String> {
        val edited = waiting.filter { it.modifiedAtMs != null && it.modifiedAtMs != it.dateMs }
        val newer = mutableSetOf<String>()
        for (chunk in edited.chunked(20)) {
            coroutineScope {
                chunk.map { t ->
                    async {
                        runCatching {
                            val doc = withTimeout(SERVER_TIMEOUT_MS) { db.document("households/$hid/transactions/${t.id}").get(Source.SERVER).await() }
                            val data = doc.data ?: return@runCatching
                            val remote = HouseholdWire.entryFromDocument(data)
                            if (remote != null && HouseholdSharing.remoteIsNewer(remote.modifiedAtMs, t.modifiedAtMs)) synchronized(newer) { newer += t.id }
                        }.onFailure { if (it is CancellationException && it !is TimeoutCancellationException) throw it }
                    }
                }.awaitAll()
            }
        }
        return newer
    }

    private suspend fun uploadCategories(db: FirebaseFirestore, hid: String) {
        val local = store.categories()
        val hash = HouseholdWire.categoriesFingerprint(local)
        if (hash == sharing.state.value.categoriesHash) return
        // Only what is new or edited here goes up; a category the household changed later is left to come down.
        val server = withTimeout(SERVER_TIMEOUT_MS) { db.collection("households/$hid/categories").get(Source.SERVER).await() }
            .documents.mapNotNull { d -> d.data?.let { HouseholdWire.categoryFromDocument(it) } }.associateBy { it.id }
        val toSend = local.filter { c ->
            val remote = server[c.id]
            remote == null || (!HouseholdWire.sameCategoryContent(remote, c) && HouseholdSharing.remoteIsNewer(c.modifiedAtMs, remote.modifiedAtMs))
        }
        for (chunk in toSend.chunked(BATCH)) {
            val batch = db.batch()
            for (c in chunk) batch.set(db.document("households/$hid/categories/${c.id}"), HouseholdWire.categoryToDocument(c))
            withTimeout(SERVER_TIMEOUT_MS) { batch.commit().await() }
        }
        sharing.setCategoriesHash(hash)
    }

    /** Deletions made here after sharing started, taken from the entry history. Only my own documents are deleted. */
    private suspend fun uploadDeletions(db: FirebaseFirestore, hid: String, uid: String) {
        val cursor = sharing.state.value.deleteCursorMs
        val marks = store.deletedSince(cursor)
        if (marks.isEmpty()) return
        var newCursor = cursor
        for (m in marks) {
            try {
                val ref = db.document("households/$hid/transactions/${m.entryId}")
                // Asking first matters: a delete the rules refuse would still show on this phone for a moment.
                val existing = withTimeout(SERVER_TIMEOUT_MS) { ref.get(Source.SERVER).await() }
                if (existing.exists() && existing.getString("ownerId") == uid) withTimeout(SERVER_TIMEOUT_MS) { ref.delete().await() }
            } catch (e: FirebaseFirestoreException) {
                // Never uploaded, or not mine to delete: nothing to do.
                if (e.code != Code.PERMISSION_DENIED && e.code != Code.NOT_FOUND) throw e
            }
            newCursor = maxOf(newCursor, m.atMs)
        }
        if (newCursor != cursor) sharing.setDeleteCursor(newCursor)
    }

    // ---- download

    private fun Query.snapshots(withMetadata: Boolean = false): Flow<QuerySnapshot> = callbackFlow {
        val registration = addSnapshotListener(if (withMetadata) MetadataChanges.INCLUDE else MetadataChanges.EXCLUDE) { snap, error ->
            if (error != null) close(error) else if (snap != null) trySend(snap)
        }
        awaitClose { registration.remove() }
    }

    private fun DocumentReference.snapshots(): Flow<DocumentSnapshot> = callbackFlow {
        val registration = addSnapshotListener { snap, error -> if (error != null) close(error) else if (snap != null) trySend(snap) }
        awaitClose { registration.remove() }
    }

    /** Keeps one listener alive: after an error it waits and starts again; a refused read means this person may have been removed. */
    private suspend fun watch(name: String, block: suspend () -> Unit) {
        while (true) {
            try {
                block()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Diagnostics.record("Household $name", e)
                if (e is FirebaseFirestoreException && e.code == Code.PERMISSION_DENIED) {
                    service.refresh() // forgets the household here when this person was removed
                    if (!household.state.value.inHousehold) return
                }
                delay(RETRY_MS)
            }
        }
    }

    private suspend fun download(hid: String, uid: String) = coroutineScope {
        val db = fb.db
        val householdDoc = db.document("households/$hid")

        launch { watch("household") { householdDoc.snapshots().collect { snap -> applyLock.withLock { applyHouseholdDocument(hid, snap) } } } }

        // Members and categories first, so placeholder accounts have names and entries have their categories.
        val membersFirst = CompletableDeferred<Unit>()
        launch {
            watch("members") {
                householdDoc.collection("members").snapshots().collect { snap ->
                    applyLock.withLock { applyMembers(snap, uid) }
                    membersFirst.complete(Unit)
                }
            }
        }
        withTimeoutOrNull(FIRST_SNAPSHOT_WAIT_MS) { membersFirst.await() }

        val categoriesFirst = CompletableDeferred<Unit>()
        launch {
            watch("categories") {
                householdDoc.collection("categories").snapshots().collect { snap ->
                    applyLock.withLock { applyCategories(snap) }
                    categoriesFirst.complete(Unit)
                }
            }
        }
        withTimeoutOrNull(FIRST_SNAPSHOT_WAIT_MS) { categoriesFirst.await() }

        val entries = householdDoc.collection("transactions")
        // Equality-only queries, so no composite index is needed.
        launch {
            watch("shared entries") {
                entries.whereEqualTo("isPrivate", false).snapshots(withMetadata = true).collect { snap ->
                    applyLock.withLock { applyEntries(snap, uid, isPublicQuery = true) }
                }
            }
        }
        launch {
            watch("private entries") {
                entries.whereEqualTo("ownerId", uid).whereEqualTo("isPrivate", true).snapshots(withMetadata = true).collect { snap ->
                    applyLock.withLock { applyEntries(snap, uid, isPublicQuery = false) }
                }
            }
        }
    }

    private fun applyHouseholdDocument(hid: String, snap: DocumentSnapshot) {
        if (!snap.exists()) return
        val now = household.state.value
        if (now.id != hid) return
        val show = snap.getBoolean("showBalances") == true
        val name = snap.getString("name") ?: now.name
        if (show != now.showBalances || name != now.name) household.save(now.id, name, now.isOwner, now.nickname, show)
    }

    private suspend fun applyMembers(snap: QuerySnapshot, myUid: String) {
        val names = mutableMapOf<String, String>()
        for (d in snap.documents) {
            names[d.id] = d.getString("displayName").orEmpty().trim()
            if (d.id == myUid) {
                sharing.setCanDelete(d.getBoolean("canDelete") == true)
                // Ownership can change while the app is open (hand-over): follow it right away.
                val isOwner = d.getString("role") == "owner"
                val now = household.state.value
                if (now.inHousehold && now.isOwner != isOwner) household.save(now.id, now.name, isOwner, now.nickname, now.showBalances)
            }
        }
        memberNames = names
        val others = names.filterKeys { it != myUid }.mapValues { (_, n) -> n.ifEmpty { FALLBACK_NAME }.take(40) }
        store.ensurePlaceholders(others, store.ownCurrency(null), clock.nowMs())
    }

    private suspend fun applyCategories(snap: QuerySnapshot) {
        val local = store.categories()
        val byId = local.associateBy { it.id }
        // If everything here had already been uploaded, what arrives is not a new local edit, so the stored fingerprint follows along.
        val wasInSync = HouseholdWire.categoriesFingerprint(local) == sharing.state.value.categoriesHash
        val writes = mutableListOf<com.mansaketh.chitta.core.model.Category>()
        for (change in snap.documentChanges) {
            if (change.type == DocumentChange.Type.REMOVED) continue // categories are never removed remotely
            val remote = change.document.data.let { HouseholdWire.categoryFromDocument(it) } ?: continue
            val mine = byId[remote.id]
            if (mine != null && (HouseholdWire.sameCategoryContent(mine, remote) || !HouseholdSharing.remoteIsNewer(remote.modifiedAtMs, mine.modifiedAtMs))) continue
            writes += remote.copy(methodAdded = mine?.methodAdded ?: remote.methodAdded)
        }
        if (writes.isEmpty()) return
        store.saveCategories(writes)
        if (wasInSync) sharing.setCategoriesHash(HouseholdWire.categoriesFingerprint(store.categories()))
    }

    private suspend fun applyEntries(snap: QuerySnapshot, myUid: String, isPublicQuery: Boolean) {
        val changes = snap.documentChanges
        for (chunk in changes.chunked(BATCH)) applyChunk(chunk, myUid)
        // Only a snapshot that came from the server says what is really there.
        if (snap.metadata.isFromCache) return
        val ids = snap.documents.map { it.id }.toSet()
        if (isPublicQuery) publicIds = ids else privateIds = ids
        val a = publicIds ?: return
        val b = privateIds ?: return
        reconcile(myUid, a + b)
    }

    private suspend fun applyChunk(chunk: List<DocumentChange>, myUid: String) {
        val local = store.get(chunk.map { it.document.id })
        val deletedHere = store.deletedIds()
        val ownAccounts = store.ownAccountIds()
        val fallbackAccount = ownAccounts.firstOrNull()
        val member = mutableListOf<Transaction>()
        val own = mutableListOf<Transaction>()
        val remove = mutableListOf<String>()
        val neededPlaceholders = mutableSetOf<String>()
        var skipped = 0

        for (change in chunk) {
            val id = change.document.id
            val mine = local[id]
            if (change.type == DocumentChange.Type.REMOVED) {
                // Deleted by someone, or made private by its owner: only other members' rows go.
                if (mine != null && HouseholdSharing.isForeignOwner(mine.ownerUid, myUid)) remove += id
                continue
            }
            val data = change.document.data
            val ownerId = (data["ownerId"] as? String).orEmpty()
            if (ownerId.isEmpty()) continue
            val remote = HouseholdWire.entryFromDocument(data)
            if (remote == null) { skipped++; continue }

            if (ownerId == myUid) {
                if (id in deletedHere) continue
                val accountHere = if (remote.accountId in ownAccounts) remote.accountId else fallbackAccount
                when {
                    mine == null -> if (accountHere != null) own += HouseholdWire.restoreOwnRow(remote, myUid, accountHere)
                    // The server says this row is mine but it sits here as somebody else's: the server decides.
                    HouseholdSharing.isForeignOwner(mine.ownerUid, myUid) -> if (accountHere != null) own += HouseholdWire.restoreOwnRow(remote, myUid, accountHere)
                    // I edited it on another phone and that edit is newer than mine: take it. A newer or equal edit made here goes up next.
                    HouseholdSharing.remoteIsNewer(remote.modifiedAtMs, mine.modifiedAtMs) ->
                        own += HouseholdWire.mergeOwnRow(mine, remote, remote.accountId in ownAccounts)
                }
                continue
            }

            // Somebody else's entry.
            if (data["isPrivate"] != false) continue // the rules do not allow this; be safe anyway
            // The server decides who owns a row; otherwise only a newer edit of theirs is taken.
            if (mine != null && !HouseholdSharing.serverOwnerWins(mine.ownerUid, ownerId) && !HouseholdSharing.remoteIsNewer(remote.modifiedAtMs, mine.modifiedAtMs)) continue
            neededPlaceholders += ownerId
            member += HouseholdWire.asMemberEntry(remote, ownerId)
        }

        if (neededPlaceholders.isNotEmpty()) {
            store.ensurePlaceholders(neededPlaceholders.associateWith { (memberNames[it] ?: "").ifEmpty { FALLBACK_NAME }.take(40) }, store.ownCurrency(null), clock.nowMs())
        }
        if (skipped > 0) Diagnostics.record("Household: skipped $skipped entries that could not be read", null)
        if (member.isNotEmpty() || own.isNotEmpty() || remove.isNotEmpty()) store.apply(member, own, remove, myUid)
    }

    /**
     * Compares everything this phone holds with what the server returned and corrects both sides: other members' rows that are gone
     * from the server are removed here, and my own rows that are missing on the server go up again.
     */
    private suspend fun reconcile(myUid: String, serverIds: Set<String>) {
        val plan = HouseholdSharing.reconcile(store.syncRows(), serverIds, store.deletedIds(), myUid, clock.nowMs())
        if (plan.deleteIds.isNotEmpty()) {
            plan.deleteIds.chunked(BATCH).forEach { store.apply(emptyList(), emptyList(), it, myUid) }
            Diagnostics.record("Household: removed ${plan.deleteIds.size} entries that are no longer in the household", null)
        }
        val again = (plan.reuploadIds + plan.undeleteIds).distinct()
        if (again.isNotEmpty()) {
            store.markDirty(again) // the upload queue picks them up
            Diagnostics.record("Household: sending ${again.size} of my entries again (missing on the server)", null)
        }
    }
}
