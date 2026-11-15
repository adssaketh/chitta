package com.mansaketh.chitta.core.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun member(uid: String, name: String, owner: Boolean = false, canDelete: Boolean = false) =
    HouseholdMember(uid, name, if (owner) HouseholdRole.Owner else HouseholdRole.Member, canDelete)

class HouseholdTest {
    // ---- limit (ported from household_ownership_test.dart)
    @Test fun limitIsEightPeople() {
        assertEquals(8, Households.MAX_MEMBERS)
        assertTrue(!Households.limitReached(1), "one person: more can join")
        assertTrue(!Households.limitReached(7), "seven people: one more can join")
        assertTrue(Households.limitReached(8), "eight people: full")
        assertTrue(Households.limitReached(9), "more than eight counts as full")
    }

    @Test fun placesLeftNeverGoesBelowZero() {
        assertEquals(6, Households.placesLeft(2)) // PDF p.32: "6 places are left" with 2 people
        assertEquals(0, Households.placesLeft(8))
        assertEquals(0, Households.placesLeft(11))
    }

    // ---- hand over (ported)
    private fun transfer(iAmOwner: Boolean = true, me: String = "me", to: String = "sis", members: Set<String> = setOf("me", "sis", "dad")) =
        Households.transferError(iAmOwner, me, to, members)

    @Test fun handOverRules() {
        assertNull(transfer(), "owner can hand over to a member")
        assertNotNull(transfer(iAmOwner = false), "a member cannot hand over")
        assertNotNull(transfer(to = ""), "a member must be picked")
        assertNotNull(transfer(to = "me"), "cannot hand over to yourself")
        assertNotNull(transfer(to = "gone"), "cannot hand over to someone who left")
        assertNotNull(transfer(members = setOf("sis", "dad")), "cannot hand over when I am not in the household")
        assertNull(transfer(members = setOf("me", "sis")), "two-person household can hand over")
    }

    @Test fun removeAndLeaveRules() {
        assertNull(Households.removeError(true, "me", "sis"))
        assertNotNull(Households.removeError(false, "me", "sis"), "only the owner removes")
        assertNotNull(Households.removeError(true, "me", "me"), "the owner cannot remove themselves")
        assertNull(Households.leaveError(false))
        assertNotNull(Households.leaveError(true), "the owner hands over before leaving")
    }

    // ---- codes
    @Test fun codeIsCleanedFromWhatIsTyped() {
        assertEquals("K7PM3QX9", Households.normalizeCode("k7pm-3qx9"))
        assertEquals("K7PM3QX9", Households.normalizeCode(" K7PM 3QX9\n"))
        assertEquals("", Households.normalizeCode("--  "))
    }

    @Test fun codeIsShownWithADash() {
        assertEquals("K7PM-3QX9", Households.formatCode("K7PM3QX9"))
        assertEquals("K7P", Households.formatCode("K7P"))
        assertEquals("K7PM-3QX9", Households.formatCode(Households.normalizeCode(Households.formatCode("K7PM3QX9"))))
    }

    @Test fun codeLengthIsChecked() {
        assertNull(Households.codeError("K7PM3QX9"))
        assertNotNull(Households.codeError("K7PM3QX"))
        assertNotNull(Households.codeError("K7PM3QX99"))
        assertNotNull(Households.codeError(""))
    }

    @Test fun newCodesAreEightCharactersFromTheAlphabetOnly() {
        val random = Random(42)
        repeat(500) {
            val code = Households.newCode(random)
            assertEquals(8, code.length)
            assertTrue(code.all { it in Households.CODE_ALPHABET }, "code $code uses only the allowed letters")
            assertNull(Households.codeError(Households.normalizeCode(code)))
        }
    }

    @Test fun alphabetHasNoLookAlikes() {
        listOf('0', 'O', '1', 'I').forEach { assertTrue(it !in Households.CODE_ALPHABET, "$it must not be in the alphabet") }
        assertEquals(32, Households.CODE_ALPHABET.toSet().size)
    }

    @Test fun differentSeedsGiveDifferentCodes() {
        val codes = (1..50).map { Households.newCode(Random(it)) }.toSet()
        assertTrue(codes.size > 45)
    }

    // ---- names
    @Test fun blankNamesAreRefused() {
        assertNotNull(Households.nicknameError("  "))
        assertNull(Households.nicknameError("Anu"))
        assertNotNull(Households.householdNameError(""))
        assertNull(Households.householdNameError("#manSAketh"))
    }

    @Test fun defaultNicknameUsesFirstNameThenEmail() {
        assertEquals("Mansa", Households.defaultNickname("Mansa Vedula", "x@y.com"))
        assertEquals("Mansa", Households.defaultNickname("  Mansa  ", null))
        assertEquals("adsaketh", Households.defaultNickname(null, "adsaketh@gmail.com"))
        assertEquals("adsaketh", Households.defaultNickname("   ", "adsaketh@gmail.com"))
        assertEquals("", Households.defaultNickname(null, "no-at-sign"))
        assertEquals("", Households.defaultNickname(null, null))
    }

    // ---- roles and listing
    @Test fun unknownRoleIsNeverOwner() {
        assertEquals(HouseholdRole.Owner, HouseholdRole.parse("owner"))
        assertEquals(HouseholdRole.Member, HouseholdRole.parse("member"))
        assertEquals(HouseholdRole.Member, HouseholdRole.parse("Owner"))
        assertEquals(HouseholdRole.Member, HouseholdRole.parse(null))
        assertEquals(HouseholdRole.Member, HouseholdRole.parse("admin"))
    }

    @Test fun ownerFirstThenByNameIgnoringCase() {
        val list = listOf(member("3", "zed"), member("1", "Anu"), member("2", "me", owner = true), member("4", "bala"))
        assertEquals(listOf("2", "1", "4", "3"), Households.sorted(list).map { it.uid })
    }

    @Test fun sameNameKeepsAStableOrder() {
        val list = listOf(member("b", "Anu"), member("a", "anu"), member("o", "Boss", owner = true))
        assertEquals(listOf("o", "a", "b"), Households.sorted(list).map { it.uid })
        assertEquals(Households.sorted(list), Households.sorted(list.reversed()))
    }

    @Test fun memberLinesMatchThePdf() {
        assertEquals("Owner · can delete others' entries", Households.memberLine(member("1", "You", owner = true, canDelete = true)))
        assertEquals("Member · deletes only their own", Households.memberLine(member("2", "Partner")))
        assertEquals("Member · can delete others' entries", Households.memberLine(member("3", "Dad", canDelete = true)))
        assertEquals("Owner · deletes only their own", Households.memberLine(member("4", "X", owner = true, canDelete = false)))
    }

    @Test fun blankNicknameShowsFallback() {
        assertEquals("Member", Households.displayName(member("1", "   ")))
        assertEquals("Anu", Households.displayName(member("1", " Anu ")))
    }
}
