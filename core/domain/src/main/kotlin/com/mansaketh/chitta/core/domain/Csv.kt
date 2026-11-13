package com.mansaketh.chitta.core.domain

/**
 * Plain CSV reading and writing (RFC 4180): quoted fields, doubled quotes, line breaks inside quotes, a byte-order mark,
 * and a guessed delimiter (comma, semicolon, tab or bar). No Android, no libraries.
 */
object Csv {
    private val DELIMITERS = charArrayOf(',', ';', '\t', '|')

    /** The delimiter that splits the first few lines into the same, largest number of fields. Comma when nothing fits. */
    fun detectDelimiter(text: String): Char {
        val lines = text.removePrefix("﻿").lineSequence().filter { it.isNotBlank() }.take(5).toList()
        if (lines.isEmpty()) return ','
        var best = ','
        var bestCount = 0
        for (d in DELIMITERS) {
            val counts = lines.map { countOutsideQuotes(it, d) }
            val least = counts.min()
            if (least > bestCount) { bestCount = least; best = d }
        }
        return best
    }

    private fun countOutsideQuotes(line: String, d: Char): Int {
        var inQuotes = false
        var n = 0
        for (ch in line) { if (ch == '"') inQuotes = !inQuotes else if (ch == d && !inQuotes) n++ }
        return n
    }

    /** Rows of fields. Completely empty lines are dropped; a trailing line break does not add a row. */
    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val src = text.removePrefix("﻿")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStarted = false
        var i = 0
        fun endField() { row.add(field.toString()); field.setLength(0); fieldStarted = false }
        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = ArrayList()
        }
        while (i < src.length) {
            val ch = src[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < src.length && src[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(ch)
            } else when {
                ch == '"' && !fieldStarted -> { inQuotes = true; fieldStarted = true }
                ch == delimiter -> endField()
                ch == '\r' -> { if (i + 1 < src.length && src[i + 1] == '\n') i++; endRow() }
                ch == '\n' -> endRow()
                else -> { field.append(ch); fieldStarted = true }
            }
            i++
        }
        if (fieldStarted || field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /** Fields are quoted only when they need to be. Lines end with CRLF, as spreadsheets expect. */
    fun write(rows: List<List<String>>, delimiter: Char = ','): String = buildString {
        for (r in rows) {
            r.forEachIndexed { i, f ->
                if (i > 0) append(delimiter)
                append(quote(f, delimiter))
            }
            append("\r\n")
        }
    }

    private fun quote(f: String, d: Char): String {
        val needs = f.any { it == d || it == '"' || it == '\n' || it == '\r' } || f.startsWith(" ") || f.endsWith(" ")
        return if (needs) "\"" + f.replace("\"", "\"\"") + "\"" else f
    }
}
