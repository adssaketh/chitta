package com.mansaketh.chitta.core.design

import androidx.compose.ui.text.TextStyle
import com.mansaketh.chitta.core.design.tokens.ChittaType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Master section 3: nothing below 13 sp (stamps 10 to 11 sp), titles never heavier than weight 500. */
class TypeRulesTest {
    private val type = ChittaType()

    private val everyStyle: Map<String, TextStyle> = mapOf(
        "amountHero" to type.amountHero, "amountLarge" to type.amountLarge, "amount" to type.amount,
        "amountRow" to type.amountRow, "amountSmall" to type.amountSmall, "headline" to type.headline,
        "dayNumber" to type.dayNumber, "title" to type.title, "body" to type.body, "bodyMedium" to type.bodyMedium,
        "label" to type.label, "labelMedium" to type.labelMedium, "button" to type.button, "mono" to type.mono,
        "stamp" to type.stamp,
    )

    @Test fun nothingBelowThirteenExceptStamps() {
        everyStyle.forEach { (name, style) ->
            val size = style.fontSize.value
            if (name == "stamp") assertTrue(size in 10f..11f, "stamp is $size sp") else assertTrue(size >= 13f, "$name is $size sp")
        }
    }

    @Test fun noStyleIsHeavierThanFiveHundred() {
        everyStyle.forEach { (name, style) ->
            assertTrue((style.fontWeight?.weight ?: 400) <= 500, "$name is heavier than 500")
        }
    }

    @Test fun lineHeightsSitOnTheFourPointGrid() {
        everyStyle.forEach { (name, style) ->
            assertEquals(0f, style.lineHeight.value % 4f, "$name line height ${style.lineHeight.value} is off the 4 grid")
        }
    }
}
