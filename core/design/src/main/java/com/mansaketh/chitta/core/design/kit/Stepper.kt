package com.mansaketh.chitta.core.design.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.mansaketh.chitta.core.design.components.pressClickable
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.icons.KitIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Month stepper (v8 §4.15): two 48 dp arrow buttons, "Earlier month" and "Later month", with the [label] (Fraunces 19 medium) centred between.
 * The label is a polite live region, so TalkBack announces the new month when it changes. An arrow with nothing beyond it is dimmed and does nothing.
 * Replaces Plan's calendar "Earlier / Later", Review's `MonthStepper` and Who spent's `MonthRow`.
 */
@Composable
fun MonthStepper(
    label: String,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
    canEarlier: Boolean = true,
    canLater: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepArrow(KitIcons.CaretLeft, "Earlier month", canEarlier, onEarlier)
        Text(
            label,
            style = ChittaTheme.type.stepperLabel,
            color = ChittaTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        StepArrow(ChittaIcons.CaretRight, "Later month", canLater, onLater)
    }
}

@Composable
private fun StepArrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Dimens.TouchTarget)
            .clip(CircleShape)
            .alpha(if (enabled) 1f else 0.4f)
            .pressClickable(enabled = enabled, role = Role.Button, onClickLabel = description, shape = CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = ChittaTheme.colors.ink, modifier = Modifier.size(Dimens.IconCaret))
    }
}
