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
 * The small muted note under a group of rows. One place for its size, colour and spacing, so every screen explains itself the same way
 * (D271). Not a stamp: sentence case, no capitals.
 */
@Composable
fun FootNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted, modifier = modifier.fillMaxWidth().padding(vertical = Dimens.Gap2))
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
    titleStyle: androidx.compose.ui.text.TextStyle = ChittaTheme.type.rowTitle,
    minHeight: androidx.compose.ui.unit.Dp = if (meta == null) Dimens.RowMin else Dimens.RowTall,
    rule: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .then(if (onClick != null) Modifier.pressClickable(role = Role.Button, onClick = onClick) else Modifier)
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
                    style = titleStyle,
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
    /** The pre-v8 handle. The v8 `SheetHeader` draws its own, so a sheet that uses it passes false (11b). */
    handle: Boolean = true,
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
        if (handle) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(Dimens.SheetHandleWidth)
                    .height(Dimens.SheetHandleHeight)
                    .clip(Shapes.Stamp)
                    .background(c.mark),
            )
        }
        Column(Modifier.padding(top = if (handle) Dimens.Gap4 else 0.dp), content = content)
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
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
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
                        .combinedClickable(
                            role = Role.Tab,
                            onClick = { if (!isSelected) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick); onSelect(item.key) },
                            onLongClick = onLongPress?.let { lp -> { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); lp() } },
                        ),
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
    border(if (focused) Dimens.FocusRing else Dimens.Hairline, if (focused) focusColor else color, Shapes.Control)
