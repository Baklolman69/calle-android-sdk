package com.calle.sdk.data

import com.calle.sdk.models.ResultSchema
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
    val searchSummary: String,
    val task: String = refinedPrompt,
    val resultSchema: ResultSchema = ResultSchema.default()
)

class SmartCallResolver(
    private val serpApiKey: String = "",
    private val groqApiKey: String = "",
    private val groqModel: String = "llama-3.3-70b-versatile"
) {
    private val serpClient = SerpApiClient(serpApiKey)

    /**
     * Resolves user intent for CALL-E V2:
     * 1. Extracts explicit phone number if present, or resolves contact name from device contact store if Context & READ_CONTACTS permission are provided.
     * 2. If missing (or if forceSearch = true), executes SerpApi Google Search to find phone number & online details.
     * 3. Uses Groq AI to refine the call task prompt with exact business details.
     */
    suspend fun resolveCall(
        rawPrompt: String,
        providedPhone: String = "",
        context: android.content.Context? = null,
        forceSearch: Boolean = false,
        onStatusUpdate: (String) -> Unit = {}
    ): ResolvedCallInfo = withContext(Dispatchers.IO) {
        val phoneInPrompt = com.calle.sdk.models.FORMATTED_PHONE_REGEX.find(rawPrompt)?.value ?: ""
        var initialPhone = sanitizeE164Phone(providedPhone.ifBlank { phoneInPrompt })

        var deviceContactName = ""
        // Step 0: Try resolving from user's device mobile contacts if permission is granted & phone is missing
        if (initialPhone.isBlank() && context != null) {
            val contactResolver = DeviceContactResolver()
            val candidateName = contactResolver.extractNameFromPrompt(rawPrompt)
            val matchedContact = contactResolver.searchContact(context, candidateName)
            if (matchedContact != null) {
                initialPhone = matchedContact.phone
                deviceContactName = matchedContact.name
                onStatusUpdate("Matched device contact: ${matchedContact.name} (${matchedContact.phone})")
            }
        }

        val isValidPhone = initialPhone.isNotBlank() && initialPhone.length >= 10

        var discoveredPhone = if (isValidPhone) initialPhone else ""
        var serpSummary = ""
        var serpResult = SerpSearchResult()

        if (!isValidPhone || forceSearch) {
            // Phone is missing/invalid or search explicitly forced -> Search Google via SerpApi
            onStatusUpdate("1/5 Phone missing/invalid. Searching Google via SerpApi...")
            serpResult = serpClient.searchBusiness(rawPrompt)
            val serpPhone = sanitizeE164Phone(serpResult.phone)
            if (serpPhone.isNotBlank() && serpPhone.length >= 10 && discoveredPhone.isBlank()) {
                discoveredPhone = serpPhone
            }
            serpSummary = "Found on Google: ${serpResult.businessName} ($discoveredPhone)"
        } else {
            onStatusUpdate("1/5 Validated phone ($initialPhone). Skipping Google Search.")
            serpSummary = if (deviceContactName.isNotBlank()) {
                "Device contact: $deviceContactName ($initialPhone)"
            } else {
                "Direct phone: $initialPhone"
            }
        }

        val businessName = serpResult.businessName.ifBlank { parseBusinessName(rawPrompt) }
        val address = serpResult.address

        // Step 2: Groq AI Synthesis & Task Refinement
        // PRIVACY GUARD: Never leak device contact phone numbers to external third-party LLMs
        val phoneForSynthesis = if (deviceContactName.isNotBlank()) "" else discoveredPhone.ifBlank { initialPhone }

        onStatusUpdate("2/5 Groq AI Refining Task & Business Intent...")
        val groqRefinement = synthesizeWithGroq(
            userPrompt = rawPrompt,
            discoveredPhone = phoneForSynthesis,
            businessName = businessName,
            address = address,
            snippetInfo = serpResult.snippetInfo
        )

        // Prioritize explicit / verified phone number
        val finalPhone = when {
            initialPhone.isNotBlank() && initialPhone.length >= 10 -> initialPhone
            discoveredPhone.isNotBlank() && discoveredPhone.length >= 10 -> discoveredPhone
            groqRefinement.phone.isNotBlank() && groqRefinement.phone.length >= 10 -> groqRefinement.phone
            else -> ""
        }

        val taskString = groqRefinement.refinedTask.ifBlank {
            buildFallbackResult(rawPrompt, finalPhone, businessName, address).refinedTask
        }

        ResolvedCallInfo(
            phone = finalPhone,
            refinedPrompt = taskString,
            businessName = businessName,
            searchSummary = serpSummary,
            task = taskString,
            resultSchema = ResultSchema.default()
        )
    }

    private fun parseBusinessName(prompt: String): String {
        var clean = prompt.trim()
        if (clean.startsWith("Call ", ignoreCase = true)) clean = clean.substring(5).trim()
        val parts = clean.split(" and ", " to ", " for ", " at ", " in ")
        return parts.firstOrNull()?.trim()?.take(30) ?: clean.take(30)
    }

    private data class GroqRefinementResult(val phone: String, val refinedTask: String)

    private suspend fun synthesizeWithGroq(
        userPrompt: String,
        discoveredPhone: String,
        businessName: String,
        address: String,
        snippetInfo: String
    ): GroqRefinementResult {
        if (groqApiKey.isBlank()) {
            return buildFallbackResult(userPrompt, discoveredPhone, businessName, address)
        }

        val modelsToTry = linkedSetOf(
            groqModel,
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant"
        ).toList()

        for (modelName in modelsToTry) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://api.groq.com/openai/v1/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $groqApiKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                connection = conn

                val systemPrompt = """
                    You are an expert voice agent assistant. Given a user's instruction and Google Search (SerpApi) results, output a clean JSON object:
                    {
                      "phone": "<E.164 phone number if found, e.g. +15550199000, else empty>",
                      "task": "<Conversational task instruction for CALL-E V2. Do NOT prepend 'Call +1... and'>"
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

                conn.outputStream.use { os ->
                    os.write(bodyJson.toString().toByteArray(Charsets.UTF_8))
                }

                if (conn.responseCode in 200..299) {
                    val respText = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")

                    val jsonMatch = Regex("""\{[\s\S]*?\}""").find(content)?.value
                    if (jsonMatch != null) {
                        val parsed = JSONObject(jsonMatch)
                        val phone = sanitizeE164Phone(parsed.optString("phone", discoveredPhone))
                        var task = parsed.optString("task", "")
                        // Ensure clean V2 task format (not prefixed with 'Call +1... and')
                        if (task.startsWith("Call ", ignoreCase = true)) {
                            val andIdx = task.indexOf(" and ", ignoreCase = true)
                            task = if (andIdx != -1) task.substring(andIdx + 5).trim() else task.substring(5).trim()
                        }
                        if (task.isNotBlank()) {
                            return GroqRefinementResult(phone = phone, refinedTask = task)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    connection?.errorStream?.close()
                } catch (_: Exception) {}
                connection?.disconnect()
            }
        }

        return buildFallbackResult(userPrompt, discoveredPhone, businessName, address)
    }

    private fun buildFallbackResult(
        userPrompt: String,
        discoveredPhone: String,
        businessName: String,
        address: String
    ): GroqRefinementResult {
        val phone = sanitizeE164Phone(discoveredPhone)
        var cleanUser = userPrompt.trim()
        if (cleanUser.startsWith("Call ", ignoreCase = true)) {
            cleanUser = cleanUser.substring(5).trim()
        }
        var taskContent = cleanUser
        if (businessName.isNotBlank() && taskContent.startsWith(businessName, ignoreCase = true)) {
            taskContent = taskContent.substring(businessName.length).trim()
            if (taskContent.startsWith("and ", ignoreCase = true)) taskContent = taskContent.substring(4).trim()
            if (taskContent.startsWith("to ", ignoreCase = true)) taskContent = taskContent.substring(3).trim()
            if (taskContent.startsWith("regarding ", ignoreCase = true)) taskContent = taskContent.substring(10).trim()
            if (taskContent.startsWith("for ", ignoreCase = true)) taskContent = taskContent.substring(4).trim()
        }

        val fallbackTask = if (businessName.isNotBlank() && address.isNotBlank()) {
            "Ask $businessName at $address regarding $taskContent."
        } else if (businessName.isNotBlank() && taskContent.isNotBlank()) {
            "Ask $businessName regarding $taskContent."
        } else if (businessName.isNotBlank()) {
            "Ask $businessName regarding business details and services."
        } else {
            cleanUser.ifBlank { "Inquire about business details and services." }
        }
        return GroqRefinementResult(phone = phone, refinedTask = fallbackTask)
    }
}
