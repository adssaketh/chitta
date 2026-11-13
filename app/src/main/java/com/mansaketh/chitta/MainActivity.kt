package com.mansaketh.chitta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mansaketh.chitta.core.design.gallery.ComponentGallery
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.ThemeMode
import com.mansaketh.chitta.core.design.tokens.Dimens

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val root: com.mansaketh.chitta.ui.RootViewModel by viewModels()
    private val ledger: com.mansaketh.chitta.ui.ledger.LedgerViewModel by viewModels()
    private val quickAdd: com.mansaketh.chitta.ui.add.QuickAddViewModel by viewModels()
    private val detail: com.mansaketh.chitta.ui.detail.EntryDetailViewModel by viewModels()
    private val manage: com.mansaketh.chitta.ui.manage.ManageViewModel by viewModels()
    private val imports: com.mansaketh.chitta.ui.imports.ImportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var mode by rememberSaveable { mutableStateOf(ThemeMode.System) }
            var hide by rememberSaveable { mutableStateOf(false) }
            ChittaTheme(mode = mode, hideAmounts = hide) {
                val vms = com.mansaketh.chitta.ui.AppViewModels(root, ledger, quickAdd, detail, manage, imports)
                if (BuildConfig.DEBUG) {
                    // Component gallery stays reachable from More in debug builds only (Phase 2 decision).
                    com.mansaketh.chitta.ui.AppRoot(vms) { onBack -> ComponentGallery(mode, { mode = it }, hide, { hide = it }) ; androidx.activity.compose.BackHandler(onBack = onBack) }
                } else {
                    com.mansaketh.chitta.ui.AppRoot(vms)
                }
            }
        }
    }
}
