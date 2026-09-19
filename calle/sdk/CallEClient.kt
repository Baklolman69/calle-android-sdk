package com.calle.sdk

import com.calle.sdk.models.CallEApiRequest
import com.calle.sdk.models.CallEStatus
import com.calle.sdk.models.CallRequest
import com.calle.sdk.models.CallResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

/**
 * Custom exceptions for granular CALL-E SDK error handling.
 */
sealed class CallEException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidApiKeyException(message: String = "Invalid or expired CALL-E API key.") : CallEException(message)
    class AccessDeniedException(message: String = "Access forbidden. Verify API key permissions.") : CallEException(message)
    class ResourceNotFoundException(val resourceId: String, message: String = "Resource not found: $resourceId") : CallEException(message)
    class RateLimitExceededException(message: String = "Rate limit exceeded (HTTP 429). Please retry after a delay.") : CallEException(message)
    class ServerException(val statusCode: Int, message: String = "CALL-E server error (HTTP $statusCode).") : CallEException(message)
    class NetworkException(message: String = "Network connection failed.", cause: Throwable? = null) : CallEException(message, cause)
    class ValidationException(message: String) : CallEException(message)
}

/**
 * Open-source Kotlin SDK Client for CALL-E voice agent integration.
 * Uses the real CALL-E API at https://api.heycall-e.com/v1
 * Falls back to simulation bridge mode when using DEMO_KEY.
 */
class CallEClient(
    private val apiKey: String = "",
    private val baseUrl: String = "https://api.heycall-e.com/v1"
) {
    private val jsonInstance = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private val httpClient by lazy {
        HttpClient(Android) {
            engine {
                connectTimeout = 15_000
                socketTimeout = 30_000
            }
            install(ContentNegotiation) {
                json(jsonInstance)
            }
            install(Logging) {
                logger = Logger.DEFAULT
                // Issue #3 Fix: Use LogLevel.NONE to avoid logging PII payload bodies or auth headers
                level = LogLevel.NONE
            }
        }
    }

    /** Check if we are in demo/simulation mode */
    val isDemoMode: Boolean
        get() = apiKey.isBlank() || apiKey.startsWith("DEMO", ignoreCase = true)

    /**
     * Validates if the API key works by making a lightweight read request.
     * Issue #6 Fix: Uses GET /calls and strictly checks 200..299 status code.
     */
    suspend fun validateApiKey(): Boolean {
        if (isDemoMode) return true
        return try {
            val response = httpClient.get("$baseUrl/calls") {
                header("Authorization", "Bearer $apiKey")
            }
            response.status.value in 200..299
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Dispatches a new phone call task via CALL-E voice agent service.
     */
    suspend fun dispatchCall(request: CallRequest): Result<CallResponse> {
        // Edge Case: Validate request parameters before dispatching
        val taskString = request.toTaskString()
        if (taskString.isBlank()) {
            return Result.failure(CallEException.ValidationException("CallRequest must contain a target phone number or task prompt."))
        }

        if (!isDemoMode && apiKey.isBlank()) {
            return Result.failure(CallEException.InvalidApiKeyException("API key cannot be blank when initialized in non-demo mode."))
        }

        return try {
            if (isDemoMode) {
                // Fallback simulation mode for hackathon offline/demo testing
                delay(800)
                val generatedId = "call_" + UUID.randomUUID().toString().take(8)
                Result.success(
                    CallResponse(
                        id = generatedId,
                        status = CallEStatus.CALL_IN_PROGRESS.name,
                        createdAt = System.currentTimeMillis().toString()
                    )
                )
            } else {
                val apiRequest = CallEApiRequest(task = taskString)

                val httpResponse = httpClient.post("$baseUrl/calls") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    setBody(apiRequest)
                }

                val responseBody = httpResponse.bodyAsText()
                // Issue #3 Fix: Log status code only, do not dump PII payload body
                android.util.Log.d("CallEClient", "CALL-E dispatchCall status: ${httpResponse.status.value}")

                if (httpResponse.status.value in 200..299) {
                    val parsed = jsonInstance.decodeFromString<CallResponse>(responseBody)
                    Result.success(parsed)
                } else {
                    val errorException = handleHttpError(httpResponse.status.value, responseBody)
                    Result.failure(errorException)
                }
            }
        } catch (e: Exception) {
            val mappedException = mapNetworkOrUnknownException(e)
            android.util.Log.e("CallEClient", "dispatchCall failed: ${mappedException.message}")
            Result.failure(mappedException)
        }
    }

    /**
     * Queries the live status of an active or completed call run.
     */
    suspend fun getCallStatus(callId: String): Result<CallResponse> {
        if (callId.isBlank()) {
            return Result.failure(CallEException.ValidationException("Call ID cannot be blank."))
        }

        return try {
            if (isDemoMode) {
                delay(500)
                Result.success(
                    CallResponse(
                        id = callId,
                        status = CallEStatus.SUCCESS.name,
                        createdAt = System.currentTimeMillis().toString()
                    )
                )
            } else {
                val httpResponse = httpClient.get("$baseUrl/calls/$callId") {
                    header("Authorization", "Bearer $apiKey")
                }
                val responseBody = httpResponse.bodyAsText()
                // Issue #3 Fix: Log status code only, do not dump PII payload body
                android.util.Log.d("CallEClient", "getCallStatus [$callId] status: ${httpResponse.status.value}")
                if (httpResponse.status.value in 200..299) {
                    val parsed = jsonInstance.decodeFromString<CallResponse>(responseBody)
                    Result.success(parsed)
                } else {
                    val errorException = handleHttpError(httpResponse.status.value, responseBody, callId)
                    Result.failure(errorException)
                }
            }
        } catch (e: Exception) {
            val mappedException = mapNetworkOrUnknownException(e)
            android.util.Log.e("CallEClient", "getCallStatus failed for $callId: ${mappedException.message}")
            Result.failure(mappedException)
        }
    }

    /**
     * Fetches the complete audio transcript and AI summary for a call ID.
     * Issue #2 Fix: Does NOT return fabricated transcripts when API returns no data.
     */
    suspend fun getTranscript(callId: String, defaultPrompt: String = ""): Result<String> {
        if (callId.isBlank()) {
            return Result.failure(CallEException.ValidationException("Call ID cannot be blank."))
        }

        return try {
            if (isDemoMode) {
                delay(400)
                val mockTranscript = """
                    [DEMO - AGENT]: Hello! I'm calling on behalf of WristCall AI to execute a task.
                    [DEMO - RECIPIENT]: Hi there! Sure, how can I help you?
                    [DEMO - AGENT]: ${defaultPrompt.ifBlank { "Table for 4 people tonight at 8:00 PM under the name Alex." }}
                    [DEMO - RECIPIENT]: Perfect, I have processed that for you. Confirmed!
                    [DEMO - AGENT]: Thank you so much! Have a great day.
                """.trimIndent()
                Result.success(mockTranscript)
            } else {
                val statusResult = getCallStatus(callId)
                if (statusResult.isSuccess) {
                    val response = statusResult.getOrThrow()
                    val turns = response.allTranscriptTurns
                    val summaryText = response.bestSummary

                    if (!turns.isNullOrEmpty()) {
                        val formattedTurns = turns.joinToString("\n") { turn ->
                            val speakerLabel = if (turn.speaker.equals("bot", ignoreCase = true) || turn.speaker.equals("agent", ignoreCase = true)) "[AGENT]" else "[RECIPIENT]"
                            "$speakerLabel: ${turn.text}"
                        }
                        Result.success(formattedTurns)
                    } else if (!summaryText.isNullOrBlank()) {
                        val summaryTranscript = """
                            [AGENT]: Task Instructions: ${defaultPrompt.ifBlank { response.task }}
                            [RECIPIENT]: $summaryText
                        """.trimIndent()
                        Result.success(summaryTranscript)
                    } else {
                        // Issue #2 Fix: Return failure when no transcript or summary is available
                        Result.failure(NoSuchElementException("No transcript turns or summary available for call ID: $callId"))
                    }
                } else {
                    val exception = statusResult.exceptionOrNull() ?: Exception("Failed to fetch status for call $callId")
                    Result.failure(exception)
                }
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkOrUnknownException(e))
        }
    }

    private fun handleHttpError(statusCode: Int, responseBody: String, resourceId: String = ""): Exception {
        val parsedMsg = parseApiErrorMessage(responseBody, statusCode)
        return when (statusCode) {
            401 -> CallEException.InvalidApiKeyException(parsedMsg)
            403 -> CallEException.AccessDeniedException(parsedMsg)
            404 -> CallEException.ResourceNotFoundException(resourceId.ifBlank { "requested resource" }, parsedMsg)
            429 -> CallEException.RateLimitExceededException(parsedMsg)
            in 500..599 -> CallEException.ServerException(statusCode, parsedMsg)
            else -> Exception(parsedMsg)
        }
    }

    private fun mapNetworkOrUnknownException(e: Exception): Exception {
        return when (e) {
            is CallEException -> e
            is UnknownHostException -> CallEException.NetworkException("No internet connection or DNS resolution failed.", e)
            is SocketTimeoutException -> CallEException.NetworkException("Connection timed out while communicating with CALL-E server.", e)
            is IOException -> CallEException.NetworkException("I/O error during network operation: ${e.message}", e)
            else -> e
        }
    }

    private fun parseApiErrorMessage(body: String, statusCode: Int): String {
        if (body.isBlank()) return "CALL-E API Error $statusCode"
        if (body.trimStart().startsWith("<")) {
            // HTML error page (e.g. Cloudflare / reverse proxy 502/504)
            return "CALL-E API HTTP $statusCode HTML response: ${body.take(60)}..."
        }

        return try {
            val json = org.json.JSONObject(body)
            if (json.has("error")) {
                val errVal = json.get("error")
                if (errVal is org.json.JSONObject) {
                    errVal.optString("message", errVal.optString("code", errVal.toString()))
                } else {
                    errVal.toString()
                }
            } else if (json.has("message")) {
                json.getString("message")
            } else if (json.has("detail")) {
                val detail = json.get("detail")
                if (detail is org.json.JSONArray && detail.length() > 0) {
                    val first = detail.getJSONObject(0)
                    first.optString("msg", detail.toString())
                } else {
                    detail.toString()
                }
            } else {
                "CALL-E API Error $statusCode"
            }
        } catch (_: Exception) {
            "CALL-E API Error $statusCode"
        }
    }

    fun close() {
        httpClient.close()
    }
}

