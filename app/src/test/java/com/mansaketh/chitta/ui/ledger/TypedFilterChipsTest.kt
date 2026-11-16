package com.mansaketh.chitta.ui.ledger

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
private val NOW = ZonedDateTime.of(2026, 11, 16, 12, 0, 0, 0, IST).toInstant().toEpochMilli()

private fun cat(id: String, name: String, main: String? = null) = Category(id, name, null, null, null, 0, null, 0, false, null, main)
private fun acct(id: String, name: String) = Account(id, name, null, null, 0, null, 0, "INR", null, 2, null, null)

private val CATS = listOf(cat("food", "Food & dining"), cat("snacks", "Snacks", main = "food"), cat("fuel", "Fuel"))
private val ACCS = listOf(acct("hdfc", "HDFC UPI"), acct("cash", "Cash"))

private fun chips(text: String) = TypedFilterChips.chips(text, CATS, ACCS, NOW, IST)

/** The chips of PDF p.18 (Screen 10): wording, the month chip, and cutting a chip's words out of the search text. */
class TypedFilterChipsTest {
    @Test fun pageExampleGivesFoodOverAndNovember() {
        val text = "cat:food amt>200 from:2026-11-01 to:2026-11-30"
        assertEquals(listOf("Food & dining", "Over ₹200", "November"), chips(text).map { it.label })
    }

    @Test fun removingAChipCutsOnlyItsWords() {
        val text = "cat:food amt>200 from:2026-11-01 to:2026-11-30"
        val all = chips(text)
        assertEquals("amt>200 from:2026-11-01 to:2026-11-30", TypedFilterChips.without(text, all[0]))
        assertEquals("cat:food from:2026-11-01 to:2026-11-30", TypedFilterChips.without(text, all[1]))
        assertEquals("cat:food amt>200", TypedFilterChips.without(text, all[2]))
    }

    @Test fun plainWordsStayAndACommaAfterAFilterIsFine() {
        val text = "amt<500, food"
        val all = chips(text)
        assertEquals(listOf("Under ₹500"), all.map { it.label })
        assertEquals("food", TypedFilterChips.without(text, all[0]))
    }

    @Test fun plainWordsAndUnknownFiltersMakeNoChip() {
        assertTrue(chips("lunch at cafe").isEmpty())
        assertTrue(chips("foo:bar").isEmpty())
        assertTrue(chips("cat:nothing-like-this").isEmpty())
        assertTrue(chips("\"amt>200\"").isEmpty())
    }

    @Test fun amountWording() {
        assertEquals("₹200 or more", chips("amt>=200").single().label)
        assertEquals("₹200 or less", chips("amt<=200").single().label)
        assertEquals("₹100 to ₹500", chips("amt:100-500").single().label)
        assertEquals("Exactly ₹250.50", chips("amt:250.50").single().label)
    }

    @Test fun otherFiltersWording() {
        assertEquals("Not Food & dining", chips("-cat:food").single().label)
        assertEquals("HDFC UPI", chips("acc:hdfc").single().label)
        assertEquals("Last 30 days", chips("last:30d").single().label)
        assertEquals("Last month", chips("last:1m").single().label)
        assertEquals("Lent", chips("type:lent").single().label)
        assertEquals("Note: rent", chips("note:rent").single().label)
    }

    @Test fun datesWithoutAWholeMonthStaySeparate() {
        assertEquals(listOf("From 5 Nov", "Until 20 Nov"), chips("from:2026-11-05 to:2026-11-20").map { it.label })
        assertEquals(listOf("From 1 Oct 2025"), chips("from:2025-10-01").map { it.label })
    }

    @Test fun anOldMonthNamesItsYear() {
        assertEquals("October 2025", chips("from:2025-10-01 to:2025-10-31").single().label)
    }
}
