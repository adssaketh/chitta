package com.mansaketh.chitta.core.design.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.IconAction
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens

/*
 * Top bars and the count badge (v8 kit, PDF page 3 "Rows and bars" and page 13 Home).
 * Both bars own their side margins, so they sit outside the screen's padding: the icon buttons are 48 dp wide with a 24 dp icon, and the
 * bar pulls the last button in by half the difference so the icon, not the button, lines up with the 24 dp screen margin.
 */

private val ActionInset = (Dimens.TouchTarget - Dimens.IconTopBar) / 2

/**
 * The main-tab title: tab icon 26, the title in Fraunces 28 medium, up to two actions at the end. Used by Home, Ledger, Plan, Insights
 * and More (replaces `ScreenTitle` and Home's `ChittaTopBar`). 76 dp tall; no rule under it.
 */
@Composable
fun TopBarMain(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = ChittaTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TopBarMainHeight)
            .padding(start = Dimens.Margin, end = Dimens.Margin - ActionInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(Dimens.IconMainTitle))
            Spacer(Modifier.width(Dimens.InlineGap))
            Text(title, style = ChittaTheme.type.screenTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/**
 * The top bar of every screen you open from a tab: back, the title, an optional muted subtitle under it, icon actions at the end,
 * and a hairline along the bottom (replaces `ChittaTopBar` for detail screens). 64 dp tall, taller when the text grows.
 */
@Composable
fun TopBarDetail(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = ChittaTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.TopBarDetailHeight)
                .padding(horizontal = Dimens.Margin - ActionInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) IconAction(ChittaIcons.ArrowLeft, "Back", onBack) else Spacer(Modifier.width(ActionInset))
            Column(Modifier.weight(1f).padding(horizontal = Dimens.Space1)) {
                Text(
                    title,
                    style = ChittaTheme.type.title,
                    color = c.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(subtitle, style = ChittaTheme.type.label, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        HairlineRule()
    }
}

/** What a badge shows: null for nothing waiting, "1" to "99", then "99+". */
fun badgeText(count: Int): String? = when {
    count <= 0 -> null
    count > 99 -> "99+"
    else -> count.toString()
}

/** The words TalkBack reads for an icon button that carries a badge: the button's label, then the count ("more than 99" past 99). */
fun badgedLabel(label: String, count: Int): String = when {
    count <= 0 -> label
    count > 99 -> "$label, more than 99"
    else -> "$label, $count"
}

/**
 * The sindoor count badge: [Dimens.BadgeMin] least size, a [Dimens.BadgeRing] ring in the page colour so it reads on any background,
 * Plex Mono 11 medium. Draws nothing for a count of 0. It is decoration; the button it sits on carries the count in its label
 * ([badgedLabel]). Place it with [BadgedIconAction], which puts it 5 dp from the top and 3 dp from the end of the button.
 */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val text = badgeText(count) ?: return
    val c = ChittaTheme.colors
    Box(
        modifier
            .clip(CircleShape)
            .background(c.background)
            .padding(Dimens.BadgeRing)
            .clearAndSetSemantics { },
    ) {
        Box(
            Modifier
                .defaultMinSize(minWidth = Dimens.BadgeMin, minHeight = Dimens.BadgeMin)
                .clip(CircleShape)
                .background(c.sindoor)
                .padding(horizontal = Dimens.BadgePadH),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = ChittaTheme.type.badge, color = c.onSindoor, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        }
    }
}

/** A 48 dp icon button with a [CountBadge] on it. The button's label includes the count. */
@Composable
fun BadgedIconAction(
    icon: ImageVector,
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        IconAction(icon, badgedLabel(label, count), onClick)
        CountBadge(count, Modifier.align(Alignment.TopEnd).padding(top = Dimens.BadgeTop, end = Dimens.BadgeEnd))
    }
}
