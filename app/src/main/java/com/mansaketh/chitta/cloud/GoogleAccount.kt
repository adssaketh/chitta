package com.mansaketh.chitta.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.mansaketh.chitta.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SignInResult {
    data class Ok(val email: String) : SignInResult
    data object Cancelled : SignInResult
    data class Failed(val message: String) : SignInResult
}

/**
 * Sign in with Google through Credential Manager (the "Sign in with Google" button flow, started by a tap). This only says who the
 * person is. Permission for Drive is a separate step ([DriveAuthorizer]), as Google's guide for Android requires.
 */
@Singleton
class GoogleAccount @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: CloudPrefs,
) {
    suspend fun signIn(activity: Activity): SignInResult {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
            .build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            val isGoogle = credential is CustomCredential &&
                (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)
            if (!isGoogle) return SignInResult.Failed("Google sent back something Paddhu Pusthakam does not read. Nothing was changed.")
            val google = GoogleIdTokenCredential.createFrom((credential as CustomCredential).data)
            // For a Google credential the id is the account's email address.
            val email = google.id.ifBlank { return SignInResult.Failed("Google did not say which account this is.") }
            prefs.signedIn(email)
            SignInResult.Ok(email)
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            SignInResult.Failed("No Google account was offered. Add one in Android settings, then try again.")
        } catch (e: GetCredentialException) {
            SignInResult.Failed(
                "Google did not allow the sign-in (${e.type.substringAfterLast('.')}). If this is a new build, its app id and signing " +
                    "certificate must be registered in the Firebase project.",
            )
        } catch (e: Exception) {
            SignInResult.Failed("Sign-in did not finish. Nothing was changed.")
        }
    }

    /** Forgets the account on this phone. The backups stay in the person's Drive. */
    suspend fun signOut() {
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        prefs.signedOut()
    }
}
