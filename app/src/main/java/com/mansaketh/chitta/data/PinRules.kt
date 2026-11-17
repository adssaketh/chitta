package com.mansaketh.chitta.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The in-app PIN (Screen 01 Lock, PDF p.9). Pure rules, no Android, so they run in JVM tests.
 * Four digits; three wrong tries, then a 30-second wait; the count starts again after the wait.
 */
object PinRules {
    const val LENGTH = 4
    const val MAX_TRIES = 3
    const val WAIT_MS = 30_000L

    /** [wrong] tries used in this round; [waitUntilMs] is zero when there is no wait running. */
    data class Attempts(val wrong: Int = 0, val waitUntilMs: Long = 0L)

    sealed interface Check {
        data object Ok : Check
        data class Wrong(val triesLeft: Int) : Check
        data class Waiting(val secondsLeft: Int) : Check
    }

    /** Judges one try. While a wait is running the try is not counted and the PIN is not even looked at. */
    fun check(correct: Boolean, before: Attempts, nowMs: Long): Pair<Check, Attempts> {
        if (nowMs < before.waitUntilMs) return Check.Waiting(secondsLeft(before.waitUntilMs, nowMs)) to before
        if (correct) return Check.Ok to Attempts()
        val wrong = before.wrong + 1
        return if (wrong >= MAX_TRIES) Check.Waiting((WAIT_MS / 1000).toInt()) to Attempts(0, nowMs + WAIT_MS)
        else Check.Wrong(MAX_TRIES - wrong) to Attempts(wrong, 0L)
    }

    fun secondsLeft(waitUntilMs: Long, nowMs: Long): Int = (((waitUntilMs - nowMs).coerceAtLeast(0) + 999) / 1000).toInt()

    /** The line under the dots. Singular and plural as the page words them ("2 tries left before a 30-second wait"). */
    fun message(check: Check): String? = when (check) {
        Check.Ok -> null
        is Check.Wrong -> "Wrong PIN. ${check.triesLeft} ${if (check.triesLeft == 1) "try" else "tries"} left before a ${WAIT_MS / 1000}-second wait."
        is Check.Waiting -> "Too many tries. Try again in ${check.secondsLeft} ${if (check.secondsLeft == 1) "second" else "seconds"}."
    }

    fun isValid(pin: String) = pin.length == LENGTH && pin.all { it in '0'..'9' }
}

/** Salted PBKDF2 of the PIN. Only the salt and the hash are kept on the phone, never the digits. */
object PinHash {
    private const val ITERATIONS = 120_000
    private const val BITS = 256

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()

    fun hash(pin: String, saltHex: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), saltHex.fromHex(), ITERATIONS, BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.toHex()
    }

    fun matches(pin: String, saltHex: String, hashHex: String): Boolean =
        MessageDigest.isEqual(hash(pin, saltHex).toByteArray(), hashHex.toByteArray())

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
