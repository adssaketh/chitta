package com.mansaketh.chitta.ui.insights

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.BudgetResult
import com.mansaketh.chitta.core.domain.CategoryChange
import com.mansaketh.chitta.core.domain.MonthReview
import com.mansaketh.chitta.core.domain.MonthReviews
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.ui.categoryName
import com.mansaketh.chitta.ui.plan.periodText
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private fun YearMonth.name() = month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
private fun YearMonth.nameWithYear() = name() + " " + year

/**
 * Month in review (Phase 8f, master section 8): one calm screen about a finished month. Totals, the biggest category changes against the
 * month before, and which budgets were hit or missed. No badges, streaks or praise. Every figure comes from `MonthReviews` in `:core:domain`.
 */
@Composable
fun MonthReviewScreen(vm: InsightsViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ChittaTopBar("Month in review", onBack = onBack)
        HairlineRule()
        if (ui.loading) LoadingPlaceholder(Modifier.padding(Dimens.Margin))
        else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = 160.dp)) {
            MonthReviewContent(ui, onMonth = vm::setReviewMonth)
        }
    }
}

/** The body of the review, shared by the Review tab in Insights and the screen Home links to. */
@Composable
internal fun MonthReviewContent(ui: InsightsUi, onMonth: (YearMonth) -> Unit) {
    val c = ChittaTheme.colors
    val review = ui.review
    if (review == null) {
        Text(
            "No month has finished yet. Your first review is here on the 1st of next month.",
            style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap4),
        )
        return
    }
    val months = ui.reviewMonths // newest first
    val at = months.indexOf(review.month)
    MonthStepper(review.month, hasEarlier = at in 0 until months.lastIndex, hasLater = at > 0,
        onEarlier = { onMonth(months[at + 1]) }, onLater = { onMonth(months[at - 1]) })

    val previous = review.previous
    Row(Modifier.fillMaxWidth().padding(top = Dimens.Gap2), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap6)) {
        Column(Modifier.weight(1f)) {
            Text("Spent", style = ChittaTheme.type.label, color = c.inkMuted)
            AmountText(review.moneyOut, style = ChittaTheme.type.amountLarge, roundToRupee = true)
            Text(compareLine(review, ChittaTheme.hideAmounts), style = ChittaTheme.type.label, color = c.inkMuted)
        }
        Column(Modifier.weight(1f)) {
            Text("Saved", style = ChittaTheme.type.label, color = c.inkMuted)
            val pct = review.savedPercent
            Text(if (pct != null) "$pct%" else "None", style = ChittaTheme.type.amountLarge, color = c.ink)
            Text(if (pct != null) "of income" else "No income this month", style = ChittaTheme.type.label, color = c.inkMuted)
        }
    }

    Spacer(Modifier.height(Dimens.SectionGap))
    SectionHeader("Totals")
    HairlineRule()
    ReviewRow("Money in", review.moneyIn)
    ReviewRow("Money out", review.moneyOut)
    ReviewRow("Net", review.net, bold = true)
    Text(
        "Transfers between your own accounts, balance corrections, and money lent or borrowed are not counted. All accounts.",
        style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2),
    )

    Spacer(Modifier.height(Dimens.SectionGap))
    SectionHeader("Biggest changes")
    HairlineRule()
    when {
        previous == null -> Text(
            "Nothing was logged in ${review.previousMonth.name()}, so there is nothing to compare with.",
            style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap3),
        )
        review.up.isEmpty() && review.down.isEmpty() -> Text(
            "No category moved by ${com.mansaketh.chitta.core.money.IndianFormat.rupeesRounded(MonthReviews.MIN_CHANGE)} or more against ${review.previousMonth.name()}.",
            style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap3),
        )
        else -> {
            if (review.up.isNotEmpty()) {
                Text("Went up", style = ChittaTheme.type.labelMedium, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap3))
                review.up.forEach { ChangeRow(it, ui, review, up = true) }
            }
            if (review.down.isNotEmpty()) {
                Text("Went down", style = ChittaTheme.type.labelMedium, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap3))
                review.down.forEach { ChangeRow(it, ui, review, up = false) }
            }
        }
    }

    Spacer(Modifier.height(Dimens.SectionGap))
    SectionHeader("Budgets")
    HairlineRule()
    if (review.budgets.isEmpty()) {
        Text("No spending budget ended in ${review.month.name()}.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap3))
    } else {
        review.budgets.forEach { BudgetRow(it, review, ui) }
    }
}

@Composable
private fun MonthStepper(month: YearMonth, hasEarlier: Boolean, hasLater: Boolean, onEarlier: () -> Unit, onLater: () -> Unit) {
    val c = ChittaTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (hasEarlier) IconAction(ChittaIcons.ArrowLeft, "Earlier month", onEarlier) else Spacer(Modifier.size(Dimens.TouchTarget))
        Text(month.nameWithYear(), style = ChittaTheme.type.title, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
        if (hasLater) IconAction(ChittaIcons.ArrowLeft, "Later month", onLater, Modifier.rotate(180f)) else Spacer(Modifier.size(Dimens.TouchTarget))
    }
}

/** "₹2,300 more than September", "Same as September", or a plain note when there is nothing to compare with. */
private fun compareLine(review: MonthReview, hidden: Boolean): String {
    val prev = review.previous ?: return "Nothing to compare with"
    val name = review.previousMonth.name()
    val diff = review.moneyOut - prev.moneyOut
    if (hidden) return "Compared with $name"
    return when {
        diff.value > 0 -> "${com.mansaketh.chitta.core.money.IndianFormat.rupeesRounded(diff)} more than $name"
        diff.value < 0 -> "${com.mansaketh.chitta.core.money.IndianFormat.rupeesRounded(-diff)} less than $name"
        else -> "Same as $name"
    }
}

@Composable
private fun ReviewRow(label: String, amount: Paise, bold: Boolean = false) {
    val c = ChittaTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (bold) ChittaTheme.type.bodyMedium else ChittaTheme.type.body, color = if (bold) c.ink else c.inkMuted, modifier = Modifier.weight(1f))
        AmountText(amount, style = if (bold) ChittaTheme.type.amount else ChittaTheme.type.amountSmall)
    }
    HairlineRule()
}

@Composable
private fun ChangeRow(change: CategoryChange, ui: InsightsUi, review: MonthReview, up: Boolean) {
    val c = ChittaTheme.colors
    val delta = Paise(Math.abs(change.current.value - change.previous.value))
    val before = review.previousMonth.name()
    val pct = change.percent
    val sub = when {
        pct == null -> "Nothing in $before"
        up -> "$pct% more than $before"
        else -> "${-pct}% less than $before"
    }
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowTall).padding(vertical = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(categoryName(change.categoryId, ui.categories), style = ChittaTheme.type.body, color = c.ink, maxLines = 1)
            Text(sub, style = ChittaTheme.type.label, color = c.inkMuted)
        }
        AmountText(delta, style = ChittaTheme.type.amountRow, roundToRupee = true)
    }
    HairlineRule()
}

@Composable
private fun BudgetRow(result: BudgetResult, review: MonthReview, ui: InsightsUi) {
    val c = ChittaTheme.colors
    val calendarMonth = result.period == MonthReviews.periodOf(review.month, ui.zone)
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowTall).padding(vertical = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(result.budget.name, style = ChittaTheme.type.body, color = c.ink, maxLines = 1)
            Text(
                (if (result.hit) "Hit" else "Missed") + if (calendarMonth) "" else " · " + periodText(result.period, ui.zone),
                style = ChittaTheme.type.label, color = c.inkMuted,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            AmountText(if (result.hit) result.left else result.over, style = ChittaTheme.type.amountRow, roundToRupee = true)
            Text(if (result.hit) "left" else "over", style = ChittaTheme.type.label, color = c.inkMuted)
        }
    }
    HairlineRule()
}
