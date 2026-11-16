package com.mansaketh.chitta.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mansaketh.chitta.core.design.kit.DateSheet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** The first day of the week from Customise. Provided once in [AppRoot]; the date sheet reads it. */
val LocalFirstDay = compositionLocalOf { DayOfWeek.MONDAY }

/**
 * One date sheet for the whole app (v8 §4.20, D283), shown by [DatePickHost]. [pickDate] only posts a request, so the many
 * "tap a date row" call sites stay one line each. Only one request is open at a time.
 */
object DatePick {
    class Request(val initial: LocalDate, val zone: ZoneId, val onUse: (LocalDate) -> Unit, val onCancel: () -> Unit)
    var request by mutableStateOf<Request?>(null)
}

/** Draws the posted date sheet over everything. Put it last in the root box. */
@Composable
fun DatePickHost() {
    val r = DatePick.request ?: return
    val close = { DatePick.request = null; r.onCancel() }
    BottomOverlay(close, handle = false) {
        DateSheet(
            initial = r.initial,
            today = LocalDate.now(r.zone),
            firstDayOfWeek = LocalFirstDay.current,
            onUse = { d -> DatePick.request = null; r.onUse(d) },
            onClose = close,
        )
    }
}
