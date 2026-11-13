package com.mansaketh.chitta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var mode by rememberSaveable { mutableStateOf(ThemeMode.System) }
            var hide by rememberSaveable { mutableStateOf(false) }
            ChittaTheme(mode = mode, hideAmounts = hide) {
                if (BuildConfig.DEBUG) {
                    // Component gallery: debug builds only (Phase 2 decision).
                    ComponentGallery(mode, { mode = it }, hide, { hide = it })
                } else {
                    Box(Modifier.fillMaxSize().background(ChittaTheme.colors.background).padding(Dimens.Margin)) {
                        Text("Chitta", style = ChittaTheme.type.headline, color = ChittaTheme.colors.ink)
                    }
                }
            }
        }
    }
}
