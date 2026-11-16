package com.mansaketh.chitta.core.intake

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The list that ships in the app's assets (converted from the old app's knownAccounts.dart). */
class DefaultKnownAccountsTest {
    private val known = KnownAccounts.parse(File("../../app/src/main/assets/known_accounts_default.json").readText())

    @Test fun hasAll42AccountsOfTheOldApp() = assertEquals(42, known.size)

    @Test fun scapiaCardsAreKnownWithAndWithoutTheNetwork() {
        assertEquals("Saketh - Federal", known.lookup("scapia:credit_card:visa")?.wallet)
        assertEquals("Saketh - Federal", known.lookup("scapia:credit_card:rupay")?.wallet)
        assertEquals("Saketh - Federal", known.lookup("scapia:credit_card:")?.wallet)
    }

    @Test fun addOnCardsCountOnTheMainCardAndPensionRowsAreNotTicked() {
        assertEquals("Saketh - Federal Bank Scapia Visa Credit Card", known.lookup("federal:credit_card:3777")?.dueCard)
        assertEquals(false, known.lookup("epfoho:account:4895")?.tickByDefault)
        assertTrue(known.lookup("icici:account:2090")!!.closed)
        assertNotNull(known.lookup("hdfc:credit_card:0886"))
    }

    @Test fun signetCardIsOperatingAndThreeFederalSavingsAccountsAreClosed() {
        assertEquals(false, known.lookup("federal:credit_card:9368")!!.closed)
        assertEquals("Saketh - Federal", known.lookup("federal:credit_card:9368")?.wallet)
        listOf("3269", "4365", "6299").forEach { assertTrue(known.lookup("federal:account:$it")!!.closed, it) }
    }
}
