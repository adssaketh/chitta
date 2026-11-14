package com.mansaketh.chitta.cloud

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.mansaketh.chitta.core.domain.HouseholdMember
import com.mansaketh.chitta.core.domain.HouseholdRole
import com.mansaketh.chitta.core.domain.Households
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.asKotlinRandom

data class HouseholdSnapshot(val id: String, val name: String, val ownerId: String, val showBalances: Boolean, val members: List<HouseholdMember>)

sealed interface HResult<out T> {
    data class Ok<T>(val value: T) : HResult<T>
    data class Fail(val message: String) : HResult<Nothing>
}

val <T> HResult<T>.errorOrNull: String? get() = (this as? HResult.Fail)?.message

/** A rule said no, with the words to show. Thrown inside a [HouseholdService] step and turned into [HResult.Fail]. */
private class Refused(message: String) : Exception(message)

/**
 * The household on Firestore (Phase 9b, step 2). It reads and writes exactly the shapes of the old Flutter Paddhu, so a household made
 * there works here and the other way round, and the published `firestore.rules` are used unchanged (see docs/HOUSEHOLD.md in the old repo):
 *
 *   users/{uid}                    householdId, displayName, inviteCode          (this person only)
 *   invites/{code}                 householdId, ownerId                          (read by exact code only)
 *   households/{h}                 ownerId, name, showBalances
 *   households/{h}/members/{uid}   role, canDelete, displayName, inviteCode (on join)
 *
 * Joining, leaving and removing never touch entries on the phone. Pure rules (codes, limit, hand-over) are in `Households`, with tests.
 * Nothing here holds money.
 */
@Singleton
class HouseholdService @Inject constructor(private val fb: FirebaseSession, private val prefs: HouseholdPrefs) {
    private val random = SecureRandom().asKotlinRandom()

    private companion object {
        const val OFFLINE = "Could not reach the server. Check your connection and try again."
        const val NOT_SIGNED_IN = "Sign in with Google first."
        const val REMOVED = "You are no longer in this household."
        /** A Firestore write made without a connection waits in a queue and never answers, so every step has a time limit. */
        const val STEP_TIMEOUT_MS = 30_000L
    }

    private fun friendly(e: Throwable): String = when {
        e is FirebaseFirestoreException && e.code == Code.PERMISSION_DENIED -> "You don't have permission to do that."
        e is FirebaseFirestoreException && (e.code == Code.UNAVAILABLE || e.code == Code.DEADLINE_EXCEEDED) -> OFFLINE
        else -> "Something went wrong. ${e.message.orEmpty()}".trim()
    }

    private fun isDenied(e: Throwable) = e is FirebaseFirestoreException && e.code == Code.PERMISSION_DENIED

    /** Runs one step with a time limit and turns failures into words. Cancellation is passed on, never swallowed. */
    private suspend fun <T> guarded(block: suspend (db: FirebaseFirestore, uid: String) -> T): HResult<T> {
        val uid = fb.uid ?: return HResult.Fail(NOT_SIGNED_IN)
        return try {
            HResult.Ok(withTimeout(STEP_TIMEOUT_MS) { block(fb.db, uid) })
        } catch (e: TimeoutCancellationException) {
            HResult.Fail(OFFLINE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Refused) {
            HResult.Fail(e.message.orEmpty())
        } catch (e: Exception) {
            HResult.Fail(friendly(e))
        }
    }

    private fun local() = prefs.state.value

    private fun remember(id: String, name: String, isOwner: Boolean, nickname: String, showBalances: Boolean = local().showBalances) =
        prefs.save(id, name, isOwner, nickname, showBalances)

    // ---- state

    /**
     * Re-checks this account's household on the server and updates the copy on this phone. Returns an error (and changes nothing) if
     * the server could not be reached. Being removed is noticed here: the phone forgets the household.
     */
    suspend fun refresh(): String? = guarded { db, uid ->
        val hid = db.document("users/$uid").get().await().getString("householdId").orEmpty()
        if (hid.isEmpty()) {
            if (local().inHousehold) prefs.clear()
            return@guarded
        }
        try {
            val member = db.document("households/$hid/members/$uid").get().await()
            // Not a member: the rules refuse the read (below). A member document that is simply missing is the same thing.
            if (!member.exists()) throw FirebaseFirestoreException("not a member", Code.PERMISSION_DENIED)
            val household = db.document("households/$hid").get().await()
            remember(
                hid, household.getString("name").orEmpty(), HouseholdRole.parse(member.getString("role")) == HouseholdRole.Owner,
                member.getString("displayName") ?: Households.defaultNickname(fb.displayName, fb.email), household.getBoolean("showBalances") == true,
            )
        } catch (e: FirebaseFirestoreException) {
            if (e.code != Code.PERMISSION_DENIED) throw e
            // Removed from the household (or it is gone): forget it here, and clear the pointer on the server.
            db.document("users/$uid").set(mapOf("householdId" to FieldValue.delete()), SetOptions.merge()).await()
            prefs.clear()
        }
    }.errorOrNull

    /** The household and its members, read from the server. */
    suspend fun load(): HResult<HouseholdSnapshot> {
        val hid = local().id
        if (hid.isEmpty()) return HResult.Fail("Not in a household.")
        val result = guarded { db, _ ->
            try {
                val household = db.document("households/$hid").get().await()
                if (!household.exists()) { prefs.clear(); throw Refused("This household no longer exists.") }
                val members = db.collection("households/$hid/members").get().await().documents.map { d ->
                    HouseholdMember(d.id, d.getString("displayName").orEmpty(), HouseholdRole.parse(d.getString("role")), d.getBoolean("canDelete") == true)
                }
                HouseholdSnapshot(hid, household.getString("name").orEmpty(), household.getString("ownerId").orEmpty(), household.getBoolean("showBalances") == true, Households.sorted(members))
            } catch (e: FirebaseFirestoreException) {
                // The rules refuse the read when this person is no longer a member.
                if (e.code == Code.PERMISSION_DENIED) throw Refused(REMOVED) else throw e
            }
        }
        if (result is HResult.Fail && result.message == REMOVED) refresh() // this makes the phone forget the household
        return result
    }

    // ---- create, join, leave

    suspend fun create(name: String, nickname: String): String? {
        Households.householdNameError(name)?.let { return it }
        Households.nicknameError(nickname)?.let { return it }
        refresh()?.let { return it }
        return guarded { db, uid ->
            if (local().inHousehold) throw Refused("You are already in a household.")
            val ref = db.collection("households").document()
            // Two steps on purpose: the rules check that the household exists before its owner may become a member.
            ref.set(mapOf("ownerId" to uid, "name" to name.trim(), "showBalances" to false, "createdAt" to FieldValue.serverTimestamp())).await()
            ref.collection("members").document(uid).set(mapOf("role" to "owner", "canDelete" to true, "displayName" to nickname.trim())).await()
            db.document("users/$uid").set(mapOf("householdId" to ref.id, "displayName" to nickname.trim()), SetOptions.merge()).await()
            remember(ref.id, name.trim(), true, nickname.trim(), false)
        }.errorOrNull
    }

    suspend fun join(code: String, nickname: String): String? {
        val clean = Households.normalizeCode(code)
        Households.codeError(clean)?.let { return it }
        Households.nicknameError(nickname)?.let { return it }
        refresh()?.let { return it }
        return guarded { db, uid ->
            if (local().inHousehold) throw Refused("You are already in a household.")
            val invite = db.document("invites/$clean").get().await()
            val hid = invite.getString("householdId").orEmpty()
            if (!invite.exists() || hid.isEmpty()) throw Refused("That invite code is not valid.")
            db.document("households/$hid/members/$uid")
                .set(mapOf("role" to "member", "canDelete" to false, "displayName" to nickname.trim(), "inviteCode" to clean)).await()
            db.document("users/$uid").set(mapOf("householdId" to hid, "displayName" to nickname.trim()), SetOptions.merge()).await()
            // Joining again after being removed: loans and deposits I shared in the earlier membership are stale now (best effort).
            runCatching { deleteOwnSharedItems(db, hid, uid) }
            val household = db.document("households/$hid").get().await()
            remember(hid, household.getString("name").orEmpty(), false, nickname.trim(), false)
        }.errorOrNull
    }

    /** A member leaves. The owner must hand the household over first. */
    suspend fun leave(): String? {
        if (!local().inHousehold) return null
        Households.leaveError(local().isOwner)?.let { return it }
        val hid = local().id
        return guarded { db, uid ->
            // My shared loans and deposits go first: after I leave nobody else could clear them (the rules only let their owner delete
            // them). If this fails I stay in, so nothing is half done.
            try { deleteOwnSharedItems(db, hid, uid) } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw Refused("Could not remove your shared loans and deposits, so you were not removed from the household. ${friendly(e)}")
            }
            db.document("households/$hid/members/$uid").delete().await()
            db.document("users/$uid").set(mapOf("householdId" to FieldValue.delete()), SetOptions.merge()).await()
            prefs.clear()
        }.errorOrNull
    }

    /** Deletes the loans and fixed deposits this person shared (step 5 builds sharing; the old app may already have made some). */
    private suspend fun deleteOwnSharedItems(db: FirebaseFirestore, hid: String, uid: String) {
        val mine = db.collection("households/$hid/sharedItems").whereEqualTo("ownerUid", uid).get(Source.SERVER).await()
        // A batch holds at most 500 writes.
        mine.documents.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    // ---- owner actions

    suspend fun remove(memberUid: String): String? {
        Households.removeError(local().isOwner, fb.uid.orEmpty(), memberUid)?.let { return it }
        val hid = local().id
        return guarded { db, _ -> db.document("households/$hid/members/$memberUid").delete().await() }.errorOrNull
    }

    /**
     * The owner hands the household to another member; the old owner becomes a member. One batch, so there are never two owners or none.
     * The old owner's invite code is cancelled first: codes belong to the owner who made them, and the new owner makes a new one.
     */
    suspend fun transfer(newOwnerUid: String): String? {
        val hid = local().id
        return guarded { db, uid ->
            val ids = db.collection("households/$hid/members").get().await().documents.map { it.id }.toSet()
            Households.transferError(local().isOwner, uid, newOwnerUid, ids)?.let { throw Refused(it) }
            revokeInviteStep(db, uid)
            val base = "households/$hid"
            val batch = db.batch()
            batch.update(db.document("$base/members/$newOwnerUid"), mapOf("role" to "owner", "canDelete" to true))
            batch.update(db.document(base), mapOf("ownerId" to newOwnerUid))
            batch.update(db.document("$base/members/$uid"), mapOf("role" to "member", "canDelete" to false))
            batch.commit().await()
            val l = local()
            remember(l.id, l.name, false, l.nickname)
        }.errorOrNull
    }

    suspend fun setCanDelete(memberUid: String, canDelete: Boolean): String? {
        if (!local().isOwner) return "Only the owner can change this."
        val hid = local().id
        return guarded { db, _ -> db.document("households/$hid/members/$memberUid").update("canDelete", canDelete).await() }.errorOrNull
    }

    suspend fun setShowBalances(show: Boolean): String? {
        if (!local().isOwner) return "Only the owner can change this."
        val hid = local().id
        return guarded { db, _ ->
            db.document("households/$hid").update("showBalances", show).await()
            val l = local()
            remember(l.id, l.name, l.isOwner, l.nickname, show)
        }.errorOrNull
    }

    suspend fun rename(name: String): String? {
        if (!local().isOwner) return "Only the owner can change this."
        Households.householdNameError(name)?.let { return it }
        val hid = local().id
        return guarded { db, _ ->
            db.document("households/$hid").update("name", name.trim()).await()
            val l = local()
            remember(l.id, name.trim(), l.isOwner, l.nickname)
        }.errorOrNull
    }

    // ---- invite codes

    private suspend fun currentCodeStep(db: FirebaseFirestore, uid: String): String? =
        db.document("users/$uid").get().await().getString("inviteCode")?.takeIf { it.isNotEmpty() }

    /** The current invite code, or null when none was made (or it could not be read). */
    suspend fun currentCode(): String? = (guarded { db, uid -> currentCodeStep(db, uid) } as? HResult.Ok)?.value

    /** A new invite code. The old one stops working. */
    suspend fun newCode(): HResult<String> {
        if (!local().isOwner) return HResult.Fail("Only the owner can invite people.")
        val hid = local().id
        return guarded<String> { db, uid ->
            val count = db.collection("households/$hid/members").get().await().size()
            if (Households.limitReached(count)) throw Refused("A household can have up to ${Households.MAX_MEMBERS} people.")
            val old = currentCodeStep(db, uid)
            repeat(5) {
                val code = Households.newCode(random)
                try {
                    db.document("invites/$code").set(mapOf("householdId" to hid, "ownerId" to uid, "createdAt" to FieldValue.serverTimestamp())).await()
                } catch (e: FirebaseFirestoreException) {
                    // A code that already exists cannot be overwritten (the rules refuse): try another one.
                    if (isDenied(e)) return@repeat else throw e
                }
                db.document("users/$uid").set(mapOf("inviteCode" to code), SetOptions.merge()).await()
                if (old != null && old != code) runCatching { db.document("invites/$old").delete().await() }
                return@guarded code
            }
            throw Refused("Could not make a code. Try again.")
        }
    }

    /** Stops people joining with the current code. */
    suspend fun revokeCode(): String? = guarded { db, uid -> revokeInviteStep(db, uid) }.errorOrNull

    private suspend fun revokeInviteStep(db: FirebaseFirestore, uid: String) {
        val code = currentCodeStep(db, uid) ?: return
        db.document("invites/$code").delete().await()
        db.document("users/$uid").set(mapOf("inviteCode" to FieldValue.delete()), SetOptions.merge()).await()
    }

    // ---- any member

    suspend fun setMyNickname(nickname: String): String? {
        Households.nicknameError(nickname)?.let { return it }
        if (!local().inHousehold) return "Not in a household."
        val hid = local().id
        return guarded { db, uid ->
            db.document("households/$hid/members/$uid").update("displayName", nickname.trim()).await()
            db.document("users/$uid").set(mapOf("displayName" to nickname.trim()), SetOptions.merge()).await()
            val l = local()
            remember(l.id, l.name, l.isOwner, nickname.trim())
        }.errorOrNull
    }
}
