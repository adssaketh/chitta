package com.mansaketh.chitta

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.mansaketh.chitta.data.AppLock
import com.mansaketh.chitta.data.SecurityPrefs
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.ui.lock.BiometricGate
import com.mansaketh.chitta.ui.lock.LockScreen
import javax.inject.Inject
import com.mansaketh.chitta.core.design.gallery.ComponentGallery
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.ThemeMode
import com.mansaketh.chitta.core.design.tokens.Dimens

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var appLock: AppLock
    @Inject lateinit var security: SecurityPrefs
    @Inject lateinit var clock: Clock

    private val root: com.mansaketh.chitta.ui.RootViewModel by viewModels()
    private val ledger: com.mansaketh.chitta.ui.ledger.LedgerViewModel by viewModels()
    private val quickAdd: com.mansaketh.chitta.ui.add.QuickAddViewModel by viewModels()
    private val detail: com.mansaketh.chitta.ui.detail.EntryDetailViewModel by viewModels()
    private val manage: com.mansaketh.chitta.ui.manage.ManageViewModel by viewModels()
    private val imports: com.mansaketh.chitta.ui.imports.ImportViewModel by viewModels()
    private val plan: com.mansaketh.chitta.ui.plan.PlanViewModel by viewModels()
    private val home: com.mansaketh.chitta.ui.home.HomeViewModel by viewModels()
    private val insights: com.mansaketh.chitta.ui.insights.InsightsViewModel by viewModels()
    private val settings: com.mansaketh.chitta.ui.settings.SettingsViewModel by viewModels()
    private val csvImport: com.mansaketh.chitta.ui.imports.CsvImportViewModel by viewModels()
    private val intake: com.mansaketh.chitta.ui.intake.IntakeViewModel by viewModels()
    private val rules: com.mansaketh.chitta.ui.rules.RulesViewModel by viewModels()
    private val automation: com.mansaketh.chitta.ui.automation.AutomationViewModel by viewModels()
    private val assets: com.mansaketh.chitta.ui.assets.AssetsViewModel by viewModels()
    private val cloud: com.mansaketh.chitta.ui.cloud.CloudBackupViewModel by viewModels()
    @Inject lateinit var signals: com.mansaketh.chitta.intake.IntakeSignals

    /** Leaving the screen starts the lock timer; a rotation does not (the activity is recreated, the app was never away). */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) appLock.onLeft(clock.nowMs())
    }

    override fun onStart() {
        super.onStart()
        appLock.onReturned(clock.nowMs())
    }

    private fun askToUnlock() {
        BiometricGate.prompt(this, "Unlock Paddhu Pusthakam", null, onSuccess = { appLock.unlocked() })
    }

    /** The share receiver opens this screen with a note of what arrived (bank messages, or a payment screenshot, PDF or CSV). */
    private fun takeIntakeRequest(i: android.content.Intent?) {
        val what = i?.getStringExtra(com.mansaketh.chitta.intake.ShareReceiverActivity.EXTRA_OPEN) ?: return
        i.removeExtra(com.mansaketh.chitta.intake.ShareReceiverActivity.EXTRA_OPEN)
        signals.request(what)
        signals.refreshNow()
    }

    /**
     * A `chitta://add?...` link: keep its query for the Quick Add sheet (only plain text values are taken; nothing is saved).
     * A `chitta://open?tab=ledger` link (launcher shortcut) only asks for a bottom tab. Both wait behind the lock screen.
     */
    private fun takeLink(i: android.content.Intent?) {
        val uri = i?.data ?: return
        if (i.action != android.content.Intent.ACTION_VIEW || uri.scheme != "chitta") return
        when (uri.host) {
            "add" -> { i.data = null; signals.requestLink(uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty().take(500) }) }
            "open" -> { i.data = null; uri.getQueryParameter("tab")?.take(20)?.let { signals.requestTab(it) } }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeIntakeRequest(intent)
        takeLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) { takeIntakeRequest(intent); takeLink(intent) }
        enableEdgeToEdge()
        setContent {
            var mode by rememberSaveable { mutableStateOf(ThemeMode.System) }
            var hide by rememberSaveable { mutableStateOf(security.hideAmounts.value) }
            // Kept on the phone so the home-screen widgets can mask too.
            val toggleHide = { hide = !hide; security.setHideAmounts(hide) }
            val locked by appLock.locked.collectAsState()
            val hideInRecents by security.hideInRecents.collectAsState()
            // Bank messages Ping handed over while Chitta was closed are read when it opens (and again when something arrives).
            val inboxTick by signals.refresh.collectAsState()
            LaunchedEffect(locked, inboxTick) { if (!locked) intake.processInbox() }
            // A secure window blanks the recent-apps preview (and blocks screenshots).
            LaunchedEffect(hideInRecents) {
                if (hideInRecents) window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
            ChittaTheme(mode = mode, hideAmounts = hide) {
                val vms = com.mansaketh.chitta.ui.AppViewModels(root, ledger, quickAdd, detail, manage, imports, plan, home, insights, settings, csvImport, intake, rules, automation, assets, cloud)
                if (locked) {
                    // While locked nothing from the book is composed, so nothing can be read or heard from behind the lock.
                    LockScreen(onUnlock = ::askToUnlock)
                } else if (BuildConfig.DEBUG) {
                    // Component gallery stays reachable from More in debug builds only (Phase 2 decision).
                    com.mansaketh.chitta.ui.AppRoot(vms, hide, toggleHide) { onBack -> ComponentGallery(mode, { mode = it }, hide, { hide = it }) ; androidx.activity.compose.BackHandler(onBack = onBack) }
                } else {
                    com.mansaketh.chitta.ui.AppRoot(vms, hide, toggleHide)
                }
            }
        }
    }
}
