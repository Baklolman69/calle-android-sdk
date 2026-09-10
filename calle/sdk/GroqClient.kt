package com.calle.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for executing LLM completions via Groq Cloud API.
 */
class GroqClient(
    private val apiKey: String = ""
) {
    suspend fun complete(
        prompt: String,
        systemPrompt: String = "You are a helpful AI assistant."
    ): Result<String> = withContext(Dispatchers.IO) {
        val modelsToTry = listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "mixtral-8x7b-32768"
        )

        for (model in modelsToTry) {
            try {
                val url = URL("https://api.groq.com/openai/v1/chat/completions")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val bodyJson = JSONObject().apply {
                    put("model", model)
                    put("temperature", 0.2)
                    put("max_tokens", 500)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }

                connection.outputStream.use { os ->
                    os.write(bodyJson.toString().toByteArray(Charsets.UTF_8))
                }

                if (connection.responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val content = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    return@withContext Result.success(content.trim())
                }
            } catch (_: Exception) {
                // Try next model
            }
        }

        Result.failure(Exception("Groq API request failed across all candidate models."))
    }
}
