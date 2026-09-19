package com.calle.sdk.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

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
 * This approach limits blast radius if a device is compromised. The SDK
 * supports this pattern — store only the scoped token in [apiKey] and
 * proxy SerpApi/Groq calls through your backend.
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
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                ENCRYPTED_PREFS_FILE,
                masterKeyAlias,
                context,
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
        get() = prefs.getString(KEY_DEFAULT_PHONE, "+15550192834") ?: "+15550192834"
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_PHONE, value).apply()
        }

    var isSetupComplete: Boolean
        get() = apiKey.isNotBlank()
        set(_) {}

    var card1Name: String
        get() = prefs.getString(KEY_CARD1_NAME, "\uD83D\uDD0D Book a Restaurant") ?: "\uD83D\uDD0D Book a Restaurant"
        set(value) { prefs.edit().putString(KEY_CARD1_NAME, value).apply() }

    var card1Phone: String
        get() = prefs.getString(KEY_CARD1_PHONE, "") ?: ""
        set(value) { prefs.edit().putString(KEY_CARD1_PHONE, value).apply() }

    var card1Prompt: String
        get() = prefs.getString(KEY_CARD1_PROMPT, "Call the restaurant and book a table for 2 tonight at 7 PM") ?: "Call the restaurant and book a table for 2 tonight at 7 PM"
        set(value) { prefs.edit().putString(KEY_CARD1_PROMPT, value).apply() }

    var card2Name: String
        get() = prefs.getString(KEY_CARD2_NAME, "\uD83D\uDCC5 Schedule Appointment") ?: "\uD83D\uDCC5 Schedule Appointment"
        set(value) { prefs.edit().putString(KEY_CARD2_NAME, value).apply() }

    var card2Phone: String
        get() = prefs.getString(KEY_CARD2_PHONE, "") ?: ""
        set(value) { prefs.edit().putString(KEY_CARD2_PHONE, value).apply() }

    var card2Prompt: String
        get() = prefs.getString(KEY_CARD2_PROMPT, "Call the clinic and schedule an appointment for a general checkup this week") ?: "Call the clinic and schedule an appointment for a general checkup this week"
        set(value) { prefs.edit().putString(KEY_CARD2_PROMPT, value).apply() }

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
     *
     * Note: This clears all key-value entries from the EncryptedSharedPreferences file.
     * The underlying encrypted XML file and Android Keystore master key persist
     * (managed by the OS). This is a data deletion, not a cryptographic key destruction.
     * For full removal, the app must be uninstalled or app data cleared via system settings.
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
        private const val KEY_CARD1_NAME = "card1_name"
        private const val KEY_CARD1_PHONE = "card1_phone"
        private const val KEY_CARD1_PROMPT = "card1_prompt"
        private const val KEY_CARD2_NAME = "card2_name"
        private const val KEY_CARD2_PHONE = "card2_phone"
        private const val KEY_CARD2_PROMPT = "card2_prompt"
    }
}

/**
 * Thrown when AES-256 encrypted credential storage cannot be initialized.
 * This SDK never falls back to plaintext storage.
 */
class CredentialStorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
