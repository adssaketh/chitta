package com.mansaketh.chitta.core.design.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The cells of one month's grid, 7 to a row, starting on [first] (the "First day of the week" setting): empty cells (null) before day 1 and
 * after the last day, so the list is always whole weeks.
 */
internal fun monthCells(month: YearMonth, first: DayOfWeek): List<LocalDate?> {
    val lead = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
    val days: List<LocalDate?> = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val head: List<LocalDate?> = List(lead) { null }
    val used = head.size + days.size
    val tail: List<LocalDate?> = List((7 - used % 7) % 7) { null }
    return head + days + tail
}

/** The seven weekdays in the order the grid shows them, [first] first. */
internal fun weekdayOrder(first: DayOfWeek): List<DayOfWeek> = List(7) { first.plus(it.toLong()) }

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val useFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val spokenFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)

/** The label on the date sheet's button: "Use 10 Nov". */
fun useDateLabel(date: LocalDate): String = "Use ${date.format(useFmt)}"

/**
 * The app's own date picker, shown in a sheet (v8 §4.20, D283). It replaces every system `DatePickerDialog`.
 * Today and Yesterday quick picks, the [MonthStepper], a weekday row starting on [firstDayOfWeek], a 7-column grid with 46 dp rows and 40 dp day
 * circles (today = 1.5 dp ink ring, chosen = ink fill), and a primary "Use d MMM" button. It returns the chosen date through [onUse]; a caller
 * that also picks a time keeps its own time handling. Wrap it in the sheet host with `SheetSurface(handle = false)`; it brings its own [SheetHeader].
 *
 * @param today passed in (not read from the clock) so the sheet is the same in tests and across midnight changes the caller already handles.
 */
@Composable
fun DateSheet(
    initial: LocalDate,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    onUse: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Date",
    onClose: (() -> Unit)? = null,
) {
    val c = ChittaTheme.colors
    var chosenEpoch by rememberSaveable { mutableStateOf(initial.toEpochDay()) }
    var monthIndex by rememberSaveable { mutableStateOf(initial.year * 12 + initial.monthValue - 1) }
    val chosen = LocalDate.ofEpochDay(chosenEpoch)
    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    fun pick(date: LocalDate) {
        chosenEpoch = date.toEpochDay()
        monthIndex = date.year * 12 + date.monthValue - 1
    }
    val yesterday = today.minusDays(1)
    Column(modifier.fillMaxWidth()) {
        SheetHeader(title, onClose = onClose)
        Spacer(Modifier.height(Dimens.Space3))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
            CategoryChip("Today", selected = chosen == today, onClick = { pick(today) })
            CategoryChip("Yesterday", selected = chosen == yesterday, onClick = { pick(yesterday) })
        }
        MonthStepper(
            label = month.format(monthFmt),
            onEarlier = { monthIndex -= 1 },
            onLater = { monthIndex += 1 },
        )
        Row(Modifier.fillMaxWidth()) {
            weekdayOrder(firstDayOfWeek).forEach { dow ->
                Box(Modifier.weight(1f).clearAndSetSemantics { contentDescription = dow.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) }, contentAlignment = Alignment.Center) {
                    Text(dow.getDisplayName(java.time.format.TextStyle.NARROW, Locale.ENGLISH), style = ChittaTheme.type.mono, color = c.inkMuted, textAlign = TextAlign.Center)
                }
            }
        }
        monthCells(month, firstDayOfWeek).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f).height(Dimens.DateRow))
                    } else {
                        val isChosen = day == chosen
                        val isToday = day == today
                        HitBox(
                            onClick = { pick(day) },
                            modifier = Modifier.weight(1f),
                            role = Role.RadioButton,
                            selected = isChosen,
                            shape = CircleShape,
                            hitMin = Dimens.DateRow,
                        ) {
                            Box(
                                Modifier
                                    .size(Dimens.DayCircle)
                                    .clip(CircleShape)
                                    .background(if (isChosen) c.ink else Color.Transparent)
                                    .then(if (isToday) Modifier.border(Dimens.TodayRing, c.ink, CircleShape) else Modifier)
                                    .clearAndSetSemantics {
                                        contentDescription = day.format(spokenFmt) + if (isToday) ", today" else ""
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(day.dayOfMonth.toString(), style = ChittaTheme.type.body, color = if (isChosen) c.background else c.ink)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Dimens.Space3))
        ChittaButton(useDateLabel(chosen), onClick = { onUse(chosen) }, modifier = Modifier.fillMaxWidth(), confirmHaptic = true)
    }
}
