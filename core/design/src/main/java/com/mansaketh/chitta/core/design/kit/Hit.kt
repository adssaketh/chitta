package com.mansaketh.chitta.core.design.kit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.mansaketh.chitta.core.design.components.pressFeedback
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Shapes

/**
 * A control that draws smaller than 48 dp (a 40 dp chip, a 36 dp filter chip, the 40 dp segmented switch) but keeps a 48 dp hit area:
 * the outer box is at least [Dimens.TouchTarget] tall and takes the tap, the inner box draws the control and shows the press feedback
 * from `Press.kt` (scale, tint, focus outline). [hitMin] lowers the 48 dp for the one place that is spec'd smaller (the segmented parts). [selected] null means a plain button; true or false makes it a selectable item.
 */
@Composable
internal fun HitBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
    selected: Boolean? = null,
    enabled: Boolean = true,
    shape: Shape = Shapes.Control,
    onClickLabel: String? = null,
    hitMin: Dp = Dimens.HitMin,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val tap = if (selected == null) {
        Modifier.clickable(interactionSource = source, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
    } else {
        Modifier.selectable(selected = selected, interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
    }
    Box(modifier.heightIn(min = hitMin).then(tap), contentAlignment = Alignment.Center) {
        Box(Modifier.pressFeedback(source, shape)) { content() }
    }
}
