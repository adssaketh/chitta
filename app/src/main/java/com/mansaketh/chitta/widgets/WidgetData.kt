package com.mansaketh.chitta.widgets

import android.content.Context
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.database.PlanRepository
import com.mansaketh.chitta.core.domain.WidgetFigures
import com.mansaketh.chitta.core.domain.WidgetLines
import com.mansaketh.chitta.data.SecurityPrefs
import com.mansaketh.chitta.data.UiPrefs
import com.mansaketh.chitta.di.Clock
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/** A widget is not an Activity or a ViewModel, so it reaches the book through this entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun ledger(): LedgerRepository
    fun plan(): PlanRepository
    fun accounts(): AccountRepository
    fun categories(): CategoryRepository
    fun uiPrefs(): UiPrefs
    fun security(): SecurityPrefs
    fun clock(): Clock
}

object WidgetData {
    /**
     * The spend widget's words, or null when the book cannot be read right now (the widget then says so instead of showing old numbers).
     * Figures are masked when hide-amounts is on or the lock is on (DECISIONS D166).
     */
    suspend fun lines(context: Context): WidgetLines? = runCatching {
        val e = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val now = e.clock().nowMs()
        val figures = WidgetFigures.compute(
            entries = e.ledger().observeAllOnce(),
            budgets = e.plan().observeBudgets().first(),
            objectives = e.plan().observeObjectives().first(),
            categories = e.categories().observeAll().first(),
            accounts = e.accounts().observeAll().first(),
            sinkingFunds = e.plan().observeSinkingFunds().first(),
            cashAccounts = e.uiPrefs().cashAccounts.value,
            preferCash = e.uiPrefs().preferCash.value,
            heroBudgetId = e.uiPrefs().heroBudgetId.value,
            nowMs = now, zone = e.clock().zone(),
        )
        val sec = e.security()
        WidgetLines.of(figures, hidden = sec.hideAmounts.value || sec.lockEnabled.value)
    }.getOrNull()
}
