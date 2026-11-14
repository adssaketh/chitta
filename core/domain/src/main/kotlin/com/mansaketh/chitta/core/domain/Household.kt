package com.mansaketh.chitta.core.domain

import kotlin.random.Random

/** Who someone is in a household. These are the exact words Firestore stores in `members/{uid}.role`. */
enum class HouseholdRole(val code: String) {
    Owner("owner"), Member("member");

    companion object {
        /** An unknown or missing word counts as a plain member, never as the owner. */
        fun parse(code: String?): HouseholdRole = if (code == Owner.code) Owner else Member
    }
}

data class HouseholdMember(val uid: String, val nickname: String, val role: HouseholdRole, val canDelete: Boolean) {
    val isOwner: Boolean get() = role == HouseholdRole.Owner
}

/**
 * Rules for a household (Phase 9b, step 2): invite codes, the member limit, who may hand over, remove or leave, and how members are
 * listed. Ported from the old Flutter app's pure rules and tests so both apps agree on the same Firestore data.
 * Plain Kotlin: the server calls live in the app.
 */
object Households {
    /** Master section 7: up to 8 people. Firestore rules cannot count members, so this is checked when the owner makes a code. */
    const val MAX_MEMBERS = 8
    const val CODE_LENGTH = 8

    /** No look-alikes: no 0/O and no 1/I (same letters as the old app, so old and new codes read the same). */
    const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** A new code. The caller passes a secure random source (the tests pass a seeded one). */
    fun newCode(random: Random): String = buildString { repeat(CODE_LENGTH) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) } }

    /** What someone typed or pasted, cleaned: letters and digits only, upper case. "k7pm-3qx9" becomes "K7PM3QX9". */
    fun normalizeCode(input: String): String = input.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    /** "ABCD2345" shown as "ABCD-2345". Anything that is not a full code is shown as it is. */
    fun formatCode(code: String): String = if (code.length == CODE_LENGTH) code.substring(0, 4) + "-" + code.substring(4) else code

    /** Why [normalized] cannot be a code, or null. */
    fun codeError(normalized: String): String? =
        if (normalized.length != CODE_LENGTH) "Invite codes have $CODE_LENGTH letters and numbers." else null

    /** The name others see. Blank gives a message. */
    fun nicknameError(nickname: String): String? = if (nickname.isBlank()) "Enter the name others will see." else null

    fun householdNameError(name: String): String? = if (name.isBlank()) "Enter a name for the household." else null

    /** A starting nickname: the first word of the Google name, else the part of the email before the "@", else empty. */
    fun defaultNickname(displayName: String?, email: String?): String {
        val first = displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
        if (first.isNotEmpty()) return first
        val e = email?.trim().orEmpty()
        return if ("@" in e) e.substringBefore("@") else ""
    }

    /** True when no more people may be invited. [memberCount] includes the owner. */
    fun limitReached(memberCount: Int): Boolean = memberCount >= MAX_MEMBERS

    /** Places left for the invite sheet ("6 places are left in the household"). Never below zero. */
    fun placesLeft(memberCount: Int): Int = (MAX_MEMBERS - memberCount).coerceAtLeast(0)

    /** Why [newOwnerUid] cannot become the owner, or null when the hand-over is fine. [memberUids] is everyone in the household now. */
    fun transferError(iAmOwner: Boolean, myUid: String, newOwnerUid: String, memberUids: Set<String>): String? = when {
        !iAmOwner -> "Only the owner can hand over the household."
        newOwnerUid.isEmpty() -> "Pick a member."
        newOwnerUid == myUid -> "You are already the owner."
        newOwnerUid !in memberUids -> "That person is no longer in the household."
        myUid !in memberUids -> "You are not in this household."
        else -> null
    }

    /** Why [targetUid] cannot be removed by me, or null. The owner cannot remove themselves (they hand over, then leave). */
    fun removeError(iAmOwner: Boolean, myUid: String, targetUid: String): String? = when {
        !iAmOwner -> "Only the owner can remove members."
        targetUid == myUid -> "You can't remove yourself. Make another member the owner first, then leave."
        else -> null
    }

    /** Why I cannot leave, or null. The owner must hand over first, so a household is never left without one. */
    fun leaveError(iAmOwner: Boolean): String? =
        if (iAmOwner) "As the owner you can't leave. Make another member the owner first." else null

    /** Owner first, then by name (ignoring case), then by id so the order never jumps between phones. */
    fun sorted(members: List<HouseholdMember>): List<HouseholdMember> =
        members.sortedWith(compareByDescending<HouseholdMember> { it.isOwner }.thenBy { it.nickname.lowercase() }.thenBy { it.uid })

    /** The grey line under a member on the Household page (PDF p.31): "Owner · can delete others' entries". */
    fun memberLine(member: HouseholdMember): String {
        val who = if (member.isOwner) "Owner" else "Member"
        return who + " · " + if (member.canDelete) "can delete others' entries" else "deletes only their own"
    }

    /** What a member shows as when their nickname is blank. */
    const val UNKNOWN_NAME = "Member"

    fun displayName(member: HouseholdMember): String = member.nickname.trim().ifEmpty { UNKNOWN_NAME }
}
