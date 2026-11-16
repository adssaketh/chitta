package com.mansaketh.chitta.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.domain.HomeSection
import com.mansaketh.chitta.core.domain.TabKey

/**
 * The one map from a feature to its icon (Phase 11 prompt, section 4, Icons). The tab bar, Customise and Home sections all read it, so a feature
 * has one icon everywhere. Icons come from the Phosphor set already bundled; none is a piggy bank. Where the app already had an icon for a
 * feature on a tab, that icon is the one (the old Customise list disagreed for Budgets, Goals and Loans; the tab icon wins).
 */
object FeatureIcons {
    fun tab(k: TabKey): ImageVector = when (k) {
        TabKey.Home -> ChittaIcons.Home
        TabKey.Ledger -> ChittaIcons.Ledger
        TabKey.Plan -> ChittaIcons.Plan
        TabKey.Insights -> ChittaIcons.Insights
        TabKey.Accounts -> ChittaIcons.Bank
        TabKey.Budgets -> ChittaIcons.Wallet
        TabKey.Goals -> ChittaIcons.Check
        TabKey.Loans -> ChittaIcons.Users
        TabKey.Bills -> ChittaIcons.Receipt
    }

    fun homeSection(s: HomeSection): ImageVector = when (s) {
        HomeSection.Budgets -> tab(TabKey.Budgets)
        HomeSection.Goals -> tab(TabKey.Goals)
        HomeSection.Loans -> tab(TabKey.Loans)
        HomeSection.Balances -> ChittaIcons.Wallet
        HomeSection.Recent -> ChittaIcons.Ledger
        HomeSection.NetWorth -> ChittaIcons.CatChartLineUp
        HomeSection.Spending -> ChittaIcons.Tag
        HomeSection.Heatmap -> ChittaIcons.Calendar
    }
}
