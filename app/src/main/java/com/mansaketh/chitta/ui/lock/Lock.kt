package com.mansaketh.chitta.ui.lock

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mansaketh.chitta.data.PinGate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.R
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
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ChittaButton
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

    /** True when a fingerprint or face is enrolled (no PIN or pattern fallback: this is only the "Use fingerprint" button). */
    fun canUseBiometric(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /** Fingerprint or face only. Cancelling returns to the PIN screen. */
    fun promptBiometric(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
        }
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build()
        BiometricPrompt(activity, executor, callback).authenticate(info)
    }

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

/**
 * Shown instead of the app while locked (PDF p.9). The in-app PIN pad; a phone with no PIN yet (the lock was on before the PIN existed)
 * first asks for the phone's own lock, then asks for a new PIN. Nothing from the book is drawn here, and the app behind it is not composed.
 */
@Composable
fun LockScreen(gate: PinGate, onUnlocked: () -> Unit) {
    val hasPin by gate.hasPin.collectAsState()
    var phoneChecked by rememberSaveable { mutableStateOf(false) }
    if (hasPin || phoneChecked) {
        PinFlow(gate, if (hasPin) PinMode.Enter else PinMode.Choose, onDone = onUnlocked, atLock = true)
    } else {
        val activity = LocalContext.current as? FragmentActivity
        val c = ChittaTheme.colors
        val ask = { activity?.let { BiometricGate.prompt(it, "Unlock Paddhu Pusthakam", "Then choose a PIN", onSuccess = { phoneChecked = true }) }; Unit }
        LaunchedEffect(Unit) { ask() }
        Column(
            Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(Dimens.Margin),
            verticalArrangement = Arrangement.Center,
        ) {
            AppIcon(Dimens.LogoLarge, shape = CircleShape)
            Spacer(Modifier.height(Dimens.Gap4))
            Text("Paddhu Pusthakam", style = ChittaTheme.type.headline, color = c.ink)
            Spacer(Modifier.height(Dimens.Gap3))
            Text("Confirm with your phone's lock, then choose a PIN.", style = ChittaTheme.type.body, color = c.inkMuted)
            Spacer(Modifier.height(Dimens.Gap6))
            ChittaButton("Unlock", ask, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * The launcher icon drawn from its two layers (an adaptive icon cannot be loaded as one image), cut to a rounded square the way a launcher
 * shows it (or a circle when [shape] is given, as on the Lock and First run pages). An adaptive icon is a 108 dp canvas of which the middle 72 dp shows, so the layers are drawn 1.5 times the size of the box.
 * Decorative: the name beside it carries the meaning, so TalkBack skips it.
 */
@Composable
fun AppIcon(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape? = null) {
    Box(modifier.size(size).clip(shape ?: RoundedCornerShape(size / 4)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.mipmap.ic_launcher_background), null, Modifier.requiredSize(size * 1.5f))
        Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.requiredSize(size * 1.5f))
    }
}
