package com.mansaketh.chitta.reminders

import android.content.Context
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.database.PlanRepository
import com.mansaketh.chitta.core.domain.BudgetAlerts
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.data.AppPrefs
import com.mansaketh.chitta.di.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Budget alerts (PDF p.25). Whenever the book or the budgets change while the app process is alive (an entry typed in, added by Ping, or
 * saved from a share), checks every budget that has an alert and posts one note per budget, period and percent. The keys already sent are
 * kept in [AppPrefs] so a note is never repeated, and changing the percent can alert again.
 */
object BudgetAlertWatcher {
    @OptIn(FlowPreview::class)
    fun start(context: Context, scope: CoroutineScope, ledger: LedgerRepository, plan: PlanRepository, categories: CategoryRepository, prefs: AppPrefs, clock: Clock) {
        val app = context.applicationContext
        scope.launch {
            combine(ledger.observeAll(), plan.observeBudgets(), categories.observeAll()) { entries, budgets, cats -> Triple(entries, budgets, cats) }
                .debounce(1_500)
                .collect { (entries, budgets, cats) ->
                    runCatching {
                        val due = BudgetAlerts.due(budgets, entries, cats, clock.nowMs(), clock.zone(), prefs.alertsSent())
                        if (due.isEmpty()) return@runCatching
                        ReminderNotifications.createChannels(app)
                        due.forEach { a ->
                            ReminderNotifications.post(
                                app, a.key.hashCode(), CHANNEL_BUDGET, a.budgetName.ifBlank { "A budget" },
                                "${IndianFormat.rupees(a.spent)} of ${IndianFormat.rupees(a.limit)} spent, ${a.percent}% of the limit.",
                            )
                            prefs.markAlertSent(a.key)
                        }
                    }
                }
        }
    }
}
