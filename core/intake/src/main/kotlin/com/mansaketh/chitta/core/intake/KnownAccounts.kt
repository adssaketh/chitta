package com.mansaketh.chitta.core.intake

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Your own names for the accounts, cards and schemes found in bank SMS, and the Chitta account each one goes into (one account per
 * bank, cards combined). The old app kept this list in its source code; Chitta keeps it in a file on the phone so no account digits are
 * ever published with the code. With no file, nothing is "known": every message waits in the review list.
 */
@Serializable
data class KnownAccount(
    val name: String,
    val closed: Boolean = false,
    /** The Chitta account the entries go into. */
    val wallet: String = "",
    /** false for pension / provident fund schemes: shown in the list but not ticked. */
    val tickByDefault: Boolean = true,
    /** For an add-on card: the main card it shares its limit with, so what you owe is counted on that one card. */
    val dueCard: String = "",
)

@Serializable
private data class KnownAccountEntry(
    val key: String,
    val name: String,
    val closed: Boolean = false,
    val wallet: String = "",
    val tickByDefault: Boolean = true,
    val dueCard: String = "",
)

@Serializable
private data class KnownAccountsFile(val version: Int = 1, val accounts: List<KnownAccountEntry> = emptyList())

class KnownAccounts(private val byKey: Map<String, KnownAccount>) {
    val size: Int get() = byKey.size
    val isEmpty: Boolean get() = byKey.isEmpty()

    /**
     * The entry for an instrument key ("issuer:kind:last digits"). An exact key wins. Otherwise it matches on the ending digits (one SMS
     * may print "XX090" for 2090) and on the issuer, where a longer sender name such as "cpptnnps" counts as the registered "ptnnps".
     */
    fun lookup(instrumentKey: String): KnownAccount? {
        byKey[instrumentKey]?.let { return it }
        val p = instrumentKey.split(':')
        if (p.size != 3 || p[2].length < 3) return null
        for ((k, v) in byKey) {
            val q = k.split(':')
            if (q.size != 3 || q[2].isEmpty() || !q[2].endsWith(p[2])) continue
            if (q[0] == p[0] || (q[0].length >= 5 && p[0].contains(q[0]))) return v
        }
        return null
    }

    /** The entry whose ending digits match [digits] (as many as are shown), or null when none or several different accounts do. Nothing is guessed. */
    fun byDigits(digits: String): KnownAccount? {
        if (digits.length < 3) return null
        var found: KnownAccount? = null
        for ((k, v) in byKey) {
            val q = k.split(':')
            if (q.size != 3 || q[2].length < 3) continue
            if (digits.endsWith(q[2]) && q[2].all { it.isDigit() }) {
                if (found != null && found.wallet != v.wallet) return null
                if (found == null) found = v
            }
        }
        return found
    }

    fun toJson(): String = JSON.encodeToString(
        KnownAccountsFile(1, byKey.map { (k, v) -> KnownAccountEntry(k, v.name, v.closed, v.wallet, v.tickByDefault, v.dueCard) }),
    )

    companion object {
        val EMPTY = KnownAccounts(emptyMap())
        private val JSON = Json { ignoreUnknownKeys = true; prettyPrint = true }

        /** Reads the file. Throws [IntakeFormatException] when it is not a known-accounts file. */
        fun parse(text: String): KnownAccounts {
            val file = try { JSON.decodeFromString<KnownAccountsFile>(text) } catch (e: Exception) { throw IntakeFormatException("That is not a known-accounts file.") }
            val map = LinkedHashMap<String, KnownAccount>()
            for (a in file.accounts) {
                if (a.key.count { it == ':' } != 2 || a.name.isBlank()) throw IntakeFormatException("An entry in the file has no valid key or name: \"${a.key}\".")
                map[a.key] = KnownAccount(a.name, a.closed, a.wallet, a.tickByDefault, a.dueCard)
            }
            return KnownAccounts(map)
        }
    }
}

/** A file or message that is not what it should be. The text is plain and safe to show. */
open class IntakeFormatException(message: String) : Exception(message)
