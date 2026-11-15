package com.mansaketh.chitta.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase for the household: Auth (the same Google account as Drive backup) and Firestore. Firebase starts only when the household
 * is first used, so a phone that never uses it never starts it.
 *
 * No google-services plugin and no google-services.json: the plugin needs a client for every build type's package name, and the
 * debug build (`.debug` suffix) has none. The three values below are the public ids of the old Paddhu Pusthakam Android app in
 * project cashew-saketh (the same ones its google-services.json holds). They are not secrets; Firestore rules decide who can read what.
 */
@Singleton
class FirebaseSession @Inject constructor(@ApplicationContext private val context: Context) {
    private val app: FirebaseApp by lazy {
        FirebaseApp.getApps(context).firstOrNull()
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId("cashew-saketh")
                    .setApplicationId("1:75913201715:android:1743ccce3fb699b95bfb46")
                    .setApiKey("AIzaSyBj44s4oxBGR-DL--v-uU7dnHSDXxNxxC4")
                    .build(),
            )!!
    }

    val auth: FirebaseAuth get() = FirebaseAuth.getInstance(app)
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance(app)

    /** The signed-in person's Firebase id, or null when Firebase has nobody signed in on this phone. */
    val uid: String? get() = runCatching { auth.currentUser?.uid }.getOrNull()
    val displayName: String? get() = runCatching { auth.currentUser?.displayName }.getOrNull()
    val email: String? get() = runCatching { auth.currentUser?.email }.getOrNull()

    /** Signs in to Firebase with the Google id token that Credential Manager gave for the account. */
    suspend fun signIn(googleIdToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken, null)).await()
    }

    fun signOut() {
        runCatching { auth.signOut() }
    }
}
