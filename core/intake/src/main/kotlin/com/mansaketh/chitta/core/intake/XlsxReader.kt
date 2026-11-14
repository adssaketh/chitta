package com.mansaketh.chitta.core.intake

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Reads the first sheet of an .xlsx file into rows of text, with no library. An .xlsx is a zip of XML parts; the few parts a plain table needs
 * (workbook, its relations, shared strings, the sheet) are read with simple tag scanning, so no XML parser is started and external entities
 * and DTDs are never looked at. Formulas are not calculated: a cell gives the value Excel last saved. Dates come through as Excel serial
 * numbers unless the cell is text; the caller decides which columns are dates.
 *
 * Refuses a file that is not a zip, has no sheet, or whose parts are larger than [MAX_PART_BYTES].
 */
object XlsxReader {
    const val MAX_PART_BYTES = 20 * 1024 * 1024

    fun looksLikeXlsx(bytes: ByteArray): Boolean = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** Rows of the first sheet, each as long as its last non-empty cell. Blank rows are kept as empty lists so row numbers match Excel. Null if unreadable. */
    fun firstSheet(bytes: ByteArray): List<List<String>>? {
        val parts = HashMap<String, String>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    if (name == "xl/workbook.xml" || name == "xl/_rels/workbook.xml.rels" || name == "xl/sharedStrings.xml" || name.startsWith("xl/worksheets/sheet")) {
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(16 * 1024)
                        var total = 0
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_PART_BYTES) return null
                            out.write(buf, 0, n)
                        }
                        parts[name] = out.toString(Charsets.UTF_8)
                    }
                    e = zip.nextEntry
                }
            }
        } catch (ex: Exception) { return null }

        val sheetPath = firstSheetPath(parts) ?: return null
        val xml = parts[sheetPath] ?: return null
        val shared = parts["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        return rows(xml, shared)
    }

    private fun firstSheetPath(parts: Map<String, String>): String? {
        val wb = parts["xl/workbook.xml"]
        val rels = parts["xl/_rels/workbook.xml.rels"]
        if (wb != null && rels != null) {
            val rid = Regex("""<sheet\b[^>]*?r:id="([^"]+)"""").find(wb)?.groupValues?.get(1)
            if (rid != null) {
                for (m in Regex("""<Relationship\b([^>]*)/?>""").findAll(rels)) {
                    val attrs = m.groupValues[1]
                    if (attr(attrs, "Id") == rid) {
                        val target = attr(attrs, "Target") ?: continue
                        val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target
                        if (parts.containsKey(path)) return path
                    }
                }
            }
        }
        return parts.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }.minOrNull()
    }

    private fun attr(attrs: String, name: String): String? = Regex("""(?:^|\s)$name\s*=\s*"([^"]*)"""").find(attrs)?.groupValues?.get(1)?.let { unescape(it) }

    private fun sharedStrings(xml: String): List<String> =
        Regex("""<si\b[^>]*>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { textOf(it.groupValues[1]) }.toList()

    /** All the text in <t> runs, leaving out phonetic hints. */
    private fun textOf(inner: String): String =
        Regex("""<t\b[^>]*>(.*?)</t>|<t\b[^>]*/>""", RegexOption.DOT_MATCHES_ALL).findAll(inner.replace(Regex("""<rPh\b.*?</rPh>""", RegexOption.DOT_MATCHES_ALL), ""))
            .joinToString("") { unescape(it.groupValues[1]) }

    private fun rows(xml: String, shared: List<String>): List<List<String>> {
        val rows = ArrayList<MutableList<String>>()
        var nextRow = 1
        for (rm in Regex("""<row\b([^>]*?)(?:/>|>(.*?)</row>)""", RegexOption.DOT_MATCHES_ALL).findAll(xml)) {
            val rowNo = attr(rm.groupValues[1], "r")?.toIntOrNull() ?: nextRow
            while (rows.size < rowNo - 1) rows.add(ArrayList())
            nextRow = rowNo + 1
            val cells = ArrayList<String>()
            var col = 0
            for (cm in Regex("""<c\b([^>]*?)(?:/>|>(.*?)</c>)""", RegexOption.DOT_MATCHES_ALL).findAll(rm.groupValues[2])) {
                val attrs = cm.groupValues[1]
                attr(attrs, "r")?.let { ref -> col = columnIndex(ref) }
                val body = cm.groupValues[2]
                val type = attr(attrs, "t")
                val value = when (type) {
                    "s" -> Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> textOf(body)
                    else -> Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.let { unescape(it).trim() } ?: ""
                }
                while (cells.size < col) cells.add("")
                if (cells.size == col) cells.add(value) else cells[col] = value
                col++
            }
            while (cells.isNotEmpty() && cells.last().isEmpty()) cells.removeAt(cells.size - 1)
            rows.add(cells)
        }
        return rows
    }

    /** "B17" is column 1 (0-based). */
    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) { if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else if (ch in 'a'..'z') n = n * 26 + (ch - 'a' + 1) else break }
        return (n - 1).coerceAtLeast(0)
    }

    private fun unescape(s: String): String {
        if ('&' !in s) return s
        return Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);""").replace(s) { m ->
            when (val e = m.groupValues[1]) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
                else -> {
                    val cp = if (e.startsWith("#x")) e.substring(2).toIntOrNull(16) else e.substring(1).toIntOrNull()
                    if (cp != null && cp in 0..0x10FFFF) String(Character.toChars(cp)) else m.value
                }
            }
        }
    }
}
