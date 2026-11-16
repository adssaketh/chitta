package com.mansaketh.chitta.ui.cloud

import com.mansaketh.chitta.core.design.icons.ChittaIcons
import androidx.activity.compose.rememberLauncherForActivityResult
import com.mansaketh.chitta.core.design.kit.SwitchRow
import com.mansaketh.chitta.core.design.kit.NavRow
import com.mansaketh.chitta.core.design.kit.Section
import com.mansaketh.chitta.core.design.kit.ValueRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.mansaketh.chitta.core.design.kit.ButtonPair
import com.mansaketh.chitta.core.design.kit.Note
import com.mansaketh.chitta.ui.NoticeBar
import com.mansaketh.chitta.core.design.kit.TopBarDetail
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.CloudBackup
import com.mansaketh.chitta.core.domain.CloudBackups
import com.mansaketh.chitta.core.domain.DriveFile
import com.mansaketh.chitta.ui.Choice
import com.mansaketh.chitta.ui.ChoiceSheet
import com.mansaketh.chitta.ui.ConfirmSheet
import com.mansaketh.chitta.ui.dayAndTime
import com.mansaketh.chitta.ui.settings.closeApp
import java.io.File
import java.time.ZoneId

private fun sizeText(bytes: Long): String = if (bytes < 1024 * 1024) "${maxOf(1L, bytes / 1024)} KB" else "%.1f MB".format(bytes / (1024.0 * 1024.0))

/** One thing the person picked from the list of files in Drive. */
private sealed interface Picked {
    data class Mine(val backup: CloudBackup) : Picked
    data class Other(val file: DriveFile) : Picked
}

/**
 * Google account and Drive backup. Built from the Settings rows and sheets (there is no PDF page for it: the design PDF does not
 * draw sign-in or backup screens). One primary button: Sign in, or Back up now once signed in.
 */
@Composable
fun CloudBackupScreen(vm: CloudBackupViewModel, onBack: () -> Unit, onPaddhuFile: (File) -> Unit) {
    val ui by vm.ui.collectAsState()
    val c = ChittaTheme.colors
    val activity = LocalContext.current as? FragmentActivity
    val zone = remember { ZoneId.systemDefault() }
    var listOpen by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Picked?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.consentResult(r.resultCode == android.app.Activity.RESULT_OK, r.data)
    }
    LaunchedEffect(ui.consent) {
        val intent = ui.consent ?: return@LaunchedEffect
        vm.consentLaunched()
        consentLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
    }

    val now = System.currentTimeMillis()
    val behind = if (ui.email != null) CloudBackups.behindNote(ui.auto, ui.lastBackupMs, now, zone) else null

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TopBarDetail("Google Drive backup", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.EndInset)) {
            if (ui.busy) Text("Working. Keep this screen open.", style = ChittaTheme.type.rowMeta14, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2))

            if (ui.email == null) {
                Text(
                    "Sign in with Google to keep copies of your book in your own Drive. Only Paddhu Pusthakam can open them.",
                    style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.padding(vertical = Dimens.Gap4),
                )
                ChittaButton("Sign in with Google", { activity?.let(vm::signIn) }, enabled = activity != null && !ui.busy, modifier = Modifier.fillMaxWidth())
                Text(
                    "Backups go to a hidden folder in your Google Drive. Entries never go anywhere else, and nothing is sent until you sign in.",
                    style = ChittaTheme.type.rowMeta14, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap4),
                )
            } else {
                // PDF p.43, screen 35. The big "Last backup" figure and the restore-point list of the drawing are not in this screen of the app.
                Spacer(Modifier.height(Dimens.Gap4))
                if (ui.lastError == null && behind != null) Note(behind)
                Section("Account", first = true) {
                    ValueRow("Signed in as", ui.email!!)
                    ValueRow("Last backup", ui.lastBackupMs?.let { dayAndTime(it, zone) } ?: "None yet")
                    if (ui.lastError != null) Text(ui.lastError!!, style = ChittaTheme.type.rowMeta14, color = c.warningText, modifier = Modifier.padding(vertical = Dimens.Gap2))
                    SwitchRow("Automatic daily backup", ui.auto, { vm.setAuto(it) })
                    NavRow("Sign out", { vm.signOut() })
                }
                Text(
                    "A backup is a copy of the whole book. The newest 20 are kept. Receipt photos stay on this phone and are not in a backup. " +
                        "Signing out here does not delete anything from Drive.",
                    style = ChittaTheme.type.rowMeta14, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap4),
                )
            }
        }
        if (ui.email != null) {
            ButtonPair(
                first = { m -> ChittaButton("Backups in Drive", { if (ui.listing == null) vm.refreshListing(); listOpen = true }, kind = ButtonKind.Secondary, modifier = m) },
                second = { m -> ChittaButton("Back up now", { vm.backupNow() }, kind = ButtonKind.Secondary, enabled = !ui.busy, modifier = m) },
                modifier = Modifier.padding(horizontal = Dimens.Margin).padding(top = Dimens.Gap3, bottom = Dimens.Gap4),
            )
        }
    }
    ui.notice?.let { NoticeBar(it) { vm.dismissNotice() } }
    }

    if (listOpen) {
        val listing = ui.listing
        val choices = buildList {
            listing?.backups?.forEach { b -> add(Choice<Picked>(dayAndTime(b.createdAtMs, zone), Picked.Mine(b), sizeText(b.file.sizeBytes))) }
            listing?.others?.forEach { f -> add(Choice<Picked>("Other file: " + f.name.ifBlank { "(no name)" }, Picked.Other(f), sizeText(f.sizeBytes))) }
        }
        ChoiceSheet(
            "Backups in Drive", choices, { picked = it; listOpen = false }, { listOpen = false },
            emptyText = if (listing == null) "Reading Drive." else "No backups in Drive yet.",
        )
    }

    when (val p = picked) {
        is Picked.Mine -> ChoiceSheet(
            dayAndTime(p.backup.createdAtMs, zone),
            listOf(Choice("Restore this backup", "restore", sizeText(p.backup.file.sizeBytes), icon = ChittaIcons.Undo), Choice("Delete this backup from Drive", "delete", icon = ChittaIcons.Trash, danger = true)),
            { act -> picked = null; if (act == "restore") vm.pickBackup(p.backup) else vm.deleteBackup(p.backup) }, { picked = null },
        )
        is Picked.Other -> ChoiceSheet(
            p.file.name.ifBlank { "Other file" },
            listOf(Choice("Try to bring it over as a Paddhu book", "paddhu", "Nothing is changed until you approve the preview", icon = ChittaIcons.Download)),
            { picked = null; vm.pickOther(p.file, onPaddhuFile) }, { picked = null },
        )
        null -> Unit
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
