package com.mansaketh.chitta.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinRulesTest {
    private val fresh = PinRules.Attempts()

    @Test fun firstWrongTryLeavesTwo() {
        val (check, after) = PinRules.check(false, fresh, 1_000L)
        assertEquals(PinRules.Check.Wrong(2), check)
        assertEquals(1, after.wrong)
        assertEquals("Wrong PIN. 2 tries left before a 30-second wait.", PinRules.message(check))
    }

    @Test fun secondWrongTryLeavesOneAndSaysTry() {
        val (c1, a1) = PinRules.check(false, fresh, 1_000L)
        val (c2, _) = PinRules.check(false, a1, 2_000L)
        assertEquals(PinRules.Check.Wrong(1), c2)
        assertEquals("Wrong PIN. 1 try left before a 30-second wait.", PinRules.message(c2))
        assertTrue(c1 is PinRules.Check.Wrong)
    }

    @Test fun thirdWrongTryStartsThirtySecondWait() {
        var a = fresh
        repeat(2) { a = PinRules.check(false, a, 1_000L).second }
        val (check, after) = PinRules.check(false, a, 10_000L)
        assertEquals(PinRules.Check.Waiting(30), check)
        assertEquals(40_000L, after.waitUntilMs)
        assertEquals(0, after.wrong)
    }

    @Test fun duringWaitEvenTheRightPinIsRefusedAndNothingChanges() {
        val waiting = PinRules.Attempts(0, 40_000L)
        val (check, after) = PinRules.check(true, waiting, 25_000L)
        assertEquals(PinRules.Check.Waiting(15), check)
        assertEquals(waiting, after)
    }

    @Test fun afterWaitThreeFreshTriesAreAllowed() {
        val waiting = PinRules.Attempts(0, 40_000L)
        val (check, after) = PinRules.check(false, waiting, 40_000L)
        assertEquals(PinRules.Check.Wrong(2), check)
        assertEquals(1, after.wrong)
    }

    @Test fun rightPinClearsTheCount() {
        val (check, after) = PinRules.check(true, PinRules.Attempts(2, 0L), 5_000L)
        assertEquals(PinRules.Check.Ok, check)
        assertEquals(PinRules.Attempts(), after)
    }

    @Test fun secondsRoundUp() {
        assertEquals(1, PinRules.secondsLeft(1_001L, 1_000L))
        assertEquals(30, PinRules.secondsLeft(31_000L, 1_000L))
        assertEquals(0, PinRules.secondsLeft(1_000L, 2_000L))
    }

    @Test fun pinIsFourDigitsOnly() {
        assertTrue(PinRules.isValid("0042"))
        assertFalse(PinRules.isValid("123"))
        assertFalse(PinRules.isValid("12345"))
        assertFalse(PinRules.isValid("12a4"))
    }

    @Test fun hashMatchesOnlyTheSamePinAndSalt() {
        val salt = PinHash.newSalt()
        val hash = PinHash.hash("2468", salt)
        assertTrue(PinHash.matches("2468", salt, hash))
        assertFalse(PinHash.matches("2469", salt, hash))
        assertFalse(PinHash.matches("2468", PinHash.newSalt(), hash))
        assertFalse(hash.contains("2468"))
    }
}
