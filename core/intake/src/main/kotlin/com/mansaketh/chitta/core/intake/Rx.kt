package com.mansaketh.chitta.core.intake

/**
 * Regular expressions written the way the old Dart app and Ping (JavaScript) write them. Java's `\s` is ASCII only, while Dart and
 * JavaScript also count the no-break space and a few more. A bank SMS can carry one, and Ping's content key must agree with Ping's
 * to the last character, so `\s` is widened here. Everything else behaves the same in all three.
 */
internal object Rx {
    private const val WS = "\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"

    /** Turns `\s` into the wide set, inside and outside a character class. `\S` outside a class becomes the opposite set. */
    fun widen(pattern: String): String {
        val out = StringBuilder()
        var inClass = false
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\\' && i + 1 < pattern.length) {
                val n = pattern[i + 1]
                when {
                    n == 's' -> out.append(if (inClass) WS else "[$WS]")
                    n == 'S' && !inClass -> out.append("[^$WS]")
                    else -> out.append(c).append(n)
                }
                i += 2
                continue
            }
            if (c == '[') inClass = true else if (c == ']') inClass = false
            out.append(c)
            i++
        }
        return out.toString()
    }

    fun of(pattern: String, ignoreCase: Boolean = false, multiline: Boolean = false): Regex {
        val opts = buildSet {
            if (ignoreCase) add(RegexOption.IGNORE_CASE)
            if (multiline) add(RegexOption.MULTILINE)
        }
        return Regex(widen(pattern), opts)
    }

    /** Runs of whitespace (the wide set) as one space, trimmed: the text both apps hash for the duplicate key. */
    val WHITESPACE_RUN: Regex = of("\\s+")
}
