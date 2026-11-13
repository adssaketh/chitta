package com.mansaketh.chitta.core.domain

/**
 * How the user arranges the app (master sections 6 and 7): the sections on Home, the bottom tabs and the More shortcuts. These are small
 * pure rules with a plain-text form, so they can be kept on the device and tested on the JVM. Reading is forgiving: unknown names are
 * dropped and anything missing is added back with its default, so a layout saved by an older version never breaks a newer one.
 */

/** The sections a person can show on Home below the fixed top (Safe to spend, month rule, Coming up). */
enum class HomeSection(val key: String, val title: String, val usesAccounts: Boolean, val usesRange: Boolean) {
    Budgets("budgets", "Budgets", false, false),
    Balances("balances", "Balances", true, false),
    Recent("recent", "Recent entries", true, false),
    NetWorth("networth", "Net worth", false, false),
    Goals("goals", "Goals", false, false),
    Loans("loans", "Loans", false, false),
    Spending("spending", "Spending by category", true, true),
    Heatmap("heatmap", "Spending heatmap", true, false),
}

/** One section on Home. Empty [accountIds] means every account. [rangeDays] is the past-days window for sections that have one. */
data class SectionConfig(val section: HomeSection, val visible: Boolean, val accountIds: Set<String> = emptySet(), val rangeDays: Int? = null)

data class HomeLayout(val sections: List<SectionConfig>) {
    val shown: List<SectionConfig> get() = sections.filter { it.visible }

    fun toggle(section: HomeSection): HomeLayout = copy(sections = sections.map { if (it.section == section) it.copy(visible = !it.visible) else it })

    /** Moves a section up (-1) or down (+1) among all sections; at an end it stays. */
    fun move(section: HomeSection, delta: Int): HomeLayout {
        val i = sections.indexOfFirst { it.section == section }
        val j = i + delta
        if (i < 0 || j !in sections.indices) return this
        val list = sections.toMutableList()
        val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        return copy(sections = list)
    }

    fun withAccounts(section: HomeSection, ids: Set<String>) = copy(sections = sections.map { if (it.section == section) it.copy(accountIds = ids) else it })
    fun withRange(section: HomeSection, days: Int?) = copy(sections = sections.map { if (it.section == section) it.copy(rangeDays = days) else it })

    fun encode(): String = sections.joinToString(";") { s ->
        listOf(s.section.key, if (s.visible) "1" else "0", s.accountIds.sorted().joinToString(",") { Escape.write(it) }, s.rangeDays?.toString() ?: "").joinToString("|")
    }

    companion object {
        /** Calm by default (master section 4: density is opt-in): Budgets and Recent entries are on, the rest are off until switched on. */
        val DEFAULT = HomeLayout(HomeSection.entries.map { SectionConfig(it, visible = it == HomeSection.Budgets || it == HomeSection.Recent, rangeDays = if (it.usesRange) 30 else null) })

        fun decode(text: String?): HomeLayout {
            if (text.isNullOrBlank()) return DEFAULT
            val seen = LinkedHashMap<HomeSection, SectionConfig>()
            for (part in text.split(';')) {
                val f = part.split('|')
                if (f.size < 2) continue
                val section = HomeSection.entries.firstOrNull { it.key == f[0] } ?: continue
                if (section in seen) continue
                val ids = f.getOrNull(2).orEmpty().split(',').filter { it.isNotEmpty() }.map(Escape::read).toSet()
                val days = f.getOrNull(3)?.toIntOrNull()?.takeIf { it > 0 }
                seen[section] = SectionConfig(section, f[1] == "1", ids, if (section.usesRange) days ?: 30 else null)
            }
            for (c in DEFAULT.sections) if (c.section !in seen) seen[c.section] = c
            return HomeLayout(seen.values.toList())
        }
    }
}

/** Where a bottom tab can lead. The four slots hold any of these; More is always the fifth and cannot be swapped. */
enum class TabKey(val key: String, val label: String) {
    Home("home", "Home"),
    Ledger("ledger", "Ledger"),
    Plan("plan", "Plan"),
    Insights("insights", "Insights"),
    Accounts("accounts", "Accounts"),
    Budgets("budgets", "Budgets"),
    Goals("goals", "Goals"),
    Loans("loans", "Loans"),
    Bills("bills", "Bills"),
}

/** The four swappable bottom tabs and which one opens first. */
data class TabLayout(val slots: List<TabKey>, val first: TabKey) {
    init {
        require(slots.size == SLOT_COUNT && slots.toSet().size == SLOT_COUNT) { "Four different tabs are needed." }
        require(first in slots) { "The first tab must be one of the tabs." }
    }

    /**
     * Puts [key] in slot [index]. If it is already in another slot the two swap, so a tab never appears twice. The first tab
     * follows along if the tab it pointed to has gone.
     */
    fun set(index: Int, key: TabKey): TabLayout {
        if (index !in slots.indices || slots[index] == key) return this
        val list = slots.toMutableList()
        val other = list.indexOf(key)
        if (other >= 0) list[other] = list[index]
        list[index] = key
        return TabLayout(list, if (first in list) first else list[0])
    }

    fun withFirst(key: TabKey): TabLayout = if (key in slots) copy(first = key) else this

    fun encode(): String = slots.joinToString(",") { it.key } + "|" + first.key

    companion object {
        const val SLOT_COUNT = 4
        val DEFAULT = TabLayout(listOf(TabKey.Home, TabKey.Ledger, TabKey.Plan, TabKey.Insights), TabKey.Home)

        fun decode(text: String?): TabLayout {
            if (text.isNullOrBlank()) return DEFAULT
            val (slotsText, firstText) = text.split('|').let { it[0] to it.getOrNull(1) }
            val slots = slotsText.split(',').mapNotNull { k -> TabKey.entries.firstOrNull { it.key == k } }
            if (slots.size != SLOT_COUNT || slots.toSet().size != SLOT_COUNT) return DEFAULT
            val first = TabKey.entries.firstOrNull { it.key == firstText }?.takeIf { it in slots } ?: slots[0]
            return TabLayout(slots, first)
        }
    }
}

/** The shortcuts on the More page, in the user's order. Design gallery (debug builds) is always drawn last and is not part of this. */
enum class MoreItem(val key: String, val label: String) {
    Accounts("accounts", "Accounts"),
    Categories("categories", "Categories"),
    Customise("customise", "Customise Home and tabs"),
    Import("import", "Bring over your book"),
    Settings("settings", "Settings"),
}

object MoreOrder {
    val DEFAULT: List<MoreItem> = MoreItem.entries.toList()

    fun decode(text: String?): List<MoreItem> {
        val saved = text.orEmpty().split(',').mapNotNull { k -> MoreItem.entries.firstOrNull { it.key == k } }.distinct()
        return saved + MoreItem.entries.filter { it !in saved }
    }

    fun encode(items: List<MoreItem>): String = items.joinToString(",") { it.key }

    fun move(items: List<MoreItem>, item: MoreItem, delta: Int): List<MoreItem> {
        val i = items.indexOf(item)
        val j = i + delta
        if (i < 0 || j !in items.indices) return items
        return items.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }
}

/** Percent-escaping for the characters the plain-text forms use as separators. */
internal object Escape {
    fun write(s: String): String = buildString { for (ch in s) when (ch) { '%' -> append("%25"); '|' -> append("%7C"); ';' -> append("%3B"); ',' -> append("%2C"); else -> append(ch) } }
    fun read(s: String): String = s.replace("%2C", ",").replace("%3B", ";").replace("%7C", "|").replace("%25", "%")
}
