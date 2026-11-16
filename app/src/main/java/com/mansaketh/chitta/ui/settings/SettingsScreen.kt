package com.mansaketh.chitta.ui.settings

import com.mansaketh.chitta.core.design.kit.SheetHeader
import android.os.Handler
import com.mansaketh.chitta.core.design.kit.SwitchRow
import com.mansaketh.chitta.core.design.kit.NavRow
import com.mansaketh.chitta.core.design.kit.ExpandGroup
import com.mansaketh.chitta.core.design.kit.ValueRow
import android.os.Looper
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ChittaButton
import com.mansaketh.chitta.core.design.kit.TopBarDetail
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.components.FootNote
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.ExportRange
import com.mansaketh.chitta.core.domain.LockTimeout
import com.mansaketh.chitta.core.domain.RestorePoint
import com.mansaketh.chitta.core.domain.RestorePoints
import com.mansaketh.chitta.ui.Choice
import com.mansaketh.chitta.ui.ChoiceSheet
import com.mansaketh.chitta.ui.ConfirmSheet
import com.mansaketh.chitta.ui.dayAndTime
import com.mansaketh.chitta.ui.lock.BiometricGate
import java.time.ZoneId

/** Closes the app so the staged restore is applied the next time it opens (the swap happens before the book is opened; see ChittaApp). */
internal fun closeApp(activity: FragmentActivity?) {
    activity?.finishAffinity()
    Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 400)
}

private fun onOff(v: Boolean) = if (v) "On" else "Off"

private fun themeLabel(k: String) = when (k) { "paper" -> "Paper (light)"; "ink" -> "Ink (dark)"; else -> "Follow the phone" }

/**
 * Settings, as far as this phase goes: Security, Backup and restore (with Google Drive backup, Phase 9a), Import and export, Data.
 * Cross-device sync and the household come later in Phase 9 (DECISIONS D66, D172).
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val waiting by vm.pendingIntake.collectAsState()
    val ui by vm.ui.collectAsState()
    val theme by vm.themeMode.collectAsState()
    val vibrate by vm.vibration.collectAsState()
    val animations by vm.animations.collectAsState()
    val c = ChittaTheme.colors
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val zone = remember { ZoneId.systemDefault() }
    var sheet by remember { mutableStateOf<String?>(null) }
    var csvAccount by remember { mutableStateOf<String?>(null) }
    var csvRange by remember { mutableStateOf(ExportRange.All) }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) vm.exportBackup(uri) }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.pickBackup(uri) }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) vm.exportCsv(uri, csvAccount, csvRange) }

    fun toggleLock() {
        val turningOn = !ui.lockEnabled
        if (activity == null) return
        if (turningOn && !BiometricGate.canAuthenticate(context)) {
            vm.tell("Set a screen lock or fingerprint in Android settings first, then turn this on.")
            return
        }
        BiometricGate.prompt(activity, if (turningOn) "Turn on app lock" else "Turn off app lock", null, { vm.setLockEnabled(turningOn) })
    }

    Column(Modifier.fillMaxSize()) {
        TopBarDetail("Settings", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.EndInset)) {
            if (ui.notice != null) {
                Text(ui.notice!!, style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.padding(vertical = Dimens.Gap2))
                ChittaButton("Dismiss", { vm.dismissNotice() }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Dimens.Gap4))
            }
            if (ui.busy) Text("Working. Keep this screen open.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2))

            ExpandGroup("Look and feel", summary = "Theme, vibration, animations", icon = ChittaIcons.Gear, initiallyExpanded = true) {
                ValueRow("Theme", themeLabel(theme), onClick = { sheet = "theme" })
                ValueRow("Vibration strength", com.mansaketh.chitta.ui.VibrationLevel.of(vibrate).label, onClick = { sheet = "vibration" })
                SwitchRow("Animations", animations, { vm.setAnimations(it) })
            }

            ExpandGroup("Security", summary = "App lock ${onOff(ui.lockEnabled).lowercase()}", icon = ChittaIcons.Lock) {
                ValueRow("App lock", onOff(ui.lockEnabled), onClick = ::toggleLock)
                if (ui.lockEnabled) ValueRow("Lock after leaving", ui.timeout.label, onClick = { sheet = "timeout" })
                ValueRow("Hide in recent apps", onOff(ui.hideInRecents), onClick = { vm.setHideInRecents(!ui.hideInRecents) })
                FootNote("The lock uses your fingerprint or face, or your phone's PIN, pattern or password. Hiding in recent apps also stops screenshots.")
            }

            ExpandGroup("Household", summary = "Share chosen entries with others", icon = ChittaIcons.Users) {
                NavRow("Household", { onOpen("household") })
                FootNote("Share chosen entries with up to 8 people, each signed in with their own Google account. Your book stays private unless you share.")
            }

            ExpandGroup("Backup and restore", summary = "Drive, sync, restore points, backup files", icon = ChittaIcons.Download) {
                NavRow("Google Drive backup", { onOpen("cloud-backup") })
                NavRow("Sync between phones", { onOpen("sync") })
                ValueRow("Restore points", "${ui.points.size}", onClick = { vm.refreshPoints(); onOpen("restore-points") })
                NavRow("Make a restore point now", { vm.makePoint() })
                NavRow("Save a backup file", { saveBackup.launch(RestorePoints.exportName(System.currentTimeMillis(), zone)) })
                NavRow("Bring back a backup file", { openBackup.launch(arrayOf("*/*")) })
                FootNote(
                    "A restore point is a copy of your book kept on this phone: one a day, and one before every restore, CSV import and delete. " +
                        "Receipt photos stay on this phone and are not in restore points or backup files.",
                )
            }

            ExpandGroup("Import and export", summary = if (waiting > 0) "$waiting bank messages waiting" else "Rules, automation, CSV, your Paddhu book", icon = ChittaIcons.Transfer) {
                NavRow("Rules", { onOpen("rules") })
                NavRow("Automation", { onOpen("automation") })
                ValueRow("Bank messages and UPI", if (waiting > 0) "$waiting waiting" else "", onClick = { onOpen("intake") })
                NavRow("Import entries from CSV", { onOpen("csv-import") })
                NavRow("Export entries to CSV", { sheet = "csv-account" })
                NavRow("Bring over your Paddhu book", { onOpen("import") })
            }

            val errorCount = com.mansaketh.chitta.diagnostics.Diagnostics.count()
            ExpandGroup("Help", summary = if (errorCount == 0) "Diagnostics: no errors" else "Diagnostics: $errorCount recorded", icon = ChittaIcons.Info) {
                ValueRow("Diagnostics", if (errorCount == 0) "No errors" else "$errorCount recorded", onClick = { sheet = "diagnostics" })
            }

            ExpandGroup("Data", summary = "Delete everything", icon = ChittaIcons.Trash) {
                NavRow("Delete everything", { sheet = "delete" })
                FootNote("This empties the book on this phone. A restore point is made first, so you can bring it back from Restore points.")
            }
        }
    }

    when (sheet) {
        "vibration" -> ChoiceSheet(
            "Vibration strength",
            com.mansaketh.chitta.ui.VibrationLevel.entries.map { Choice(it.label, it.key, selected = it.key == vibrate) },
            { vm.setVibration(it); sheet = null }, { sheet = null },
        )
        "theme" -> ChoiceSheet(
            "Theme",
            listOf("system" to "Follow the phone", "paper" to "Paper (light)", "ink" to "Ink (dark)").map { (k, label) -> Choice(label, k, selected = k == theme) },
            { vm.setThemeMode(it); sheet = null }, { sheet = null },
        )
        "diagnostics" -> com.mansaketh.chitta.ui.BottomOverlay({ sheet = null }, handle = false) {
            var report by remember { mutableStateOf(com.mansaketh.chitta.diagnostics.Diagnostics.report(context)) }
            SheetHeader("Diagnostics")
            Spacer(Modifier.height(Dimens.Gap2))
            Text(
                "Errors the app hit on this phone, with the phone and app version. Nothing is sent anywhere. Read it, then share it only if you are happy to.",
                style = ChittaTheme.type.label, color = c.inkMuted,
            )
            Spacer(Modifier.height(Dimens.Gap3))
            Column(Modifier.heightIn(max = com.mansaketh.chitta.ui.sheetMax(0.35f)).verticalScroll(rememberScrollState())) {
                Text(report.takeLast(2_500), style = ChittaTheme.type.label, color = c.ink)
            }
            Spacer(Modifier.height(Dimens.Gap4))
            ChittaButton("Share", {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(android.content.Intent.EXTRA_SUBJECT, "Paddhu Pusthakam diagnostics"); putExtra(android.content.Intent.EXTRA_TEXT, report)
                }
                context.startActivity(android.content.Intent.createChooser(send, "Share diagnostics"))
            }, confirmHaptic = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Dimens.InlineGap))
            ChittaButton("Clear", { com.mansaketh.chitta.diagnostics.Diagnostics.clear(); report = com.mansaketh.chitta.diagnostics.Diagnostics.report(context) }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
        }
        "timeout" -> ChoiceSheet("Lock after leaving", LockTimeout.entries.map { Choice(it.label, it, selected = it == ui.timeout) }, { vm.setTimeout(it); sheet = null }, { sheet = null })
        "csv-account" -> ChoiceSheet(
            "Export which accounts?",
            listOf(Choice("All accounts", "")) + ui.accounts.filter { !it.archived }.map { Choice(it.name, it.id) },
            { csvAccount = it.ifEmpty { null }; sheet = "csv-range" }, { sheet = null },
        )
        "csv-range" -> ChoiceSheet(
            "Export which dates?", ExportRange.entries.map { Choice(it.label, it) },
            { csvRange = it; sheet = null; saveCsv.launch("paddhu-pusthakam-entries-" + java.time.LocalDate.now(zone) + ".csv") }, { sheet = null },
        )
        "delete" -> ConfirmSheet(
            "Delete everything?",
            listOf("This empties your accounts, categories, entries, budgets, goals and loans on this phone.", "A restore point is made first. To undo this, open Restore points and restore it. Receipt photos are deleted for good: a restore point does not hold them."),
            "Delete everything", { sheet = null; vm.deleteAll { } }, { sheet = null },
        )
    }

    ui.candidate?.let { cand ->
        ConfirmSheet(
            "Replace your book with this backup?",
            listOf(
                "The backup has ${cand.facts.accounts} accounts and ${cand.facts.entries} entries.",
                "Your current book is saved as a restore point first. Paddhu Pusthakam then closes; open it again to see the restored book.",
            ),
            "Restore and close", { vm.confirmCandidate { closeApp(activity) } }, { vm.clearCandidate() },
        )
    }
}

/** The restore points on this phone, newest first. Tap one to restore or delete it. */
@Composable
fun RestorePointsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val c = ChittaTheme.colors
    val activity = LocalContext.current as? FragmentActivity
    val zone = remember { ZoneId.systemDefault() }
    var picked by remember { mutableStateOf<RestorePoint?>(null) }
    var confirm by remember { mutableStateOf<RestorePoint?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBarDetail("Restore points", onBack = onBack)
        if (ui.notice != null) {
            Text(ui.notice!!, style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.padding(horizontal = Dimens.Margin, vertical = Dimens.Gap2))
        }
        if (ui.points.isEmpty()) {
            Text("No restore points yet. One is made each day you open Paddhu Pusthakam, or make one in Settings.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(Dimens.Margin))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Dimens.EndInset)) {
                items(ui.points, key = { it.fileName }) { p ->
                    ValueRow(dayAndTime(p.createdAtMs, zone), p.kind.label + " · " + sizeText(p.sizeBytes), onClick = { picked = p })
                }
            }
        }
    }

    picked?.let { p ->
        ChoiceSheet(
            dayAndTime(p.createdAtMs, zone), listOf(Choice("Restore this point", "restore", p.kind.label), Choice("Delete this point", "delete")),
            { act -> picked = null; if (act == "restore") confirm = p else vm.deletePoint(p) }, { picked = null },
        )
    }
    confirm?.let { p ->
        ConfirmSheet(
            "Restore this point?",
            listOf(
                "Your book goes back to how it was on ${dayAndTime(p.createdAtMs, zone)}. Anything added since is not in it.",
                "Your current book is saved as a restore point first. Paddhu Pusthakam then closes; open it again to see the restored book.",
            ),
            "Restore and close", { confirm = null; vm.restorePoint(p) { closeApp(activity) } }, { confirm = null },
        )
    }
}

private fun sizeText(bytes: Long): String = if (bytes < 1024 * 1024) "${maxOf(1L, bytes / 1024)} KB" else "%.1f MB".format(bytes / (1024.0 * 1024.0))
