package com.calle.sdk

import com.calle.sdk.models.CallEventsPage
import com.calle.sdk.models.CallLifecycleEvent
import com.calle.sdk.models.CallOutcome
import com.calle.sdk.models.CallRequest
import com.calle.sdk.models.CallResponse
import com.calle.sdk.models.CallV2ApiRequest
import com.calle.sdk.models.ResultStatus
import com.calle.sdk.models.TranscriptTurn
import com.calle.sdk.models.sanitizeE164Phone
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

/**
 * Custom exceptions for granular CALL-E SDK error handling.
 * Conforms to CALL-E V2 error contracts.
 */
sealed class CallEException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidApiKeyException(message: String = "Invalid or expired CALL-E API key.") : CallEException(message)
    class AccessDeniedException(message: String = "Access forbidden. Verify API key permissions.") : CallEException(message)
    class ResourceNotFoundException(val resourceId: String, message: String = "Resource not found: $resourceId") : CallEException(message)
    class RateLimitExceededException(message: String = "Rate limit exceeded (HTTP 429). Please retry after a delay.") : CallEException(message)
    class ServerException(val statusCode: Int, message: String = "CALL-E server error (HTTP $statusCode).") : CallEException(message)
    class NetworkException(message: String = "Network connection failed.", cause: Throwable? = null) : CallEException(message, cause)
    class ValidationException(message: String) : CallEException(message)

    // V2-specific exceptions:
    class IdempotencyConflictException(val reasonCode: String?, message: String) : CallEException(message)
    class IncompleteInputException(val missingInputs: List<String>, message: String) : CallEException(message)
    class UnsupportedTargetException(val field: String?, message: String) : CallEException(message)
    class InvalidSchemaException(message: String) : CallEException(message)
}

/**
 * Open-source Kotlin SDK Client for CALL-E voice agent integration.
 * Built for the CALL-E V2 API (https://api.heycall-e.com/v2).
 * Falls back to simulation bridge mode when using DEMO_KEY or empty key.
 */
class CallEClient(
    private val apiKey: String = "",
    private val baseUrl: String = "https://api.heycall-e.com/v2"
) {
    private val jsonInstance = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
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
                logger = object : Logger {
                    override fun log(message: String) {}
                }
                // Use LogLevel.NONE to avoid logging PII payload bodies or auth headers
                level = LogLevel.NONE
            }
        }
    }

    /** Check if we are in demo/simulation mode */
    val isDemoMode: Boolean
        get() = apiKey.isBlank() || apiKey.startsWith("DEMO", ignoreCase = true)

    /**
     * Validates if the API key works by making a lightweight read request to GET /v2/calls.
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
     * Dispatches a new phone call task via CALL-E V2 service.
     * Sets the mandatory Idempotency-Key header and handles HTTP 202 Accepted.
     */
    suspend fun dispatchCall(request: CallRequest): Result<CallResponse> {
        val phone = sanitizeE164Phone(request.toPhoneNumber)
        val taskInstructions = request.cleanInstructions()

        if (phone.isBlank()) {
            return Result.failure(CallEException.ValidationException("CallRequest must contain a valid E.164 phone number."))
        }

        if (taskInstructions.isBlank()) {
            return Result.failure(CallEException.ValidationException("CallRequest must contain task instructions."))
        }

        if (!isDemoMode && apiKey.isBlank()) {
            return Result.failure(CallEException.InvalidApiKeyException("API key cannot be blank when initialized in non-demo mode."))
        }

        return try {
            if (isDemoMode) {
                // Fallback simulation mode for offline/demo testing
                delay(800)
                val generatedId = "call_" + UUID.randomUUID().toString().take(8)
                val mockTurns = listOf(
                    TranscriptTurn(offsetSeconds = 1, speaker = "bot", text = "Hello! Calling to execute: $taskInstructions"),
                    TranscriptTurn(offsetSeconds = 4, speaker = "user", text = "Understood. The details are confirmed.")
                )
                Result.success(
                    CallResponse(
                        id = generatedId,
                        billingCallId = "billing_" + UUID.randomUUID().toString().take(12),
                        status = "completed",
                        callOutcomeRaw = CallOutcome.COMPLETED.name.lowercase(),
                        resultStatusRaw = ResultStatus.AVAILABLE.name.lowercase(),
                        task = taskInstructions,
                        phone = phone,
                        transcript = mockTurns,
                        result = mapOf(
                            "summary" to JsonPrimitive("Task instructions successfully completed for $phone."),
                            "task_completed" to JsonPrimitive(true)
                        ),
                        createdAt = System.currentTimeMillis().toString(),
                        completedAt = (System.currentTimeMillis() + 5000).toString()
                    )
                )
            } else {
                val apiRequest = CallV2ApiRequest(
                    task = taskInstructions,
                    phone = phone,
                    resultSchema = request.resultSchema,
                    region = request.region,
                    locale = request.locale,
                    metadata = request.metadata,
                    webhookUrl = request.webhookUrl
                )

                val httpResponse = httpClient.post("$baseUrl/calls") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    header("Idempotency-Key", request.idempotencyKey)
                    setBody(apiRequest)
                }

                val responseBody = httpResponse.bodyAsText()
                android.util.Log.d("CallEClient", "CALL-E V2 dispatchCall status: ${httpResponse.status.value}")

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
     * Queries the live status of an active or completed call run (`GET /v2/calls/{id}`).
     */
    suspend fun getCallStatus(callId: String): Result<CallResponse> {
        if (callId.isBlank()) {
            return Result.failure(CallEException.ValidationException("Call ID cannot be blank."))
        }

        return try {
            if (isDemoMode) {
                delay(400)
                Result.success(
                    CallResponse(
                        id = callId,
                        status = "completed",
                        callOutcomeRaw = CallOutcome.COMPLETED.name.lowercase(),
                        resultStatusRaw = ResultStatus.AVAILABLE.name.lowercase(),
                        createdAt = System.currentTimeMillis().toString(),
                        result = mapOf(
                            "summary" to JsonPrimitive("Simulation call completed successfully."),
                            "task_completed" to JsonPrimitive(true)
                        )
                    )
                )
            } else {
                val httpResponse = httpClient.get("$baseUrl/calls/$callId") {
                    header("Authorization", "Bearer $apiKey")
                }
                val responseBody = httpResponse.bodyAsText()
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
     * Polls the live status of an active call run according to CALL-E V2 readiness rules:
     * Continues polling while `result_status` is `PENDING`, and completes once the result
     * is finalized (`AVAILABLE`, `UNAVAILABLE`, or `NOT_APPLICABLE`).
     *
     * @param callId The ID of the call to track.
     * @param pollIntervalMs Interval between status queries in milliseconds (default: 2000ms).
     * @param maxAttempts Maximum polling iterations before stopping (default: 60, approx 2 minutes).
     */
    fun pollCallStatus(
        callId: String,
        pollIntervalMs: Long = 2000L,
        maxAttempts: Int = 60
    ): Flow<CallResponse> = flow {
        var attempts = 0
        while (attempts < maxAttempts) {
            val statusResult = getCallStatus(callId)
            if (statusResult.isSuccess) {
                val response = statusResult.getOrThrow()
                emit(response)

                // CALL-E V2 Readiness Rule: Terminate only when result_status is no longer pending!
                if (response.isResultReady) {
                    break
                }
            } else {
                throw statusResult.exceptionOrNull() ?: Exception("Polling status failed for call $callId")
            }
            attempts++
            delay(pollIntervalMs)
        }
    }

    /**
     * Cancels an active call prior to provider submission (`POST /v2/calls/{id}/cancel`).
     */
    suspend fun cancelCall(callId: String): Result<CallResponse> {
        if (callId.isBlank()) {
            return Result.failure(CallEException.ValidationException("Call ID cannot be blank."))
        }

        return try {
            if (isDemoMode) {
                Result.success(
                    CallResponse(
                        id = callId,
                        status = "canceled",
                        resultStatusRaw = ResultStatus.NOT_APPLICABLE.name.lowercase()
                    )
                )
            } else {
                val httpResponse = httpClient.post("$baseUrl/calls/$callId/cancel") {
                    header("Authorization", "Bearer $apiKey")
                }
                val responseBody = httpResponse.bodyAsText()
                if (httpResponse.status.value in 200..299) {
                    val parsed = jsonInstance.decodeFromString<CallResponse>(responseBody)
                    Result.success(parsed)
                } else {
                    Result.failure(handleHttpError(httpResponse.status.value, responseBody, callId))
                }
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkOrUnknownException(e))
        }
    }

    /**
     * Fetches a paginated page of execution lifecycle events (`GET /v2/calls/{id}/events`).
     */
    suspend fun getCallEvents(
        callId: String,
        cursor: String? = null,
        limit: Int = 50
    ): Result<CallEventsPage> {
        if (callId.isBlank()) {
            return Result.failure(CallEException.ValidationException("Call ID cannot be blank."))
        }

        return try {
            if (isDemoMode) {
                Result.success(
                    CallEventsPage(
                        events = listOf(
                            CallLifecycleEvent(
                                id = "$callId:ev:1",
                                type = "call.accepted",
                                callId = callId,
                                message = "Call instructions prepared and accepted."
                            )
                        ),
                        nextCursor = null
                    )
                )
            } else {
                val httpResponse = httpClient.get("$baseUrl/calls/$callId/events") {
                    header("Authorization", "Bearer $apiKey")
                    parameter("limit", limit.coerceIn(1, 100))
                    if (!cursor.isNullOrBlank()) {
                        parameter("cursor", cursor)
                    }
                }
                val responseBody = httpResponse.bodyAsText()
                if (httpResponse.status.value in 200..299) {
                    val parsed = jsonInstance.decodeFromString<CallEventsPage>(responseBody)
                    Result.success(parsed)
                } else {
                    Result.failure(handleHttpError(httpResponse.status.value, responseBody, callId))
                }
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkOrUnknownException(e))
        }
    }

    /**
     * Reactive stream of lifecycle events and speech updates for a call.
     * Retains cursor and emits new events sequentially without duplicates.
     */
    fun pollCallEvents(
        callId: String,
        pollIntervalMs: Long = 1000L,
        maxAttempts: Int = 120
    ): Flow<CallLifecycleEvent> = flow {
        var currentCursor: String? = null
        val seenEventIds = mutableSetOf<String>()
        var attempts = 0

        while (attempts < maxAttempts) {
            val pageResult = getCallEvents(callId, cursor = currentCursor)
            if (pageResult.isSuccess) {
                val page = pageResult.getOrThrow()
                for (event in page.events) {
                    if (seenEventIds.add(event.id)) {
                        emit(event)
                        currentCursor = event.id
                    }
                }

                if (page.events.any { it.type in listOf("call.completed", "call.failed", "call.canceled") }) {
                    break
                }
            }
            attempts++
            delay(pollIntervalMs)
        }
    }

    /**
     * Fetches the complete audio transcript for a call ID directly from V2 top-level transcript.
     */
    suspend fun getTranscript(callId: String, defaultPrompt: String = ""): Result<String> {
        if (callId.isBlank()) {
            return Result.failure(CallEException.ValidationException("Call ID cannot be blank."))
        }

        return try {
            if (isDemoMode) {
                delay(300)
                val mockTranscript = """
                    [AGENT]: Hello! I'm calling to inquire: ${defaultPrompt.ifBlank { "Check table reservation tonight." }}
                    [RECIPIENT]: Hi there! Sure, we have confirmed that for you.
                    [AGENT]: Thank you so much! Have a great day.
                """.trimIndent()
                Result.success(mockTranscript)
            } else {
                val statusResult = getCallStatus(callId)
                if (statusResult.isSuccess) {
                    val response = statusResult.getOrThrow()
                    val turns = response.transcript.ifEmpty { response.allTranscriptTurns }

                    if (turns.isNotEmpty()) {
                        val formattedTurns = turns.joinToString("\n") { turn ->
                            val speakerLabel = if (turn.speaker.equals("bot", ignoreCase = true) || turn.speaker.equals("agent", ignoreCase = true)) "[AGENT]" else "[RECIPIENT]"
                            "$speakerLabel: ${turn.text}"
                        }
                        Result.success(formattedTurns)
                    } else if (!response.bestSummary.isNullOrBlank()) {
                        val summaryTranscript = """
                            [AGENT]: Task: ${response.task.ifBlank { defaultPrompt }}
                            [RECIPIENT]: ${response.bestSummary}
                        """.trimIndent()
                        Result.success(summaryTranscript)
                    } else {
                        Result.failure(NoSuchElementException("No transcript turns available for call ID: $callId"))
                    }
                } else {
                    Result.failure(statusResult.exceptionOrNull() ?: Exception("Failed to fetch transcript for call $callId"))
                }
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkOrUnknownException(e))
        }
    }

    private fun handleHttpError(statusCode: Int, responseBody: String, resourceId: String = ""): Exception {
        val parsedMsg = parseApiErrorMessage(responseBody, statusCode)

        return try {
            val json = org.json.JSONObject(responseBody)
            val errObj = if (json.has("error") && json.get("error") is org.json.JSONObject) {
                json.getJSONObject("error")
            } else null

            val code = errObj?.optString("code", "") ?: ""

            when {
                statusCode == 400 && code == "result_schema_invalid" -> {
                    CallEException.InvalidSchemaException(parsedMsg)
                }
                statusCode == 409 -> {
                    val reason = errObj?.optJSONObject("details")?.optString("reason_code")
                    CallEException.IdempotencyConflictException(reason, parsedMsg)
                }
                statusCode == 422 && code == "input_incomplete" -> {
                    val missing = mutableListOf<String>()
                    val missingArr = errObj?.optJSONObject("details")?.optJSONArray("missing_inputs")
                    if (missingArr != null) {
                        for (i in 0 until missingArr.length()) {
                            missing.add(missingArr.getString(i))
                        }
                    }
                    CallEException.IncompleteInputException(missing, parsedMsg)
                }
                statusCode == 422 && (code == "unsupported_region" || code == "unsupported_language") -> {
                    val field = errObj?.optJSONObject("details")?.optString("field")
                    CallEException.UnsupportedTargetException(field, parsedMsg)
                }
                statusCode == 401 -> CallEException.InvalidApiKeyException(parsedMsg)
                statusCode == 403 -> CallEException.AccessDeniedException(parsedMsg)
                statusCode == 404 -> CallEException.ResourceNotFoundException(resourceId.ifBlank { "requested resource" }, parsedMsg)
                statusCode == 429 -> CallEException.RateLimitExceededException(parsedMsg)
                statusCode in 500..599 -> CallEException.ServerException(statusCode, parsedMsg)
                else -> Exception(parsedMsg)
            }
        } catch (_: Exception) {
            when (statusCode) {
                401 -> CallEException.InvalidApiKeyException(parsedMsg)
                403 -> CallEException.AccessDeniedException(parsedMsg)
                404 -> CallEException.ResourceNotFoundException(resourceId.ifBlank { "requested resource" }, parsedMsg)
                429 -> CallEException.RateLimitExceededException(parsedMsg)
                in 500..599 -> CallEException.ServerException(statusCode, parsedMsg)
                else -> Exception(parsedMsg)
            }
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
