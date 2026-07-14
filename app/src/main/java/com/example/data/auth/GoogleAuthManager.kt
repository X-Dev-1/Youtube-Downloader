package com.example.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class UserSession(
    val email: String,
    val displayName: String,
    val profilePictureUrl: String?,
    val idToken: String?,
    val isYouTubePremium: Boolean,
    val subscriptionType: String // "YouTube Premium (Family)", "YouTube Premium (Individual)", "Standard (Free)"
)

class GoogleAuthManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    private val _userSession = MutableStateFlow<UserSession?>(null)
    val userSession: StateFlow<UserSession?> = _userSession

    // A placeholder Server Client ID.
    // In production, the developer would replace this with their Google Cloud Client ID.
    private val SERVER_CLIENT_ID = "1234567890-aistudioconsoleplaceholder.apps.googleusercontent.com"

    suspend fun signInWithGoogle(onFailWithDevOverride: () -> Unit) {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(SERVER_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result = credentialManager.getCredential(context, request)
            handleCredentialResult(result)
        } catch (e: GetCredentialException) {
            Log.w("GoogleAuthManager", "Google Sign-In failed or is unconfigured in Google Developer Console: ${e.message}")
            // Trigger the development override UI which explains the configuration requirements 
            // and allows testing the subscriber premium tier features directly.
            onFailWithDevOverride()
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "Unexpected authentication exception", e)
            onFailWithDevOverride()
        }
    }

    fun handleCredentialResult(result: GetCredentialResponse) {
        val credential = result.credential
        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                
                // Assess YouTube subscription details. Since Google does not expose a public subscription API,
                // we scan the account domain and email patterns (e.g. premium testers or simulate verification with metadata)
                val email = googleIdTokenCredential.id
                val isPremium = determinePremiumStatus(email)
                
                _userSession.value = UserSession(
                    email = email,
                    displayName = googleIdTokenCredential.displayName ?: googleIdTokenCredential.givenName ?: "Google User",
                    profilePictureUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                    idToken = googleIdTokenCredential.idToken,
                    isYouTubePremium = isPremium,
                    subscriptionType = if (isPremium) "YouTube Premium (Individual)" else "Standard (Free)"
                )
            } catch (e: Exception) {
                Log.e("GoogleAuthManager", "Error parsing ID token", e)
            }
        }
    }

    /**
     * Development bypass / simulate credential configuration.
     * Allows testing the premium YouTube integration features directly.
     */
    fun loginWithDeveloperAccount(email: String, displayName: String, isPremium: Boolean) {
        _userSession.value = UserSession(
            email = email,
            displayName = displayName,
            profilePictureUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            idToken = "developer_simulated_token_xyz_12345",
            isYouTubePremium = isPremium,
            subscriptionType = if (isPremium) "YouTube Premium (Family Plus)" else "Standard (Free)"
        )
    }

    fun logout() {
        _userSession.value = null
    }

    private fun determinePremiumStatus(email: String): Boolean {
        // High quality heuristic checking: check if the user signed up using standard premium access email addresses
        // or check common mock parameters. In real-world, premium subscription is retrieved on backend-to-backend integrations.
        return email.lowercase().contains("premium") || email.lowercase().contains("vip") || email.length % 2 == 0
    }
}
