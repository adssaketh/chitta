package com.mansaketh.chitta.ui.automation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.mansaketh.chitta.core.design.components.ChittaTopBar
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.SectionHeader
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.ui.ConfirmSheet
import com.mansaketh.chitta.ui.TextSheet
import com.mansaketh.chitta.ui.ValueRow

/** Package names are Android's own ids for apps. Checked from memory, not from the Play Store: if an app does not react, look up its id under Android's App info. */
private val SUGGESTED = listOf(
    "Google Messages" to "com.google.android.apps.messaging",
    "Samsung Messages" to "com.samsung.android.messaging",
    "PhonePe" to "com.phonepe.app",
    "Google Pay" to "com.google.android.apps.nbu.paisa.user",
    "Paytm" to "net.one97.paytm",
)

private sealed interface AutoSheet {
    data object None : AutoSheet
    data object AddApp : AutoSheet
    data object BadPackage : AutoSheet
    data class NewSecret(val secret: String) : AutoSheet
    data object ConfirmNew : AutoSheet
}

/** Settings > Automation (master section 7; Phase 1 puts it in the Automation group). Screen: PDF p.30 More list pattern, rows and sheets as elsewhere. */
@Composable
fun AutomationScreen(vm: AutomationViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val c = ChittaTheme.colors
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<AutoSheet>(AutoSheet.None) }
    LaunchedEffect(Unit) { vm.refresh() }

    val custom = ui.apps.filter { p -> SUGGESTED.none { it.second == p } }.sorted()

    Column(Modifier.fillMaxSize()) {
        ChittaTopBar("Automation", onBack = onBack)
        LazyColumn(Modifier.padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 140.dp)) {
            item {
                SectionHeader("Read notifications")
                HairlineRule()
                ValueRow("Notification access", if (ui.accessOn) "On" else "Off", onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                ValueRow("I have been to Android settings", "Check again", onClick = { vm.refresh() })
                Text(
                    if (ui.accessOn) "Chitta reads only the apps you tick below. It keeps a notice only if it mentions rupees, and it goes to your review list like Ping messages. Nothing is saved until you tick it and save, except confident bank messages from your known accounts, which save with Undo."
                    else "Off. Without Notification access Chitta reads nothing, and everything else in the app works as usual. Tap the row to open Android's list and allow Chitta.",
                    style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2),
                )
                Text(
                    "Only notices that read like a bank debit or credit are understood. A notice that only says \"Paid ₹250\" is not read yet.",
                    style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2),
                )
                HairlineRule()
            }
            item { SectionHeader("Apps to read") }
            SUGGESTED.forEach { (label, pkg) ->
                item(key = pkg) {
                    ValueRow(label, if (pkg in ui.apps) "On" else "Off", onClick = { vm.setApp(pkg, pkg !in ui.apps) })
                    HairlineRule()
                }
            }
            custom.forEach { pkg ->
                item(key = pkg) {
                    ValueRow(pkg, "On · tap to remove", onClick = { vm.setApp(pkg, false) })
                    HairlineRule()
                }
            }
            item {
                ValueRow("Add an app by its package name", "", onClick = { sheet = AutoSheet.AddApp })
                Text("For your bank's app, use the id shown under Android's App info, like com.bank.app.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2))
                Spacer(Modifier.height(Dimens.SectionGap))

                SectionHeader("Add from another app")
                HairlineRule()
                ValueRow("Background add", if (ui.secretSet) "On" else "Off", onClick = { sheet = if (ui.secretSet) AutoSheet.ConfirmNew else AutoSheet.NewSecret(vm.newSecret()) })
                if (ui.secretSet) ValueRow("Turn background add off", "", onClick = { vm.turnOffReceiver() })
                Text(
                    "Another app (Tasker, a shortcut) can add an entry without opening Chitta, but only if it sends your secret. The entry needs an amount, one of your accounts and one of your categories, or it is refused with a notice. " +
                        "Every add shows a notice with Undo for 10 seconds. Transfers cannot be added this way. Making a new secret stops the old one at once.",
                    style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2),
                )
            }
        }
    }

    when (val s = sheet) {
        AutoSheet.AddApp -> TextSheet("Add an app", "Package name", "", { text -> sheet = if (vm.addPackage(text)) AutoSheet.None else AutoSheet.BadPackage }, { sheet = AutoSheet.None }, placeholder = "com.bank.app")
        AutoSheet.BadPackage -> ConfirmSheet("Not a package name", listOf("A package name has dots and no spaces, like com.bank.app. Nothing was added."), "Close", { sheet = AutoSheet.None }, { sheet = AutoSheet.None }, attention = false)
        AutoSheet.ConfirmNew -> ConfirmSheet("Make a new secret?", listOf("The old secret stops working at once. Apps that use it must be updated."), "Make new secret", { sheet = AutoSheet.NewSecret(vm.newSecret()) }, { sheet = AutoSheet.None })
        is AutoSheet.NewSecret -> ConfirmSheet(
            "Your secret",
            listOf(
                s.secret, "Copy it into the other app now; it is not shown again.",
                "Action: com.mansaketh.chitta.action.ADD", "Extras (text): secret, amount, title, category, account; optional type, note, date.",
            ),
            "Done", { sheet = AutoSheet.None }, { sheet = AutoSheet.None }, attention = false,
        )
        AutoSheet.None -> Unit
    }
}
