package com.mansaketh.chitta.ui.household

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.cloud.HouseholdSnapshot
import com.mansaketh.chitta.core.design.components.ButtonStyle
import com.mansaketh.chitta.core.design.components.ChittaButton
import com.mansaketh.chitta.core.design.components.ChittaTopBar
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.Stamp
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.design.tokens.Shapes
import com.mansaketh.chitta.core.domain.HouseholdDiagnostics
import com.mansaketh.chitta.core.domain.HouseholdMember
import com.mansaketh.chitta.core.domain.HouseholdSharing
import com.mansaketh.chitta.core.domain.Households
import com.mansaketh.chitta.ui.BottomOverlay
import com.mansaketh.chitta.ui.Choice
import com.mansaketh.chitta.ui.ChoiceSheet
import com.mansaketh.chitta.ui.ConfirmSheet
import com.mansaketh.chitta.ui.TextSheet
import com.mansaketh.chitta.ui.ValueRow

/**
 * Household (Phase 9b, steps 2 and 3). The page follows the design PDF p.31 (Household), p.32 (Invite sheet) and p.33 (Choose what to share).
 * Step 4 adds the "Who spent how much" row (opens the p.34 page) and the Diagnostics row and sheet.
 */
@Composable
fun HouseholdScreen(vm: HouseholdViewModel, onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    val ui by vm.ui.collectAsState()
    val c = ChittaTheme.colors
    val activity = LocalContext.current as? Activity
    // Which sheet is open: create-name, create-nick, join-code, join-nick, nickname, rename, invite, leave, member:UID, owner:UID, remove:UID.
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    // What was typed in the first step of the two-step create and join sheets.
    var draft by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.opened() }
    // Right after creating or joining, ask once what to do with the entries already on the phone (PDF p.33).
    LaunchedEffect(ui.askToShare) { if (ui.askToShare) { sheet = "share-choice"; vm.askHandled() } }

    Column(Modifier.fillMaxSize()) {
        ChittaTopBar("Household", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.EndInset)) {
            if (ui.email != null && ui.connected) Text("Signed in with Google", style = ChittaTheme.type.label, color = c.inkMuted)
            if (ui.notice != null) {
                Text(ui.notice!!, style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.padding(vertical = Dimens.Gap2))
                ChittaButton("Dismiss", { vm.dismissNotice() }, style = ButtonStyle.Quiet)
                Spacer(Modifier.height(Dimens.Gap4))
            }
            if (ui.busy) Text("Working. Keep this screen open.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2))

            val snap = ui.snapshot
            when {
                !ui.connected -> SignInPart(signedInWithGoogle = ui.email != null, enabled = activity != null && !ui.busy) { activity?.let(vm::signIn) }
                !ui.local.inHousehold -> NoHouseholdPart(ui.busy, onCreate = { sheet = "create-name" }, onJoin = { sheet = "join-code" })
                snap == null -> Text("Reading the household.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap4))
                else -> HouseholdPart(
                    ui, snap,
                    onRename = { sheet = "rename" }, onInvite = { vm.loadCode(); sheet = "invite" },
                    onMember = { m -> sheet = if (m.uid == ui.myUid) "nickname" else "member:" + m.uid },
                    onToggleBalances = { vm.setShowBalances(!snap.showBalances) },
                    onSharing = { sheet = if (ui.sharing.ready) "sharing" else "share-choice" },
                    onWhoSpent = { onOpen("household-spent") },
                    onDiagnostics = { vm.loadDiagnostics(); sheet = "diagnostics" },
                    onLeave = { sheet = "leave" },
                )
            }
        }
    }

    val snap = ui.snapshot
    val key = sheet
    when {
        key == "create-name" -> TextSheet("Name your household", "Household name", "", { draft = it; sheet = "create-nick" }, { sheet = null }, saveLabel = "Next")
        key == "create-nick" -> TextSheet("Your name in the household", "The name others will see", ui.suggestedNickname, { vm.create(draft, it); sheet = null }, { sheet = null }, saveLabel = "Make household")
        key == "join-code" -> TextSheet("Invite code", "8 letters and numbers", "", { draft = it; sheet = "join-nick" }, { sheet = null }, saveLabel = "Next", placeholder = "K7PM-3QX9")
        key == "join-nick" -> TextSheet("Your name in the household", "The name others will see", ui.suggestedNickname, { vm.join(draft, it); sheet = null }, { sheet = null }, saveLabel = "Join")
        key == "nickname" -> TextSheet("Your name", "The name others will see", ui.local.nickname, { vm.setNickname(it); sheet = null }, { sheet = null })
        key == "rename" -> TextSheet("Household name", "Name", snap?.name ?: ui.local.name, { vm.rename(it); sheet = null }, { sheet = null })
        key == "share-choice" && ui.local.inHousehold -> ShareChoiceSheet(
            snap?.name ?: ui.local.name,
            onContinue = { choice -> sheet = null; vm.chooseSharing(choice) }, onNotNow = { sheet = null },
        )
        key == "sharing" && ui.local.inHousehold -> SharingSheet(ui, vm, onClose = { sheet = null })
        key == "diagnostics" && ui.local.inHousehold -> DiagnosticsSheet(vm, onClose = { sheet = null })
        key == "invite" && snap != null -> InviteSheet(ui.code, Households.placesLeft(snap.members.size), vm, onClose = { sheet = null })
        key == "leave" -> ConfirmSheet(
            "Leave this household?",
            listOf("Your entries stay on this phone as your own. Entries other members shared are removed from it.", "You can join again with a new invite code."),
            "Leave", { sheet = null; vm.leave() }, { sheet = null },
        )
        key != null && key.startsWith("member:") && snap != null -> {
            val m = snap.members.firstOrNull { it.uid == key.removePrefix("member:") }
            if (m != null) MemberSheet(m, vm, onDismiss = { sheet = null }, onOwner = { sheet = "owner:" + m.uid }, onRemove = { sheet = "remove:" + m.uid })
        }
        key != null && key.startsWith("owner:") && snap != null -> {
            val m = snap.members.firstOrNull { it.uid == key.removePrefix("owner:") }
            if (m != null) ConfirmSheet(
                "Make ${Households.displayName(m)} the owner?",
                listOf("You become a normal member. Your invite code is cancelled, and the new owner makes a new one.", "Nobody's entries change."),
                "Make owner", { sheet = null; vm.makeOwner(m.uid) }, { sheet = null }, attention = false,
            )
        }
        key != null && key.startsWith("remove:") && snap != null -> {
            val m = snap.members.firstOrNull { it.uid == key.removePrefix("remove:") }
            if (m != null) ConfirmSheet(
                "Remove ${Households.displayName(m)}?",
                listOf("They can no longer see the household's shared entries. Entries already on their phone stay there.", "They can join again with a new invite code."),
                "Remove", { sheet = null; vm.remove(m.uid) }, { sheet = null },
            )
        }
    }
}

@Composable
private fun SignInPart(signedInWithGoogle: Boolean, enabled: Boolean, onSignIn: () -> Unit) {
    val c = ChittaTheme.colors
    Text(
        "Each person signs in with their own Google account and keeps their own private book. A household lets up to ${Households.MAX_MEMBERS} people share chosen entries.",
        style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.padding(vertical = Dimens.Gap4),
    )
    ChittaButton(if (signedInWithGoogle) "Continue with Google" else "Sign in with Google", onSignIn, enabled = enabled)
    Text(
        "Nothing is shared until you create or join a household. Your entries stay on this phone.",
        style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap4),
    )
}

@Composable
private fun NoHouseholdPart(busy: Boolean, onCreate: () -> Unit, onJoin: () -> Unit) {
    val c = ChittaTheme.colors
    Text(
        "You are not in a household. Create one and invite people with a code, or join one with a code someone gave you.",
        style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.padding(vertical = Dimens.Gap4),
    )
    ChittaButton("Create a household", onCreate, enabled = !busy)
    Spacer(Modifier.height(Dimens.Gap2))
    ChittaButton("Join with a code", onJoin, style = ButtonStyle.Quiet, enabled = !busy)
    Text(
        "Joining or leaving never deletes entries that are already on your phone.",
        style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap4),
    )
}

@Composable
private fun HouseholdPart(
    ui: HouseholdUi, snap: HouseholdSnapshot,
    onRename: () -> Unit, onInvite: () -> Unit, onMember: (HouseholdMember) -> Unit, onToggleBalances: () -> Unit, onSharing: () -> Unit,
    onWhoSpent: () -> Unit, onDiagnostics: () -> Unit, onLeave: () -> Unit,
) {
    val c = ChittaTheme.colors
    val iAmOwner = ui.local.isOwner
    Spacer(Modifier.height(Dimens.Gap4))
    Text("Household", style = ChittaTheme.type.label, color = c.inkMuted)
    Text(
        snap.name, style = ChittaTheme.type.headline, color = c.ink,
        modifier = Modifier.fillMaxWidth().then(if (iAmOwner) Modifier.clickable(role = Role.Button, onClickLabel = "Rename", onClick = onRename) else Modifier),
    )
    Spacer(Modifier.height(Dimens.SectionGap))

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Members", style = ChittaTheme.type.title, color = c.ink)
        Text("${snap.members.size} of ${Households.MAX_MEMBERS}", style = ChittaTheme.type.label, color = c.inkMuted)
    }
    Spacer(Modifier.height(Dimens.HeadingGap))
    HairlineRule()
    snap.members.forEach { m ->
        val mine = m.uid == ui.myUid
        // Everyone can tap their own row to change their name; the owner can tap the others for hand-over, delete permission and remove.
        val tappable = mine || iAmOwner
        MemberRow(m, mine, tappable) { onMember(m) }
    }
    Spacer(Modifier.height(Dimens.Gap6))

    if (iAmOwner) {
        ChittaButton("Invite someone", onInvite, style = ButtonStyle.Quiet, enabled = !ui.busy)
        Spacer(Modifier.height(Dimens.Gap6))
    }

    // The design draws a switch; the app has no switch yet, so this is a row that says On or Off, like the other Settings rows.
    Column {
        ValueRow("Show account balances to members", if (snap.showBalances) "On" else "Off", onClick = if (iAmOwner) onToggleBalances else null)
        Text(
            "Members always see their own. Accounts stay private to their owner." + if (iAmOwner) "" else " Only the owner can change this.",
            style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2),
        )
    }

    // PDF p.31 draws "Your sharing" with a line under it; built as a value row like the others (D202).
    ValueRow("Your sharing", sharingValue(ui), onClick = onSharing)
    Text(
        if (ui.sharing.ready) "New entries are shared with the household unless you mark them Mine only."
        else "Nothing is shared yet. Tap to choose what happens to the entries already on this phone.",
        style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2),
    )
    if (ui.sharing.problem.isNotBlank()) Text(ui.sharing.problem, style = ChittaTheme.type.label, color = c.ink, modifier = Modifier.padding(bottom = Dimens.Gap2))

    // PDF p.34 is reached from here. Diagnostics (master section 7) has no PDF page: a value row opening a sheet, built from existing parts.
    ValueRow("Who spent how much", "This month", onClick = onWhoSpent)
    ValueRow("Diagnostics", "Phone and server", onClick = onDiagnostics)

    Spacer(Modifier.height(Dimens.SectionGap))
    if (iAmOwner) {
        Text(
            "To leave, make another member the owner first.",
            style = ChittaTheme.type.label, color = c.inkMuted,
        )
    } else {
        Text(
            "Leave this household", style = ChittaTheme.type.body, color = c.ink, textDecoration = TextDecoration.Underline,
            modifier = Modifier.heightIn(min = Dimens.TouchTarget).clickable(role = Role.Button, onClick = onLeave).padding(vertical = Dimens.Gap3),
        )
    }
}

private fun sharingValue(ui: HouseholdUi): String = when {
    !ui.sharing.ready -> "Not chosen"
    ui.sharing.problem.isNotBlank() -> "Needs attention"
    (ui.counts?.waiting ?: 0) > 0 -> "${ui.counts!!.waiting} waiting"
    else -> "Up to date"
}

@Composable
private fun MemberRow(m: HouseholdMember, mine: Boolean, tappable: Boolean, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin)
                .then(if (tappable) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(vertical = Dimens.RowPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (mine) "You" else Households.displayName(m), style = ChittaTheme.type.body, color = c.ink)
                    if (m.isOwner) { Spacer(Modifier.width(Dimens.Gap2)); Stamp("Owner") }
                }
                Text(Households.memberLine(m), style = ChittaTheme.type.label, color = c.inkMuted)
            }
            if (tappable) Icon(ChittaIcons.CaretRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(Dimens.IconSmall))
        }
        HairlineRule()
    }
}

/** What the owner can do with another member (PDF p.31 has no sheet for this: built from the existing choice and confirm sheets). */
@Composable
private fun MemberSheet(m: HouseholdMember, vm: HouseholdViewModel, onDismiss: () -> Unit, onOwner: () -> Unit, onRemove: () -> Unit) {
    val name = Households.displayName(m)
    ChoiceSheet(
        name,
        listOf(
            Choice("Make owner", "owner", "You become a normal member"),
            Choice(if (m.canDelete) "Stop them deleting others' entries" else "Let them delete others' entries", "delete", Households.memberLine(m)),
            Choice("Remove from household", "remove", "Their entries stay on their phone"),
        ),
        { pick ->
            when (pick) {
                "owner" -> onOwner()
                "delete" -> { vm.setCanDelete(m.uid, !m.canDelete); onDismiss() }
                else -> onRemove()
            }
        },
        onDismiss,
    )
}

/** PDF p.32. One code at a time: a new code cancels the old one, and "Stop people joining" cancels it without a new one. */
@Composable
private fun InviteSheet(code: String?, placesLeft: Int, vm: HouseholdViewModel, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val context = LocalContext.current
    var copied by rememberSaveable { mutableStateOf(false) }
    BottomOverlay(onClose) {
        Text("Invite someone", style = ChittaTheme.type.headline, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        Text("Send them this code. They sign in with their own Google account and enter it.", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap4))
        if (code == null) {
            Text("There is no active code. Make one to let someone join.", style = ChittaTheme.type.body, color = c.ink)
            Spacer(Modifier.height(Dimens.Gap4))
            ChittaButton("Make a code", { vm.newCode() }, confirmHaptic = true)
        } else {
            val shown = Households.formatCode(code)
            Box(
                Modifier.fillMaxWidth().clip(Shapes.Control).background(c.background)
                    .drawBehind {
                        drawRoundRect(
                            color = c.mark, cornerRadius = CornerRadius(12.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        )
                    }
                    .clickable(role = Role.Button, onClickLabel = "Copy the code") {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Invite code", shown))
                        copied = true
                    }
                    .padding(vertical = Dimens.Gap6),
                contentAlignment = Alignment.Center,
            ) {
                Text(shown, style = ChittaTheme.type.amountLarge, color = c.ink, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(Dimens.Gap3))
            Text(
                (if (copied) "Copied. " else "Tap to copy. ") + "Anyone with this code can join, so share it only with people you trust.",
                style = ChittaTheme.type.label, color = c.inkMuted,
            )
            Spacer(Modifier.height(Dimens.Gap4))
            ChittaButton("Share code", {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Join my household in Paddhu Pusthakam. Sign in with Google, open More > Settings > Household > Join with a code, and enter: $shown")
                }
                context.startActivity(Intent.createChooser(send, "Share code").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            Spacer(Modifier.height(Dimens.Gap2))
            ChittaButton("Make a new code", { copied = false; vm.newCode() }, style = ButtonStyle.Quiet)
            Spacer(Modifier.height(Dimens.Gap3))
            Text(
                "A new code cancels the old one. " + when (placesLeft) { 0 -> "The household is full."; 1 -> "1 place is left in the household."; else -> "$placesLeft places are left in the household." },
                style = ChittaTheme.type.label, color = c.inkMuted,
            )
            Text(
                "Stop people joining with this code", style = ChittaTheme.type.body, color = c.ink, textDecoration = TextDecoration.Underline,
                modifier = Modifier.heightIn(min = Dimens.TouchTarget).clickable(role = Role.Button) { copied = false; vm.stopJoining() }.padding(vertical = Dimens.Gap3),
            )
        }
    }
}

/** PDF p.33. Shown right after creating or joining, and from "Your sharing" until a choice is made. Nothing is deleted either way. */
@Composable
private fun ShareChoiceSheet(householdName: String, onContinue: (HouseholdSharing.ShareChoice) -> Unit, onNotNow: () -> Unit) {
    val c = ChittaTheme.colors
    var choice by rememberSaveable { mutableStateOf(HouseholdSharing.ShareChoice.SHARE_EXISTING) }
    BottomOverlay(onNotNow) {
        Text("Share your existing entries?", style = ChittaTheme.type.headline, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        Text(
            "You've joined ${householdName.ifBlank { "the household" }}. Choose what happens to the entries already on this phone.",
            style = ChittaTheme.type.label, color = c.inkMuted,
        )
        Spacer(Modifier.height(Dimens.Gap4))
        ShareOption(
            "Share my earlier entries", "Add the entries you kept private on this phone to the household.",
            selected = choice == HouseholdSharing.ShareChoice.SHARE_EXISTING,
        ) { choice = HouseholdSharing.ShareChoice.SHARE_EXISTING }
        HairlineRule()
        ShareOption(
            "Only new entries", "What you add from now on is shared. Earlier ones stay on this phone.",
            selected = choice == HouseholdSharing.ShareChoice.ONLY_NEW,
        ) { choice = HouseholdSharing.ShareChoice.ONLY_NEW }
        HairlineRule()
        Spacer(Modifier.height(Dimens.Gap3))
        Text(
            "Everyone can always edit and delete their own entries. You can share earlier entries later from the Household page.",
            style = ChittaTheme.type.label, color = c.inkMuted,
        )
        Spacer(Modifier.height(Dimens.Gap4))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
            ChittaButton("Not now", onNotNow, style = ButtonStyle.Quiet, modifier = Modifier.weight(1f))
            ChittaButton("Continue", { onContinue(choice) }, modifier = Modifier.weight(1f), confirmHaptic = true)
        }
    }
}

/** A radio row: the app has no radio component, so this draws the ring and the dot. */
@Composable
private fun ShareOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = Dimens.Gap3),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 2.dp).size(20.dp).border(1.5.dp, c.ink, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(c.ink))
        }
        Spacer(Modifier.width(Dimens.Gap3))
        Column(Modifier.weight(1f)) {
            Text(title, style = if (selected) ChittaTheme.type.bodyMedium else ChittaTheme.type.body, color = c.ink)
            Text(detail, style = ChittaTheme.type.label, color = c.inkMuted)
        }
    }
}

/** What "Your sharing" opens: how much is shared and waiting, when it last went up, and the two things a person can do about it. */
@Composable
private fun SharingSheet(ui: HouseholdUi, vm: HouseholdViewModel, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val counts = ui.counts
    BottomOverlay(onClose) {
        Text("Your sharing", style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap3))
        if (counts != null) {
            Text("${counts.shared} of your entries are shared. ${counts.waiting} waiting to go up.", style = ChittaTheme.type.body, color = c.ink)
            Text("${counts.fromMembers} entries from other members are on this phone.", style = ChittaTheme.type.label, color = c.inkMuted)
        }
        Text(
            if (ui.sharing.lastSyncMs > 0) "Last sent " + java.time.Instant.ofEpochMilli(ui.sharing.lastSyncMs).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM, h:mm a", java.util.Locale.ENGLISH)) + "." else "Nothing sent yet.",
            style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2),
        )
        if (ui.sharing.problem.isNotBlank()) Text(ui.sharing.problem, style = ChittaTheme.type.label, color = c.ink, modifier = Modifier.padding(top = Dimens.Gap2))
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Send now", { vm.sendNow(); onClose() }, enabled = !ui.busy)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Share my earlier entries", { vm.shareEarlier(); onClose() }, style = ButtonStyle.Quiet, enabled = !ui.busy)
        Spacer(Modifier.height(Dimens.Gap3))
        Text(
            "Entries marked Mine only are never shared. Sharing earlier entries cannot be undone from here; leaving the household keeps your entries on this phone.",
            style = ChittaTheme.type.label, color = c.inkMuted,
        )
    }
}

/** What the phone and the server each hold, side by side (master section 7). Read from the server when the sheet opens; "Check again" reads it again. */
@Composable
private fun DiagnosticsSheet(vm: HouseholdViewModel, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val d by vm.diagnostics.collectAsState()
    BottomOverlay(onClose) {
        Text("Diagnostics", style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        Text("What this phone holds against what the server holds.", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap3))
        when {
            d.loading -> Text("Reading the server.", style = ChittaTheme.type.body, color = c.inkMuted)
            d.error != null -> Text(d.error!!, style = ChittaTheme.type.body, color = c.ink)
            else -> {
                HairlineRule()
                d.rows.forEach { r ->
                    Column(Modifier.fillMaxWidth().padding(vertical = Dimens.RowPadV)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(r.label, style = ChittaTheme.type.body, color = c.ink)
                            Text("Phone ${r.phone} · Server ${r.server}", style = ChittaTheme.type.label, color = c.inkMuted)
                        }
                        Text(HouseholdDiagnostics.describe(r), style = ChittaTheme.type.label, color = c.inkMuted)
                    }
                    HairlineRule()
                }
                Spacer(Modifier.height(Dimens.Gap3))
                Text(
                    if (HouseholdDiagnostics.allSame(d.rows)) "Everything matches." else "Something differs. Tap Send now on Your sharing, then check again.",
                    style = ChittaTheme.type.body, color = c.ink,
                )
            }
        }
        HouseholdDiagnostics.waitingNote(d.waiting)?.let { Text(it, style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2)) }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Check again", { vm.loadDiagnostics() }, style = ButtonStyle.Quiet, enabled = !d.loading)
    }
}
