package com.mansaketh.chitta.ui.detail

import androidx.compose.foundation.layout.Arrangement
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
import com.mansaketh.chitta.core.design.components.ButtonStyle
import com.mansaketh.chitta.core.design.components.ChittaButton
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
import com.mansaketh.chitta.ui.ValueRow
import com.mansaketh.chitta.ui.categoryName

private class LineDraft(var categoryId: String? = null, var amountText: String = "")

/**
 * Split one payment across categories (master section 8; section 12: paise, leftover paise to the first line, Save blocked
 * until the lines equal the total). Shows what is left to place while you type. Screen: PDF p.16 "Split, not balanced".
 */
@Composable
fun SplitSheet(total: Paise, categories: List<Category>, firstCategoryId: String, onSave: (List<SplitLine>) -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val lines = remember { listOf(LineDraft(firstCategoryId), LineDraft()).toMutableStateList() }
    var tick by remember { mutableStateOf(0) }          // forces a recompute when a LineDraft field changes
    var pickFor by remember { mutableStateOf<Int?>(null) }

    fun parsed(): List<SplitLine?> = lines.map { l ->
        val amount = AmountExpression.evaluate(l.amountText)
        if (l.categoryId == null || amount == null) null else SplitLine(l.categoryId!!, null, amount)
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val ready = parsed()
    val complete = ready.all { it != null }
    val valid = ready.filterNotNull()
    val remainderPaise = Splits.remainder(total, valid)
    val check: SplitCheck = if (!complete) SplitCheck.ZeroLine else Splits.check(total, valid)

    BottomOverlay(onClose) {
        Text("Split ${IndianFormat.rupees(total, alwaysShowPaise = true)}", style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        lines.forEachIndexed { i, l ->
            ValueRow("Line ${i + 1}", l.categoryId?.let { categoryName(it, categories) } ?: "Pick a category", onClick = { pickFor = i })
            ChittaField(
                value = l.amountText, onValueChange = { l.amountText = it; tick++ }, label = "Amount for line ${i + 1}", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        Spacer(Modifier.height(Dimens.Gap2))
        Text(
            when {
                check == SplitCheck.Balanced -> "The lines add up to the total."
                remainderPaise.value > 0 -> "${IndianFormat.rupees(remainderPaise, alwaysShowPaise = true)} left to place."
                remainderPaise.value < 0 -> "${IndianFormat.rupees(Paise(-remainderPaise.value), alwaysShowPaise = true)} over the total."
                else -> "Every line needs a category and an amount above zero."
            },
            style = ChittaTheme.type.body, color = if (check == SplitCheck.Balanced) c.ink else c.warningText,
        )
        Spacer(Modifier.height(Dimens.Gap3))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
            ChittaButton("Add a line", { lines.add(LineDraft()) }, style = ButtonStyle.Quiet)
            ChittaButton("Equal parts", {
                val withCat = lines.filter { it.categoryId != null }
                if (withCat.size >= 2) {
                    Splits.equalLines(total, withCat.map { it.categoryId!! }).forEachIndexed { i, sl -> withCat[i].amountText = plain(sl.amount.value) }
                    tick++
                }
            }, style = ButtonStyle.Quiet, enabled = lines.count { it.categoryId != null } >= 2)
        }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Save split", { onSave(valid) }, enabled = check == SplitCheck.Balanced, confirmHaptic = true)
    }
    pickFor?.let { i ->
        ChoiceSheet("Category for line ${i + 1}", categories.map { Choice(it.name, it.id, selected = it.id == lines[i].categoryId) },
            { id -> lines[i].categoryId = id; tick++; pickFor = null }, { pickFor = null })
    }
}

private fun plain(paise: Long): String = if (paise % 100 == 0L) (paise / 100).toString() else "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"
