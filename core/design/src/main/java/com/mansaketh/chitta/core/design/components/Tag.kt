package com.mansaketh.chitta.core.design.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Shapes

/**
 * A small label for a flag on a row ("Split", "Possible duplicate"): sentence case, muted text, a hairline outline with 4 dp corners.
 * Not a pill and not uppercase, so it is not a stamp and does not count toward the two-stamps-a-screen limit (D263).
 */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier) {
    val c = ChittaTheme.colors
    Text(
        text,
        style = ChittaTheme.type.labelMedium,
        color = c.inkMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.border(Dimens.Hairline, c.mark, Shapes.Tag).padding(horizontal = Dimens.TagPadH),
    )
}
