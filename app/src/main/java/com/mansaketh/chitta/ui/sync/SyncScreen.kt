package com.mansaketh.chitta.ui.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mansaketh.chitta.core.design.components.ButtonStyle
import com.mansaketh.chitta.core.design.components.ChittaButton
import com.mansaketh.chitta.core.design.components.ChittaTopBar
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.SectionHeader
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.ui.Choice
import com.mansaketh.chitta.ui.ChoiceSheet
import com.mansaketh.chitta.ui.ValueRow
import com.mansaketh.chitta.ui.dayAndTime
import java.time.ZoneId

/**
 * Sync between this person's phones. Built from the Settings rows (there is no PDF page for it): one button, a status line, a few rows.
 * No on/off switch: sync runs whenever Google is signed in (the person's choice, 15 Nov 2026).
 */
@Composable
fun SyncScreen(vm: SyncViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val c = ChittaTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    var choiceOpen by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize()) {
        ChittaTopBar("Sync between phones", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.EndInset)) {
            if (ui.notice != null) {
                Text(ui.notice!!, style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.padding(vertical = Dimens.Gap2))
                ChittaButton("Dismiss", { vm.dismissNotice() }, style = ButtonStyle.Quiet)
                Spacer(Modifier.height(Dimens.Gap4))
            }
            Spacer(Modifier.height(Dimens.Gap4))
            if (!ui.signedIn) {
                Text(
                    "Sign in with Google (Settings, Google Drive backup) and your book will follow you to your other phones.",
                    style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.padding(bottom = Dimens.Gap4),
                )
            } else {
                ChittaButton(if (ui.running) "Syncing" else "Sync now", { vm.syncNow() }, enabled = !ui.running && !ui.needsChoice)
                Spacer(Modifier.height(Dimens.SectionGap))
                SectionHeader("Status")
                HairlineRule()
                ValueRow("Status", ui.line)
                ValueRow("Last synced", ui.lastSyncMs?.let { dayAndTime(it, zone) } ?: "Never")
                ValueRow("Waiting to send", ui.waiting.toString())
                if (ui.error != null) Text(ui.error!!, style = ChittaTheme.type.label, color = c.warningText, modifier = Modifier.padding(vertical = Dimens.Gap2))
                if (ui.needsChoice) ValueRow("Choose how to sync", "", onClick = { choiceOpen = true })
            }
            Text(
                "Your whole book, apart from things that belong to one phone (lock, notifications, backup switches and household sharing), is kept in your own " +
                    "private space in the cloud. Each phone sends its changes a few seconds after you make them, when you open the app, and about once an hour. " +
                    "If two phones change the same thing, the newer change wins. Receipt photos stay on the phone they were taken on. " +
                    "Restoring a backup or deleting everything on one phone makes your other phones follow it.",
                style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap4),
            )
        }
    }

    if (ui.needsChoice && choiceOpen) {
        ChoiceSheet(
            "This phone has not synced for over 180 days",
            listOf(
                Choice("Merge", true, "Keep what is here and what is in the cloud. Things you deleted elsewhere may come back."),
                Choice("Replace this phone with the cloud", false, "A restore point is made first. Changes made only on this phone are dropped."),
            ),
            { merge -> choiceOpen = false; vm.resolve(merge) }, { choiceOpen = false },
        )
    }
}
