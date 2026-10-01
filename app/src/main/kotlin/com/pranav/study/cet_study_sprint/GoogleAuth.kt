package com.pranav.study.cet_study_sprint

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

internal data class GoogleAccount(
    val email: String,
    val displayName: String,
    val photoUrl: String?
)

internal object GoogleAccountAuth {
    suspend fun signIn(context: Context, webClientId: String): GoogleAccount {
        require(webClientId.isNotBlank() && !webClientId.startsWith("YOUR_")) {
            "Google sign-in needs the OAuth Web client ID from Firebase."
        }
        val googleOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()
        val credential = CredentialManager.create(context)
            .getCredential(context = context, request = request)
            .credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("Google returned an unsupported credential type.")
        }

        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
        val auth = FirebaseAuth.getInstance()
        val localUser = auth.currentUser?.takeIf { it.isAnonymous }
        // Linking retains the anonymous UID, queued results and leaderboard position.
        val result = try {
            (localUser?.linkWithCredential(firebaseCredential) ?: auth.signInWithCredential(firebaseCredential))
                .awaitLeaderboardTask()
        } catch (collision: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            if (context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE)
                    .let { it.getBoolean("leaderboard_enabled", false) || it.getBoolean(PrivacyConsent.PENDING, false) || it.getString("leaderboard_uid", null) != null }) throw collision
            // The local row has been hidden (or was never shared), so switching is explicit and safe.
            auth.signInWithCredential(firebaseCredential).awaitLeaderboardTask()
        }
        val firebaseUser: FirebaseUser = result.user ?: error("Firebase did not return a signed-in user.")
        return GoogleAccount(
            email = firebaseUser.email ?: google.id,
            displayName = firebaseUser.displayName ?: google.displayName
                ?: google.givenName ?: google.id.substringBefore('@'),
            photoUrl = firebaseUser.photoUrl?.toString() ?: google.profilePictureUri?.toString()
        )
    }

    suspend fun signOut(context: Context) {
        // Keep the original identity until pending public-profile removal succeeds.
        Leaderboards.repository(context).leave()
        context.getSharedPreferences("study_sprint", Context.MODE_PRIVATE).edit()
            .putBoolean("leaderboard_enabled", false).remove("leaderboard_uid").apply()
        FirebaseAuth.getInstance().signOut()
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    fun userMessage(error: Throwable): String = when (error) {
        is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
            "This Google account already has a profile. Leave the local leaderboard first, then sign in again. Your study history stays on this phone."
        is GetCredentialCancellationException -> "Google sign-in was cancelled."
        is GetCredentialException -> error.message ?: "Google sign-in failed. Check the OAuth configuration."
        is FirebaseAuthException -> error.message ?: "Firebase could not authenticate this Google account."
        is IllegalArgumentException -> error.message ?: "Google sign-in is not configured."
        else -> error.message ?: "Google sign-in failed. Please try again."
    }
}
