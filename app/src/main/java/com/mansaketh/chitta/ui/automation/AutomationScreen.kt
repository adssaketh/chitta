package com.mansaketh.chitta.ui.automation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.ChittaTopBar
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.SectionHeader
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.ui.ConfirmSheet
import com.mansaketh.chitta.ui.ValueRow

private sealed interface AutoSheet {
    data object None : AutoSheet
    data class NewSecret(val secret: String) : AutoSheet
    data object ConfirmNew : AutoSheet
}

/** Settings > Automation (master section 7): the secret for adding entries from another app. Rows and sheets as elsewhere in Settings (PDF p.30). */
@Composable
fun AutomationScreen(vm: AutomationViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val c = ChittaTheme.colors
    var sheet by remember { mutableStateOf<AutoSheet>(AutoSheet.None) }
    LaunchedEffect(Unit) { vm.refresh() }

    Column(Modifier.fillMaxSize()) {
        ChittaTopBar("Automation", onBack = onBack)
        LazyColumn(Modifier.padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 140.dp)) {
            item {
                SectionHeader("Add from another app")
                HairlineRule()
                ValueRow("Background add", if (ui.secretSet) "On" else "Off", onClick = { sheet = if (ui.secretSet) AutoSheet.ConfirmNew else AutoSheet.NewSecret(vm.newSecret()) })
                if (ui.secretSet) ValueRow("Turn background add off", "", onClick = { vm.turnOffReceiver() })
                Text(
                    "Another app (Tasker, a shortcut) can add an entry without opening Chitta, but only if it sends your secret. The entry needs an amount, one of your accounts and one of your categories, or it is refused with a notice. " +
                        "Every add shows a notice with Undo for 10 seconds. Transfers cannot be added this way. Making a new secret stops the old one at once.",
                    style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2),
                )
                Text(
                    "Bank messages come from Ping, and payment screenshots from your UPI apps are read in Bank messages and UPI, so Chitta does not read notifications.",
                    style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2),
                )
            }
        }
    }

    when (val s = sheet) {
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
