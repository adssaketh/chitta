package com.mansaketh.chitta.ui.detail

import com.mansaketh.chitta.core.design.kit.SheetHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.AmountText
import com.mansaketh.chitta.core.design.components.pressClickable
import com.mansaketh.chitta.core.design.kit.TopBarDetail
import com.mansaketh.chitta.ui.OverlayState
import com.mansaketh.chitta.ui.categoryTint
import com.mansaketh.chitta.core.design.kit.ValueRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ChittaButton
import com.mansaketh.chitta.core.design.components.ChittaField
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.AmountExpression
import com.mansaketh.chitta.core.domain.SplitCheck
import com.mansaketh.chitta.core.domain.SplitLine
import com.mansaketh.chitta.core.domain.Splits
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.ui.BottomOverlay
import com.mansaketh.chitta.ui.Choice
import com.mansaketh.chitta.ui.ChoiceSheet
import com.mansaketh.chitta.ui.categoryName

private class LineDraft(var categoryId: String? = null, var amountText: String = "", var subCategoryId: String? = null)

/**
 * Split one payment across categories (master section 8; section 12: paise, leftover paise to the first line, Save blocked
 * until the lines equal the total). Shows what is left to place while you type. Screen: PDF p.16 "Split, not balanced".
 */
@Composable
fun SplitSheet(
    total: Paise, categories: List<Category>, firstCategoryId: String, onSave: (List<SplitLine>) -> Unit, onClose: () -> Unit,
    initial: List<SplitLine> = emptyList(), title: String? = null,
) {
    val c = ChittaTheme.colors
    // An already split payment opens with its current lines; a new split starts with two.
    val lines = remember { (if (initial.size >= 2) initial.map { LineDraft(it.categoryId, plain(it.amount.value), it.subCategoryId) } else listOf(LineDraft(firstCategoryId), LineDraft())).toMutableStateList() }
    var tick by remember { mutableStateOf(0) }          // forces a recompute when a LineDraft field changes
    var pickFor by remember { mutableStateOf<Int?>(null) }

    fun parsed(): List<SplitLine?> = lines.map { l ->
        val amount = AmountExpression.evaluate(l.amountText)
        if (l.categoryId == null || amount == null) null else SplitLine(l.categoryId!!, l.subCategoryId, amount)
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val ready = parsed()
    val complete = ready.all { it != null }
    val valid = ready.filterNotNull()
    val remainderPaise = Splits.remainder(total, valid)
    val check: SplitCheck = if (!complete) SplitCheck.ZeroLine else Splits.check(total, valid)
    val balanced = check == SplitCheck.Balanced
    val left = remainderPaise.value

    // Full screen (PDF p.21): this is a page with a back arrow, not a sheet.
    androidx.activity.compose.BackHandler(onBack = onClose)
    DisposableEffect(Unit) { OverlayState.open += 1; onDispose { OverlayState.open -= 1 } }
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().imePadding()
            .pressClickable(role = null) { },
    ) {
        TopBarDetail("Split", onBack = onClose, subtitle = (title?.takeIf { it.isNotBlank() }?.let { "$it \u00B7 " } ?: "") + IndianFormat.rupees(total))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.Margin, vertical = Dimens.Gap4)) {
                Text(
                    when { balanced -> "Everything is assigned"; left < 0 -> "Over the total by"; else -> "Still to assign before you can save" },
                    style = ChittaTheme.type.body, color = c.inkMuted,
                )
                Spacer(Modifier.height(Dimens.Gap1))
                AmountText(Paise(Math.abs(left)), style = ChittaTheme.type.amountHero, color = if (balanced) c.ink else c.warningText, alwaysShowPaise = left % 100L != 0L)
            }
            HorizontalDivider(color = c.rule, thickness = Dimens.Hairline)
            lines.forEachIndexed { i, l ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.Margin, vertical = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(l.categoryId?.let { c.categoryTint(it) } ?: c.rule))
                    Spacer(Modifier.width(Dimens.Gap3))
                    Text(
                        l.categoryId?.let { categoryName(it, categories) } ?: "Pick a category", style = ChittaTheme.type.body,
                        color = if (l.categoryId == null) c.inkMuted else c.ink,
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTarget).pressClickable(role = Role.Button, onClickLabel = "Change category") { pickFor = i }.wrapContentHeight(Alignment.CenterVertically),
                    )
                    ChittaField(
                        value = l.amountText, onValueChange = { l.amountText = it; tick++ }, label = null, description = "Amount for line ${i + 1}",
                        modifier = Modifier.width(150.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), minHeight = 48.dp,
                    )
                }
                HorizontalDivider(color = c.rule, thickness = Dimens.Hairline)
            }
            Spacer(Modifier.height(Dimens.Gap3))
            Column(Modifier.padding(horizontal = Dimens.Margin)) {
                com.mansaketh.chitta.core.design.kit.ButtonPair(
                    first = { m -> ChittaButton("Add a line", { lines.add(LineDraft()) }, kind = ButtonKind.Secondary, modifier = m) },
                    second = { m ->
                        ChittaButton("Equal parts", {
                            val withCat = lines.filter { it.categoryId != null }
                            if (withCat.size >= 2) {
                                Splits.equalLines(total, withCat.map { it.categoryId!! }).forEachIndexed { i, sl -> withCat[i].amountText = plain(sl.amount.value) }
                                tick++
                            }
                        }, kind = ButtonKind.Secondary, enabled = lines.count { it.categoryId != null } >= 2, modifier = m)
                    },
                )
                val first = lines.firstOrNull()
                if (!balanced && left > 0 && first?.categoryId != null) {
                    Spacer(Modifier.height(Dimens.Gap3))
                    val shape = com.mansaketh.chitta.core.design.tokens.Shapes.Control
                    Row(
                        Modifier.fillMaxWidth().clip(shape).border(Dimens.Hairline, c.rule, shape).padding(start = Dimens.Gap4, end = Dimens.Gap3, top = Dimens.Gap3, bottom = Dimens.Gap3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Add the ${IndianFormat.rupees(remainderPaise)} to ${categoryName(first.categoryId!!, categories)}?", style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                        ChittaButton("Add", {
                            val now = AmountExpression.evaluate(first.amountText)?.value ?: 0L
                            first.amountText = plain(now + left); tick++
                        }, kind = ButtonKind.Secondary, small = true)
                    }
                }
            }
            Spacer(Modifier.height(Dimens.Gap4))
        }
        Column(Modifier.padding(horizontal = Dimens.Margin).padding(bottom = Dimens.Gap3, top = Dimens.Gap2)) {
            if (balanced) {
                ChittaButton("Save split", { onSave(valid) }, confirmHaptic = true, modifier = Modifier.fillMaxWidth())
            } else {
                // Dashed and flat until the lines add up (PDF p.21): it reads as not yet available, and says what is missing.
                val shape = com.mansaketh.chitta.core.design.tokens.Shapes.Control
                val dash = c.inkMuted
                Box(
                    Modifier.fillMaxWidth().height(Dimens.ButtonHeight).clip(shape).drawBehind {
                        drawRoundRect(dash, cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (left > 0) "Save \u00B7 ${IndianFormat.rupees(remainderPaise)} left" else if (left < 0) "Save \u00B7 ${IndianFormat.rupees(Paise(-left))} over" else "Save \u00B7 every line needs a category and amount",
                        style = ChittaTheme.type.body, color = c.inkMuted,
                    )
                }
            }
        }
    }
    pickFor?.let { i ->
        ChoiceSheet("Category for line ${i + 1}", categories.map { Choice(it.name, it.id, selected = it.id == lines[i].categoryId) },
            { id -> if (lines[i].categoryId != id) lines[i].subCategoryId = null; lines[i].categoryId = id; tick++; pickFor = null }, { pickFor = null })
    }
}

private fun plain(paise: Long): String = if (paise % 100 == 0L) (paise / 100).toString() else "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"
