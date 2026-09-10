package com.calle.sdk.data

import com.calle.sdk.models.sanitizeE164Phone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ResolvedCallInfo(
    val phone: String,
    val refinedPrompt: String,
    val businessName: String,
    val searchSummary: String
)

class SmartCallResolver(
    private val serpApiKey: String = "",
    private val groqApiKey: String = ""
) {
    private val serpClient = SerpApiClient(serpApiKey)

    /**
     * Resolves user intent:
     * 1. Extracts explicit phone number if present.
     * 2. If missing, executes SerpApi Google Search to find phone number & online details.
     * 3. Uses Groq AI to refine the call prompt with exact business details.
     */
    suspend fun resolveCall(
        rawPrompt: String,
        providedPhone: String = "",
        onStatusUpdate: (String) -> Unit = {}
    ): ResolvedCallInfo = withContext(Dispatchers.IO) {
        val phoneRegex = Regex("""\+?\d{10,15}""")
        val phoneInPrompt = phoneRegex.find(rawPrompt)?.value ?: ""
        val initialPhone = sanitizeE164Phone(providedPhone.ifBlank { phoneInPrompt })

        val isValidPhone = initialPhone.isNotBlank() && initialPhone.length >= 10

        var discoveredPhone = if (isValidPhone) initialPhone else ""
        var serpSummary = ""
        var serpResult = SerpSearchResult()

        if (!isValidPhone) {
            // Phone is missing or wrong/invalid -> Search Google via SerpApi
            onStatusUpdate("1/5 Phone missing/invalid. Searching Google via SerpApi...")
            serpResult = serpClient.searchBusiness(rawPrompt)
            val serpPhone = sanitizeE164Phone(serpResult.phone)
            if (serpPhone.isNotBlank() && serpPhone.length >= 10) {
                discoveredPhone = serpPhone
            }
            serpSummary = "Found on Google: ${serpResult.businessName} ($discoveredPhone)"
        } else {
            onStatusUpdate("1/5 Validated phone ($initialPhone). Fetching Google context...")
            serpResult = serpClient.searchBusiness(rawPrompt)
            val serpPhone = sanitizeE164Phone(serpResult.phone)
            if (serpPhone.isNotBlank() && serpPhone.length >= 10 && discoveredPhone.isBlank()) {
                discoveredPhone = serpPhone
            }
            serpSummary = "Direct phone: $initialPhone"
        }

        val businessName = serpResult.businessName.ifBlank { parseBusinessName(rawPrompt) }
        val address = serpResult.address

        // Step 2: Groq AI Synthesis & Prompt Refinement (For ALL calls)
        onStatusUpdate("2/5 Groq AI 120B Refining Prompt & Business Intent...")
        val groqRefinement = synthesizeWithGroq(
            userPrompt = rawPrompt,
            discoveredPhone = discoveredPhone.ifBlank { initialPhone },
            businessName = businessName,
            address = address,
            snippetInfo = serpResult.snippetInfo
        )

        val finalPhone = when {
            groqRefinement.phone.isNotBlank() && groqRefinement.phone.length >= 10 -> groqRefinement.phone
            discoveredPhone.isNotBlank() && discoveredPhone.length >= 10 -> discoveredPhone
            else -> initialPhone
        }

        ResolvedCallInfo(
            phone = finalPhone,
            refinedPrompt = groqRefinement.refinedPrompt,
            businessName = businessName,
            searchSummary = serpSummary
        )
    }

    private fun parseBusinessName(prompt: String): String {
        var clean = prompt.trim()
        if (clean.startsWith("Call ", ignoreCase = true)) clean = clean.substring(5).trim()
        val parts = clean.split(" and ", " to ", " for ", " at ", " in ")
        return parts.firstOrNull()?.trim()?.take(30) ?: clean.take(30)
    }

    private data class GroqRefinementResult(val phone: String, val refinedPrompt: String)

    private suspend fun synthesizeWithGroq(
        userPrompt: String,
        discoveredPhone: String,
        businessName: String,
        address: String,
        snippetInfo: String
    ): GroqRefinementResult {
        val modelsToTry = listOf("gpt-oss-120b", "openai/gpt-oss-120b", "llama-3.3-70b-versatile", "llama-3.1-8b-instant")

        for (modelName in modelsToTry) {
            try {
                val url = URL("https://api.groq.com/openai/v1/chat/completions")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $groqApiKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 12000
                    readTimeout = 12000
                }

                val systemPrompt = """
                    You are an expert voice agent assistant. Given a user's instruction and Google Search (SerpApi) results, output a JSON object:
                    {
                      "phone": "<E.164 phone number if found, e.g. +15550199000, else empty>",
                      "task": "<Refined call task string starting strictly with 'Call +1... and ...'>"
                    }
                """.trimIndent()

                val userContent = """
                    User Instruction: "$userPrompt"
                    Discovered Phone: "$discoveredPhone"
                    Business Name: "$businessName"
                    Address: "$address"
                    Google Snippets: "$snippetInfo"
                """.trimIndent()

                val bodyJson = JSONObject().apply {
                    put("model", modelName)
                    put("temperature", 0.1)
                    put("max_tokens", 250)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userContent)
                        })
                    })
                }

                connection.outputStream.use { os ->
                    os.write(bodyJson.toString().toByteArray(Charsets.UTF_8))
                }

                if (connection.responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")

                    val jsonMatch = Regex("""\{.*\}""", RegexOption.DOT_MATCHES_ALL).find(content)?.value
                    if (jsonMatch != null) {
                        val parsed = JSONObject(jsonMatch)
                        val phone = sanitizeE164Phone(parsed.optString("phone", discoveredPhone))
                        val task = parsed.optString("task", "")
                        if (task.isNotBlank()) {
                            return GroqRefinementResult(phone = phone, refinedPrompt = task)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Fallback refinement if Groq fails or returns raw text
        val phone = sanitizeE164Phone(discoveredPhone)
        val cleanUser = if (userPrompt.startsWith("Call ", ignoreCase = true)) userPrompt.substring(5).trim() else userPrompt
        val fallbackPrompt = if (phone.isNotBlank()) {
            "Call $phone and ask $businessName at $address regarding $cleanUser."
        } else {
            "Search for $businessName, find their phone number, call them, and execute: $cleanUser"
        }
        return GroqRefinementResult(phone = phone, refinedPrompt = fallbackPrompt)
    }
}
