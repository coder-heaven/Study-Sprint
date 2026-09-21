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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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
        val firebaseUser: FirebaseUser = suspendCoroutine { continuation ->
            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user == null) {
                        continuation.resumeWithException(
                            IllegalStateException("Firebase did not return a signed-in user.")
                        )
                    } else {
                        continuation.resume(user)
                    }
                }
                .addOnFailureListener(continuation::resumeWithException)
        }
        return GoogleAccount(
            email = firebaseUser.email ?: google.id,
            displayName = firebaseUser.displayName ?: google.displayName
                ?: google.givenName ?: google.id.substringBefore('@'),
            photoUrl = firebaseUser.photoUrl?.toString() ?: google.profilePictureUri?.toString()
        )
    }

    suspend fun signOut(context: Context) {
        FirebaseAuth.getInstance().signOut()
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    fun userMessage(error: Throwable): String = when (error) {
        is GetCredentialCancellationException -> "Google sign-in was cancelled."
        is GetCredentialException -> error.message ?: "Google sign-in failed. Check the OAuth configuration."
        is FirebaseAuthException -> error.message ?: "Firebase could not authenticate this Google account."
        is IllegalArgumentException -> error.message ?: "Google sign-in is not configured."
        else -> error.message ?: "Google sign-in failed. Please try again."
    }
}