package com.calle.sdk.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.io.File

/**
 * Secure credential storage for CALL-E Android SDK.
 *
 * All API keys and sensitive configuration are encrypted at rest using
 * AndroidX Security Crypto (AES-256-SIV for keys, AES-256-GCM for values)
 * via [EncryptedSharedPreferences].
 *
 * SECURITY MODEL:
 * - Zero telemetry, zero data collection — all data stays on-device.
 * - Credentials are encrypted using Android Keystore-backed master key.
 * - A dedicated secure directory is created in app-internal storage
 *   (inaccessible to other apps without root).
 * - Automatic fallback to standard SharedPreferences on devices where
 *   AndroidX Security Crypto is unsupported (API < 23 edge cases).
 *
 * @see <a href="https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences">EncryptedSharedPreferences</a>
 */
class CallEPreferences(context: Context) {

    private val secureDir: File = File(context.filesDir, SECURE_STORAGE_DIR).apply {
        if (!exists()) {
            mkdirs()
            Log.d(TAG, "Created secure credential storage directory: $absolutePath")
        }
    }

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    /**
     * Creates AES-256 encrypted SharedPreferences backed by Android Keystore.
     * Falls back to standard SharedPreferences if encryption setup fails
     * (e.g., older devices with broken Keystore implementations).
     */
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
            Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to standard prefs", e)
            context.getSharedPreferences(FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    // ── CALL-E API Key (AES-256 Encrypted) ─────────────────────────────

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value).apply()
        }

    // ── SerpApi Key (AES-256 Encrypted) ─────────────────────────────────

    var serpApiKey: String
        get() = prefs.getString(KEY_SERP_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SERP_API_KEY, value).apply()
        }

    // ── Groq API Key (AES-256 Encrypted) ────────────────────────────────

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
        get() = prefs.getString(KEY_CARD1_NAME, "\uD83D\uDD0D Search & Call Cattleack BBQ") ?: "\uD83D\uDD0D Search & Call Cattleack BBQ"
        set(value) { prefs.edit().putString(KEY_CARD1_NAME, value).apply() }

    var card1Phone: String
        get() = prefs.getString(KEY_CARD1_PHONE, "") ?: ""
        set(value) { prefs.edit().putString(KEY_CARD1_PHONE, value).apply() }

    var card1Prompt: String
        get() = prefs.getString(KEY_CARD1_PROMPT, "Call Cattleack Barbeque in Farmers Branch TX and ask opening hours & best sellers") ?: "Call Cattleack Barbeque in Farmers Branch TX and ask opening hours & best sellers"
        set(value) { prefs.edit().putString(KEY_CARD1_PROMPT, value).apply() }

    var card2Name: String
        get() = prefs.getString(KEY_CARD2_NAME, "Pizza Hut Dallas") ?: "Pizza Hut Dallas"
        set(value) { prefs.edit().putString(KEY_CARD2_NAME, value).apply() }

    var card2Phone: String
        get() = prefs.getString(KEY_CARD2_PHONE, "+15550199000") ?: "+15550199000"
        set(value) { prefs.edit().putString(KEY_CARD2_PHONE, value).apply() }

    var card2Prompt: String
        get() = prefs.getString(KEY_CARD2_PROMPT, "Call Pizza Hut in Dallas TX to check if open & available pizzas") ?: "Call Pizza Hut in Dallas TX to check if open & available pizzas"
        set(value) { prefs.edit().putString(KEY_CARD2_PROMPT, value).apply() }

    // ── Security Utilities ──────────────────────────────────────────────

    /**
     * Returns true if credentials are stored with AES-256 encryption.
     * Returns false if the device fell back to standard SharedPreferences.
     */
    val isEncryptedStorage: Boolean
        get() = prefs is EncryptedSharedPreferences

    /**
     * Returns the absolute path to the secure credential storage directory.
     */
    val secureStoragePath: String
        get() = secureDir.absolutePath

    /**
     * Checks if any API key credentials are currently stored.
     */
    fun hasStoredCredentials(): Boolean {
        return apiKey.isNotBlank() || serpApiKey.isNotBlank() || groqApiKey.isNotBlank()
    }

    /**
     * Securely wipes all stored credentials and configuration.
     * Clears the encrypted preferences and removes the secure storage directory contents.
     */
    fun clear() {
        prefs.edit().clear().apply()
        secureDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "All stored credentials securely wiped")
    }

    companion object {
        private const val TAG = "CallEPreferences"

        /** Encrypted preferences file (AES-256-SIV keys + AES-256-GCM values) */
        private const val ENCRYPTED_PREFS_FILE = "calle_sdk_secure_prefs"

        /** Fallback file for devices that cannot support EncryptedSharedPreferences */
        private const val FALLBACK_PREFS_FILE = "calle_sdk_prefs"

        /** Secure directory created inside app-internal storage */
        private const val SECURE_STORAGE_DIR = "calle_secure_credentials"

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
