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
    private val apiKey: String = "",
    private val defaultModel: String = "llama-3.3-70b-versatile"
) {
    suspend fun complete(
        prompt: String,
        systemPrompt: String = "You are a helpful AI assistant."
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Groq API key cannot be blank."))
        }

        val modelsToTry = linkedSetOf(
            defaultModel,
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant"
        ).toList()

        var lastException: Throwable? = null

        for (model in modelsToTry) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://api.groq.com/openai/v1/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                connection = conn

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

                conn.outputStream.use { os ->
                    os.write(bodyJson.toString().toByteArray(Charsets.UTF_8))
                }

                if (conn.responseCode in 200..299) {
                    val respText = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val content = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    return@withContext Result.success(content.trim())
                } else {
                    val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    lastException = Exception("Groq HTTP ${conn.responseCode} ($model): ${errText.take(150)}")
                    if (conn.responseCode == 401 || conn.responseCode == 403) {
                        return@withContext Result.failure(lastException)
                    }
                }
            } catch (e: Exception) {
                lastException = e
            } finally {
                try {
                    connection?.errorStream?.close()
                } catch (_: Exception) {}
                connection?.disconnect()
            }
        }

        Result.failure(lastException ?: Exception("Groq API request failed across all candidate models."))
    }
}
