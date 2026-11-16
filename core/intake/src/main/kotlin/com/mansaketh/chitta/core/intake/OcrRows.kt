package com.mansaketh.chitta.core.intake

/** One line of text found in a picture, with where it sits (pixels). */
data class OcrLine(val text: String, val left: Int, val top: Int, val bottom: Int) {
    val centre: Double get() = (top + bottom) / 2.0
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/**
 * Puts the lines a text recogniser found back into table rows: lines whose middles are close (less than 0.6 of a typical line height apart,
 * one after the other) are one row, read left to right. A table cell that wraps onto two lines ("... TIER I" and "POP") joins the numbers
 * beside it, so a row of a statement reads "name units price value" in one piece, which is what the statement readers look for.
 */
object OcrRows {
    fun join(lines: List<OcrLine>): String {
        val usable = lines.filter { it.text.isNotBlank() }.sortedBy { it.centre }
        if (usable.isEmpty()) return ""
        val tolerance = 0.6 * usable.map { it.height }.sorted()[usable.size / 2]
        val rows = ArrayList<MutableList<OcrLine>>()
        var lastCentre = Double.NEGATIVE_INFINITY
        for (l in usable) {
            if (rows.isNotEmpty() && l.centre - lastCentre < tolerance) rows.last().add(l) else rows.add(mutableListOf(l))
            lastCentre = l.centre
        }
        return rows.joinToString("\n") { r -> r.sortedWith(compareBy({ it.left }, { it.top })).joinToString(" ") { it.text.trim() } }
    }
}
