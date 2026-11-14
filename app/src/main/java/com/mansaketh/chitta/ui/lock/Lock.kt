package com.mansaketh.chitta.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.mansaketh.chitta.core.design.components.ButtonStyle
import com.mansaketh.chitta.core.design.components.ChittaButton
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens

/**
 * Fingerprint or face, with the phone's PIN, pattern or password as the fallback (BIOMETRIC_WEAK or DEVICE_CREDENTIAL). On Android 9 and 10
 * the library falls back to the system confirm-credential screen. androidx.biometric 1.1.0 is the latest stable release (DECISIONS D67).
 */
object BiometricGate {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** False when the phone has no screen lock or biometric set up, so a lock would have nothing to check against. */
    fun canAuthenticate(context: Context): Boolean = BiometricManager.from(context).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, title: String, subtitle: String?, onSuccess: () -> Unit, onFailure: (String) -> Unit = {}) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onFailure(errString.toString()) }
        }
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(AUTH).also { if (subtitle != null) it.setSubtitle(subtitle) }.build()
        BiometricPrompt(activity, executor, callback).authenticate(info)
    }
}

/** Shown instead of the app while locked. Nothing from the book is drawn here, and the app behind it is not composed. */
@Composable
fun LockScreen(onUnlock: () -> Unit, message: String? = null) {
    val c = ChittaTheme.colors
    // Ask once as the screen appears; if it is cancelled, the button asks again.
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(Dimens.Margin),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Paddhu Pusthakam", style = ChittaTheme.type.headline, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap3))
        Text(message ?: "Your book is locked.", style = ChittaTheme.type.body, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap6))
        ChittaButton("Unlock", onUnlock, style = ButtonStyle.Primary)
    }
}
