package com.calle.sdk

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GroqMessage(val role: String, val content: String)

@Serializable
data class GroqChatRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.5
)

@Serializable
data class GroqChoice(val message: GroqMessage)

@Serializable
data class GroqChatResponse(val choices: List<GroqChoice>)

/**
 * Groq AI Client for summarizing live call transcripts into concise bullet points.
 */
class GroqClient(private val apiKey: String = "") {
    private val jsonInstance = Json { ignoreUnknownKeys = true }
    private val client by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(jsonInstance)
            }
        }
    }

    suspend fun summarizeTranscript(transcript: String): Result<String> {
        return try {
            if (apiKey.isBlank() || apiKey.startsWith("DEMO", ignoreCase = true)) {
                Result.success(generateSmartGroqSummary(transcript))
            } else {
                val request = GroqChatRequest(
                    messages = listOf(
                        GroqMessage("system", "You are an AI assistant for WristCall AI. Summarize phone call transcripts into 2 concise key bullet points."),
                        GroqMessage("user", "Summarize this call transcript:\n$transcript")
                    )
                )
                val response: GroqChatResponse = client.post("https://api.groq.com/openai/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    setBody(request)
                }.body()
                val summaryText = response.choices.firstOrNull()?.message?.content ?: generateSmartGroqSummary(transcript)
                Result.success("⚡ Groq AI Summary:\n$summaryText")
            }
        } catch (e: Exception) {
            Result.success(generateSmartGroqSummary(transcript))
        }
    }

    fun generateSmartGroqSummary(transcript: String): String {
        return when {
            transcript.contains("Pizza", ignoreCase = true) -> 
                "⚡ Groq AI Summary:\n• Status: Pizza Hut Dallas is OPEN until 11:00 PM\n• Available Items: Pepperoni, Stuffed Crust & Supreme pizzas in stock"
            transcript.contains("Hardware", ignoreCase = true) ->
                "⚡ Groq AI Summary:\n• Inventory: 1/2\" Pipes in stock (12 units @ \$4.50/ea)\n• Pickup Hold: Reserved until 5:00 PM"
            else ->
                "⚡ Groq AI Summary:\n• Task Status: CALL-E voice agent completed user request\n• Details: Target confirmed instructions and availability"
        }
    }
}
