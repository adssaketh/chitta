package com.mansaketh.chitta.cloud

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface TokenResult {
    data class Token(val value: String) : TokenResult
    /** Google needs the person to say yes on its own screen. Launch [intent], then ask again. */
    data class NeedsConsent(val intent: PendingIntent) : TokenResult
    data class Failed(val message: String) : TokenResult
}

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}

/**
 * Asks Google for permission to use only the app's hidden Drive folder (scope drive.appdata, which Google lists as non-sensitive),
 * for the signed-in account. Without a screen it works only after the person has said yes once.
 */
@Singleton
class DriveAuthorizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: CloudPrefs,
) {
    private val client get() = Identity.getAuthorizationClient(context)

    suspend fun token(): TokenResult {
        val email = prefs.email.value ?: return TokenResult.Failed("Sign in with Google first.")
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA)))
            .setAccount(Account(email, "com.google"))
            .build()
        return try { toResult(client.authorize(request).await()) } catch (e: Exception) {
            TokenResult.Failed("Google would not give Paddhu Pusthakam access to Drive (${e.javaClass.simpleName}). Nothing was changed.")
        }
    }

    /** Reads the answer after the person has been through Google's consent screen. */
    fun fromIntent(data: Intent?): TokenResult {
        if (data == null) return TokenResult.Failed("Drive access was not allowed.")
        return try { toResult(client.getAuthorizationResultFromIntent(data)) } catch (e: Exception) {
            TokenResult.Failed("Drive access was not allowed.")
        }
    }

    /** Drops a token Drive refused, so the next [token] call gets a fresh one. */
    suspend fun forget(token: String) { runCatching { client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await() } }

    private fun toResult(r: AuthorizationResult): TokenResult {
        val pending = r.pendingIntent
        if (r.hasResolution() && pending != null) return TokenResult.NeedsConsent(pending)
        val token = r.accessToken ?: return TokenResult.Failed("Google gave no Drive access.")
        return TokenResult.Token(token)
    }

    companion object { const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata" }
}
