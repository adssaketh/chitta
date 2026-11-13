package com.mansaketh.chitta.data

import android.content.Context
import com.mansaketh.chitta.core.domain.HomeLayout
import com.mansaketh.chitta.core.domain.MoreItem
import com.mansaketh.chitta.core.domain.MoreOrder
import com.mansaketh.chitta.core.domain.TabLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How the person has arranged the app: Home sections, bottom tabs, More shortcuts, and the few switches Home needs. Each is a StateFlow, so
 * screens update the moment a choice is made. These are device choices (DECISIONS D58); they move into the settings that sync between a
 * person's own devices in Phase 7.
 */
@Singleton
class UiPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val p = context.getSharedPreferences("chitta_ui", Context.MODE_PRIVATE)

    private val _home = MutableStateFlow(HomeLayout.decode(p.getString("home", null)))
    val homeLayout: StateFlow<HomeLayout> = _home
    fun setHomeLayout(v: HomeLayout) { _home.value = v; p.edit().putString("home", v.encode()).apply() }

    private val _tabs = MutableStateFlow(TabLayout.decode(p.getString("tabs", null)))
    val tabLayout: StateFlow<TabLayout> = _tabs
    fun setTabLayout(v: TabLayout) { _tabs.value = v; p.edit().putString("tabs", v.encode()).apply() }

    private val _more = MutableStateFlow(MoreOrder.decode(p.getString("more", null)))
    val moreOrder: StateFlow<List<MoreItem>> = _more
    fun setMoreOrder(v: List<MoreItem>) { _more.value = v; p.edit().putString("more", MoreOrder.encode(v)).apply() }

    /** Accounts counted by the cash check ("for spending"). Empty until the person marks some. */
    private val _cash = MutableStateFlow(p.getStringSet("cashAccounts", emptySet()).orEmpty().toSet())
    val cashAccounts: StateFlow<Set<String>> = _cash
    fun setCashAccounts(v: Set<String>) { _cash.value = v; p.edit().putStringSet("cashAccounts", v).apply() }

    /** Lead with the cash check instead of Safe to spend (master section 12). */
    private val _preferCash = MutableStateFlow(p.getBoolean("preferCash", false))
    val preferCash: StateFlow<Boolean> = _preferCash
    fun setPreferCash(v: Boolean) { _preferCash.value = v; p.edit().putBoolean("preferCash", v).apply() }

    /** The budget picked in Home's top bar. Null means the first one that qualifies. */
    private val _heroBudget = MutableStateFlow(p.getString("heroBudget", null))
    val heroBudgetId: StateFlow<String?> = _heroBudget
    fun setHeroBudgetId(v: String?) { _heroBudget.value = v; p.edit().apply { if (v == null) remove("heroBudget") else putString("heroBudget", v) }.apply() }

    /** First day of the week for the heatmap. */
    private val _firstDay = MutableStateFlow(runCatching { DayOfWeek.valueOf(p.getString("firstDay", "MONDAY")!!) }.getOrDefault(DayOfWeek.MONDAY))
    val firstDay: StateFlow<DayOfWeek> = _firstDay
    fun setFirstDay(v: DayOfWeek) { _firstDay.value = v; p.edit().putString("firstDay", v.name).apply() }
}
