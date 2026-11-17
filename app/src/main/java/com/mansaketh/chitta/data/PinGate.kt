package com.mansaketh.chitta.data

import com.mansaketh.chitta.di.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Sets, checks and clears the in-app PIN. Tries and the wait are saved, so closing the app does not reset them. */
@Singleton
class PinGate @Inject constructor(private val prefs: SecurityPrefs, private val clock: Clock) {
    val hasPin = prefs.hasPin

    fun set(pin: String) {
        require(PinRules.isValid(pin))
        val salt = PinHash.newSalt()
        prefs.savePin(salt, PinHash.hash(pin, salt))
    }

    fun clear() = prefs.clearPin()

    fun verify(pin: String): PinRules.Check {
        val salt = prefs.pinSalt()
        val hash = prefs.pinHash()
        val now = clock.nowMs()
        val before = prefs.pinAttempts()
        // While a wait runs the PIN is not looked at, so the wait cannot be skipped by guessing.
        val correct = now >= before.waitUntilMs && salt != null && hash != null && PinHash.matches(pin, salt, hash)
        val (result, after) = PinRules.check(correct, before, now)
        prefs.setPinAttempts(after)
        return result
    }

    /** Seconds still to wait, or 0 when a try is allowed now. */
    fun waitSecondsLeft(): Int = PinRules.secondsLeft(prefs.pinAttempts().waitUntilMs, clock.nowMs())
}
