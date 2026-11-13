package com.mansaketh.chitta.core.intake

import kotlin.test.Test
import kotlin.test.assertEquals

class InstrumentTest {
    @Test
    fun everyFixtureGetsTheExpectedKeyAndLabel() {
        val cases = json("instrument_synthetic.json").list("cases")
        // The expected keys were made by reading the whole list together, as Ping does.
        val index = Instrument.indexAll(cases.map { Triple(it.str("id"), it.str("sender"), it.str("text")) })
        for (c in cases) {
            // A null key means the old app's index skipped the message (an OTP or not money-like).
            if (!c.has("key")) { assertEquals(null, index[c.str("id")], "case ${c.str("id")} is left out"); continue }
            val key = index[c.str("id")]
            assertEquals(c.str("key"), key, "case ${c.str("id")} key")
            val label = if (c.has("label")) c.str("label") else null
            assertEquals(label, Instrument.label(key!!), "case ${c.str("id")} label")
        }
    }
}
