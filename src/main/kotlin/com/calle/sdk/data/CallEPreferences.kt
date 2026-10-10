package com.calle.sdk.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted credential storage for CALL-E Android SDK.
 *
 * All API keys and sensitive configuration are encrypted at rest using
 * AndroidX Security Crypto (AES-256-SIV for keys, AES-256-GCM for values)
 * via [EncryptedSharedPreferences]. The master key is backed by Android Keystore.
 *
 * SECURITY MODEL:
 * - Zero telemetry, zero data collection — all data stays on-device.
 * - Credentials are encrypted using Android Keystore-backed AES-256 master key.
 * - **No plaintext fallback.** If encrypted storage cannot be initialized,
 *   construction fails with [CredentialStorageException] so the developer
 *   is aware and can handle the error explicitly.
 *
 * ARCHITECTURAL NOTE ON CLIENT-SIDE API KEYS:
 * Encryption protects credentials at rest, but any key accessible to an
 * Android app can potentially be extracted by a determined attacker with
 * device access. For production deployments, the recommended architecture is:
 *
 *   1. Keep third-party secrets (SerpApi, Groq) on your backend server.
 *   2. Issue short-lived, scoped tokens from your backend to the Android client.
 *   3. Use this SDK's credential storage only for the scoped client token.
 *
 * @throws CredentialStorageException if AES-256 encrypted storage cannot be created.
 * @see <a href="https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences">EncryptedSharedPreferences</a>
 */
class CallEPreferences(context: Context) {

    /**
     * AES-256 encrypted SharedPreferences.
     * Throws [CredentialStorageException] on failure — never falls back to plaintext.
     */
    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext ?: context
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                appContext,
                ENCRYPTED_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize encrypted credential storage", e)
            throw CredentialStorageException(
                "Cannot initialize secure credential storage. " +
                "AES-256 EncryptedSharedPreferences is required but failed on this device. " +
                "Credentials will NOT be stored in plaintext.",
                e
            )
        }
    }

    // ── Encrypted Credentials ───────────────────────────────────────────

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value).apply()
        }

    var serpApiKey: String
        get() = prefs.getString(KEY_SERP_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SERP_API_KEY, value).apply()
        }

    var groqApiKey: String
        get() = prefs.getString(KEY_GROQ_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_GROQ_API_KEY, value).apply()
        }

    // ── Non-sensitive Configuration ─────────────────────────────────────

    var defaultPhoneNumber: String
        get() = prefs.getString(KEY_DEFAULT_PHONE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_PHONE, value).apply()
        }

    val isSetupComplete: Boolean
        get() = apiKey.isNotBlank()

    // ── Credential Utilities ────────────────────────────────────────────

    /**
     * Returns true — this implementation always uses AES-256 encryption.
     * If encryption was unavailable, construction would have failed.
     */
    val isEncryptedStorage: Boolean
        get() = true

    /**
     * Checks if any API key credentials are currently stored.
     */
    fun hasStoredCredentials(): Boolean {
        return apiKey.isNotBlank() || serpApiKey.isNotBlank() || groqApiKey.isNotBlank()
    }

    /**
     * Deletes all stored credentials and configuration from the encrypted store.
     */
    fun clear() {
        prefs.edit().clear().apply()
        Log.d(TAG, "All stored credentials deleted from encrypted store")
    }

    companion object {
        private const val TAG = "CallEPreferences"

        /** Encrypted preferences file (AES-256-SIV keys + AES-256-GCM values) */
        private const val ENCRYPTED_PREFS_FILE = "calle_sdk_secure_prefs"

        // Credential keys
        private const val KEY_API_KEY = "calle_api_key"
        private const val KEY_SERP_API_KEY = "serp_api_key"
        private const val KEY_GROQ_API_KEY = "groq_api_key"

        // Non-sensitive config keys
        private const val KEY_DEFAULT_PHONE = "calle_default_phone"
    }
}

/**
 * Thrown when AES-256 encrypted credential storage cannot be initialized.
 * This SDK never falls back to plaintext storage.
 */
class CredentialStorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
