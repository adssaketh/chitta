package com.mansaketh.chitta.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.rupeeAware
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Shapes

/** A 1 dp line in the rule colour. Hairlines and whitespace separate content; there are no card shadows. */
@Composable
fun HairlineRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(Dimens.Hairline).background(ChittaTheme.colors.rule))
}

/**
 * A section title: Hanken Grotesk Medium (never heavier), sentence case, an optional action on the right.
 *
 * The heading is as tall as its text, followed by [Dimens.HeadingGap] before whatever comes next (a rule
 * or the first content). Only a heading with an action keeps the 48 dp touch height, because the action
 * needs it; the action's own padding supplies the air under the text in that case.
 * Space above the heading is the screen's job: use [Section], which adds [Dimens.SectionGap].
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val hasAction = action != null && onAction != null
    Row(
        modifier
            .fillMaxWidth()
            .then(if (hasAction) Modifier.heightIn(min = Dimens.TouchTarget) else Modifier.padding(bottom = Dimens.HeadingGap)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title.rupeeAware(), style = ChittaTheme.type.sectionHeading, color = ChittaTheme.colors.ink, modifier = Modifier.semantics { heading() })
        if (hasAction) {
            Text(
                action.orEmpty(),
                style = ChittaTheme.type.link,
                color = ChittaTheme.colors.ink,
                modifier = Modifier
                    .heightIn(min = Dimens.TouchTarget)
                    .clickable(role = Role.Button, onClick = onAction!!)
                    .padding(horizontal = Dimens.InlineGap, vertical = Dimens.HeadingGap),
            )
        }
    }
}

/**
 * One section of a screen: [Dimens.SectionGap] above, the heading, [Dimens.HeadingGap], an optional rule,
 * then the content. Every screen uses this instead of building its own gaps, so the rhythm is the same
 * everywhere. Set [first] on the first section under a title or tab rule: it gets [Dimens.HeadingGap]
 * above instead of the full section gap.
 */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    rule: Boolean = false,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(top = if (first) Dimens.HeadingGap else Dimens.SectionGap)) {
        SectionHeader(title, action = action, onAction = onAction)
        if (rule) HairlineRule()
        content()
    }
}

/**
 * The one list row: [title] (and an optional [meta] line) on the left, an optional [trailing] amount or
 * value on the right, an optional [leading] icon or date. [Dimens.RowPadV] top and bottom, at least 56 dp
 * (68 dp with a meta line, [minHeight] to override). The title and meta are limited to [titleMaxLines] and
 * [metaMaxLines] with an ellipsis, so rows keep an even height, and the trailing value always has
 * [Dimens.TitleAmountGap] before it. A rule closes the row unless [rule] is false.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleMaxLines: Int = 1,
    metaMaxLines: Int = 1,
    minHeight: androidx.compose.ui.unit.Dp = if (meta == null) Dimens.RowMin else Dimens.RowTall,
    rule: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(vertical = Dimens.RowPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Box(Modifier.width(Dimens.Gap4))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title.rupeeAware(),
                    style = ChittaTheme.type.rowTitle,
                    color = ChittaTheme.colors.ink,
                    maxLines = titleMaxLines,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (meta != null) {
                    Text(
                        meta.rupeeAware(),
                        style = ChittaTheme.type.rowMeta,
                        color = ChittaTheme.colors.inkMuted,
                        maxLines = metaMaxLines,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Box(Modifier.width(Dimens.TitleAmountGap))
                trailing()
            }
        }
        if (rule) HairlineRule()
    }
}

/**
 * The look of a bottom sheet: the surface colour, 28 dp top corners and a handle. Behaviour (drag,
 * scrim, springs) is added in the screen that hosts it; this is the frame the content sits in.
 */
@Composable
fun SheetSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ChittaTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shapes.Sheet)
            .background(c.surface)
            .padding(horizontal = Dimens.Margin)
            .padding(top = Dimens.Gap3, bottom = Dimens.Gap6),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(32.dp)
                .height(4.dp)
                .clip(Shapes.Stamp)
                .background(c.mark),
        )
        Column(Modifier.padding(top = Dimens.Gap4), content = content)
    }
}

/**
 * A main-screen title with its bottom-bar icon beside it. The icon is decoration: the title carries the meaning.
 * Every main tab uses this with no extra vertical padding: the row is [Dimens.TitleBarMin] tall, so the
 * titles sit on the same line on every tab. Left and right margins belong to the screen.
 */
@Composable
fun ScreenTitle(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = Dimens.TitleBarMin).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ChittaTheme.colors.ink, modifier = Modifier.size(Dimens.IconSize))
        Box(Modifier.width(Dimens.InlineGap))
        Text(title, style = ChittaTheme.type.screenTitle, color = ChittaTheme.colors.ink)
    }
}

/** Top bar: optional back arrow, a title, actions on the right. Plain, no elevation. */
@Composable
fun ChittaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    titleIcon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ButtonHeight)
            .padding(horizontal = Dimens.Gap2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconAction(ChittaIcons.ArrowLeft, "Back", onBack)
        } else {
            Box(Modifier.width(Dimens.Gap4))
        }
        if (titleIcon != null) {
            Icon(titleIcon, contentDescription = null, tint = ChittaTheme.colors.ink, modifier = Modifier.size(Dimens.IconSize))
            Box(Modifier.width(Dimens.Gap2))
        }
        Text(
            title,
            style = ChittaTheme.type.title,
            color = ChittaTheme.colors.ink,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

/** The eye icon on Home: one tap masks every amount on every screen. */
@Composable
fun HideAmountsAction(hidden: Boolean, onToggle: () -> Unit) {
    IconAction(
        icon = if (hidden) ChittaIcons.EyeSlash else ChittaIcons.Eye,
        description = if (hidden) "Show amounts" else "Hide amounts",
        onClick = onToggle,
    )
}

/** The ink card icon: opens the card panel. Shown when a card bill is due soon. */
@Composable
fun CardAction(onClick: () -> Unit) {
    IconAction(ChittaIcons.CreditCard, "Cards. A bill is due soon.", onClick)
}

/** The five main destinations (master section 6). */
enum class NavDestination(val label: String, val icon: () -> ImageVector) {
    Home("Home", { ChittaIcons.Home }),
    Ledger("Ledger", { ChittaIcons.Ledger }),
    Plan("Plan", { ChittaIcons.Plan }),
    Insights("Insights", { ChittaIcons.Insights }),
    More("More", { ChittaIcons.More }),
}

/** One tab of the bottom bar. [key] is what the app uses to tell tabs apart. */
data class NavItem(val key: String, val label: String, val icon: ImageVector)

/**
 * Bottom navigation. The selected tab is marked by ink and a 2 dp rule above it, not a pill:
 * a pill behind every label is one of the banned patterns.
 */
@Composable
fun ChittaBottomNav(
    selected: NavDestination,
    onSelect: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChittaBottomNav(
        items = NavDestination.entries.map { NavItem(it.name, it.label, it.icon()) },
        selectedKey = selected.name,
        onSelect = { key -> onSelect(NavDestination.valueOf(key)) },
        modifier = modifier,
    )
}

/**
 * The bar with any tabs: the app swaps tabs by long-pressing it (master section 6), so the items are a list.
 * [onLongPress] fires on a long press of any tab.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChittaBottomNav(
    items: List<NavItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val c = ChittaTheme.colors
    Column(modifier.fillMaxWidth().background(c.background)) {
        HairlineRule()
        Row(Modifier.fillMaxWidth()) {
            items.forEach { item ->
                val isSelected = item.key == selectedKey
                val tint = if (isSelected) c.ink else c.inkMuted
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = Dimens.NavBarMin)
                        .semantics { selected = isSelected }
                        .combinedClickable(role = Role.Tab, onClick = { onSelect(item.key) }, onLongClick = onLongPress),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(Dimens.MarkerWidth).height(Dimens.MarkerHeight).background(if (isSelected) c.ink else c.background))
                    Box(Modifier.height(Dimens.Gap2))
                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(Dimens.IconSize))
                    Box(Modifier.height(Dimens.Gap1))
                    Text(item.label, style = if (isSelected) ChittaTheme.type.labelMedium else ChittaTheme.type.label, color = tint, textAlign = TextAlign.Center)
                    Box(Modifier.height(Dimens.Gap2))
                }
            }
        }
    }
}

/**
 * The Undo bar. In Paper it is an ink bar with paper text; in Ink it turns into a light bar with dark
 * text (dark PDF, screen 05), which falls out of using ink and background colours.
 */
@Composable
fun UndoBar(
    message: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "Undo",
) {
    val c = ChittaTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ButtonHeight)
            .clip(Shapes.Control)
            .background(c.ink)
            .padding(start = Dimens.Gap4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message.rupeeAware(), style = ChittaTheme.type.body, color = c.background, modifier = Modifier.weight(1f).padding(vertical = Dimens.Gap3))
        Text(
            actionLabel,
            style = ChittaTheme.type.bodyMedium,
            color = c.background,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .heightIn(min = Dimens.TouchTarget)
                .clickable(role = Role.Button, onClick = onUndo)
                .padding(horizontal = Dimens.Gap4, vertical = Dimens.Gap3),
        )
    }
}

/** First use: one plain sentence and one action. Never fake entries, never an illustration. */
@Composable
fun EmptyState(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = Dimens.HeadingGap, bottom = Dimens.SectionGap), verticalArrangement = Arrangement.spacedBy(Dimens.Gap4)) {
        Text(message, style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted)
        ChittaButton(actionLabel, onAction, style = ButtonStyle.Quiet)
    }
}

/** Error: what failed, whether anything was saved, how to retry (master section 13). */
@Composable
fun ErrorState(
    whatFailed: String,
    savedStatus: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "Try again",
) {
    Column(modifier.fillMaxWidth().padding(top = Dimens.HeadingGap, bottom = Dimens.SectionGap), verticalArrangement = Arrangement.spacedBy(Dimens.Gap3)) {
        Text(whatFailed, style = ChittaTheme.type.bodyMedium, color = ChittaTheme.colors.warningText)
        Text(savedStatus, style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted)
        Box(Modifier.height(Dimens.Gap1))
        ChittaButton(retryLabel, onRetry, style = ButtonStyle.Quiet)
    }
}

/** Loading: quiet placeholder lines in the rule colour. No shimmer and no fake progress. */
@Composable
fun LoadingPlaceholder(modifier: Modifier = Modifier, lines: Int = 4) {
    Column(modifier.fillMaxWidth().semantics { }, verticalArrangement = Arrangement.spacedBy(Dimens.Gap4)) {
        repeat(lines) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i % 2 == 0) 1f else 0.7f)
                    .height(Dimens.Gap4)
                    .clip(Shapes.Stamp)
                    .background(ChittaTheme.colors.rule),
            )
        }
    }
}

/** A bordered text surface, used by fields. */
internal fun Modifier.fieldBorder(focused: Boolean, color: androidx.compose.ui.graphics.Color, focusColor: androidx.compose.ui.graphics.Color) =
    border(if (focused) 2.dp else 1.dp, if (focused) focusColor else color, Shapes.Control)
