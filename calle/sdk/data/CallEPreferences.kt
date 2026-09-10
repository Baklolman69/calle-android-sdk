package com.calle.sdk.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Shared Preferences helper for persisting CALL-E API Key and default settings.
 */
class CallEPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("calle_sdk_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value).apply()
        }

    var defaultPhoneNumber: String
        get() = prefs.getString(KEY_DEFAULT_PHONE, "+15550192834") ?: "+15550192834"
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_PHONE, value).apply()
        }

    var isSetupComplete: Boolean
        get() = apiKey.isNotBlank()
        set(_) {}

    var card1Name: String
        get() = prefs.getString(KEY_CARD1_NAME, "🔍 Search & Call Cattleack BBQ") ?: "🔍 Search & Call Cattleack BBQ"
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

    var serpApiKey: String
        get() = prefs.getString(KEY_SERP_API_KEY, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SERP_API_KEY, value).apply() }

    var groqApiKey: String
        get() = prefs.getString(KEY_GROQ_API_KEY, "") ?: ""
        set(value) { prefs.edit().putString(KEY_GROQ_API_KEY, value).apply() }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_API_KEY = "calle_api_key"
        private const val KEY_DEFAULT_PHONE = "calle_default_phone"
        private const val KEY_SERP_API_KEY = "serp_api_key"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_CARD1_NAME = "card1_name"
        private const val KEY_CARD1_PHONE = "card1_phone"
        private const val KEY_CARD1_PROMPT = "card1_prompt"
        private const val KEY_CARD2_NAME = "card2_name"
        private const val KEY_CARD2_PHONE = "card2_phone"
        private const val KEY_CARD2_PROMPT = "card2_prompt"
    }
}
