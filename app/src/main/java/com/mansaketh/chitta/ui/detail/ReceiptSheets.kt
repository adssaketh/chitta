package com.mansaketh.chitta.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.ButtonStyle
import com.mansaketh.chitta.core.design.components.ChittaButton
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.rupeeAware
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.intake.AmountSource
import com.mansaketh.chitta.core.intake.ReceiptReading
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.ui.BottomOverlay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What can be done with an entry's receipt photo. Built from the Entry detail sheets (PDF p.15); the design PDF has no receipt page. */
@Composable
fun ReceiptSheet(
    has: Boolean, onCamera: () -> Unit, onGallery: () -> Unit, onView: () -> Unit, onReadAgain: () -> Unit, onRemove: () -> Unit, onClose: () -> Unit,
) {
    val c = ChittaTheme.colors
    BottomOverlay(onClose) {
        Text("Receipt", style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        Text(
            "The photo stays on this phone and is not in backups. Its text is read on the phone, and nothing changes on the entry until you tap a suggestion.",
            style = ChittaTheme.type.body, color = c.inkMuted,
        )
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton(if (has) "Take a new photo" else "Take a photo", onCamera)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton(if (has) "Choose another photo" else "Choose from gallery", onGallery, style = ButtonStyle.Quiet)
        if (has) {
            Spacer(Modifier.height(Dimens.Gap2))
            ChittaButton("View photo", onView, style = ButtonStyle.Quiet)
            Spacer(Modifier.height(Dimens.Gap2))
            ChittaButton("Read the text again", onReadAgain, style = ButtonStyle.Quiet)
            Spacer(Modifier.height(Dimens.Gap2))
            ChittaButton("Remove photo", onRemove, style = ButtonStyle.Quiet)
        }
    }
}

/** A message about the photo (it could not be read, or nothing could be read from it). */
@Composable
fun ReceiptNoticeSheet(text: String, onClose: () -> Unit) {
    BottomOverlay(onClose) {
        Text("Receipt", style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
        Spacer(Modifier.height(Dimens.Gap3))
        Text(text, style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Close", onClose)
    }
}

/** An amount as the rest of the app shows it, masked while "hide amounts" is on. */
@Composable
private fun money(paise: Long): AnnotatedString =
    (if (ChittaTheme.hideAmounts) "₹ ••••" else IndianFormat.rupees(Paise(paise), alwaysShowPaise = true)).rupeeAware()

private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * What the receipt's text seemed to say, as suggestions beside what the entry holds now. Each row has its own Use button; once used
 * the row says it matches. Nothing is applied by opening or closing this sheet (DECISIONS D151).
 */
@Composable
fun ReceiptReadingSheet(
    reading: ReceiptReading, entry: Transaction, canEdit: Boolean, zone: ZoneId,
    onUseAmount: (Long) -> Unit, onUseDate: (LocalDate) -> Unit, onUseTitle: (String) -> Unit, onClose: () -> Unit,
) {
    val c = ChittaTheme.colors
    BottomOverlay(onClose) {
        Text("Read from the receipt", style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        Text("Check these against the photo. Printed text can be misread.", style = ChittaTheme.type.body, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap2))
        reading.amountPaise?.let { p ->
            val now = Math.abs(entry.amount)
            SuggestionRow(
                "Amount", money(p), money(now),
                matches = p == now, canUse = canEdit,
                note = if (reading.amountSource == AmountSource.LARGEST) "No total line was found. This is the largest amount on the receipt, so check it." else null,
            ) { onUseAmount(p) }
        }
        reading.date?.let { d ->
            val now = Instant.ofEpochMilli(entry.dateMs).atZone(zone).toLocalDate()
            SuggestionRow("Date", AnnotatedString(d.format(dayFormat)), AnnotatedString(now.format(dayFormat)), matches = d == now, canUse = true, note = null) { onUseDate(d) }
        }
        reading.merchant?.let { m ->
            SuggestionRow("Shop name as the title", AnnotatedString(m), AnnotatedString(entry.name.ifBlank { "No title" }), matches = m.equals(entry.name.trim(), ignoreCase = true), canUse = canEdit, note = null) { onUseTitle(m) }
        }
        Spacer(Modifier.height(Dimens.Gap3))
        ChittaButton("Done", onClose)
    }
}

@Composable
private fun SuggestionRow(label: String, suggested: AnnotatedString, now: AnnotatedString, matches: Boolean, canUse: Boolean, note: String?, onUse: () -> Unit) {
    val c = ChittaTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = Dimens.Gap2)) {
        Text(label, style = ChittaTheme.type.label, color = c.inkMuted)
        Text(suggested, style = ChittaTheme.type.bodyMedium, color = c.ink)
        if (matches) {
            Text("Same as the entry.", style = ChittaTheme.type.label, color = c.inkMuted)
        } else {
            Text(buildAnnotatedString { append("Now: "); append(now) }, style = ChittaTheme.type.label, color = c.inkMuted)
            if (note != null) Text(note, style = ChittaTheme.type.label, color = c.warningText)
            if (canUse) ChittaButton("Use this", onUse, style = ButtonStyle.Quiet)
        }
    }
    HairlineRule()
}

/**
 * The photo, full screen, drawn inside the app's own window (not a dialog) so that "hide in recents" covers it too.
 * Pinch to zoom and drag to move; it cannot be dragged off the screen.
 */
@Composable
fun ReceiptViewer(load: suspend () -> ImageBitmap?, onClose: () -> Unit) {
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(Unit) { image = load(); failed = image == null }
    BackHandler(onBack = onClose)
    Box(
        Modifier.fillMaxSize().background(Color.Black).onSizeChanged { size = it }.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                val maxX = size.width * (scale - 1f) / 2f
                val maxY = size.height * (scale - 1f) / 2f
                offset = if (scale == 1f) Offset.Zero else Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
            }
        },
    ) {
        image?.let {
            Image(
                it, contentDescription = "Receipt photo", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
        if (failed) Text("The photo could not be opened.", style = ChittaTheme.type.body, color = Color.White, modifier = Modifier.align(Alignment.Center))
        ChittaButton("Close", onClose, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Dimens.Margin).width(120.dp), style = ButtonStyle.Quiet)
    }
}
