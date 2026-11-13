package com.mansaketh.chitta.core.design.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Shapes

enum class StampTone {
    /** Status that is just information: PAID, UPCOMING, SKIPPED, LENT, COLLECTED, STALE, ESTIMATE. */
    Neutral,

    /** The one thing that needs attention: OVERDUE, OVER PACE. Red only here. */
    Attention,

    /** Money in. */
    Income,
}

/**
 * A status stamp: a small outlined, letter-spaced mono label, not a rounded pill. Stamps are the only
 * uppercase text in the app, and a screen carries at most two (master sections 3 and 4).
 * The text is upper-cased here, so callers pass plain words like "Overdue".
 */
@Composable
fun Stamp(
    text: String,
    modifier: Modifier = Modifier,
    tone: StampTone = StampTone.Neutral,
) {
    val colors = ChittaTheme.colors
    val tint = when (tone) {
        StampTone.Neutral -> colors.inkMuted
        StampTone.Attention -> colors.warningText
        StampTone.Income -> colors.green
    }
    Text(
        text = text.uppercase(),
        style = ChittaTheme.type.stamp,
        color = tint,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .semantics { contentDescription = text }
            .border(1.dp, tint, Shapes.Stamp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
