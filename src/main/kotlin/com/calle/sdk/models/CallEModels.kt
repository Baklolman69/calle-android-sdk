package com.calle.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/**
 * Core CALL-E SDK Models representing API requests, responses, and call execution state.
 * Fully compliant with the CALL-E V2 API specification (https://docs.heycall-e.com/calls).
 */

/**
 * Closed scalar-object JSON Schema for structured result extraction.
 * CALL-E V2 strictly requires:
 * - type: "object"
 * - additionalProperties: false
 * - At most 32 scalar properties (string, boolean, integer, number)
 * - No nested objects, arrays, or null unions.
 */
@Serializable
data class ResultSchema(
    val type: String = "object",
    val properties: Map<String, SchemaProperty>,
    val required: List<String> = properties.keys.toList(),
    @SerialName("additionalProperties") val additionalProperties: Boolean = false
) {
    companion object {
        /**
         * Standard default schema extracting a summary and completion status.
         */
        fun default(): ResultSchema = ResultSchema(
            properties = mapOf(
                "summary" to SchemaProperty(type = "string", description = "Summary of the conversation outcome"),
                "task_completed" to SchemaProperty(type = "boolean", description = "Whether the caller's objective was achieved")
            )
        )
    }
}

@Serializable
data class SchemaProperty(
    val type: String, // "string", "boolean", "integer", "number"
    val description: String? = null
)

/**
 * Developer-facing request model for dispatching a phone call task in V2.
 */
data class CallRequest(
    val toPhoneNumber: String,
    val promptInstructions: String,
    val resultSchema: ResultSchema = ResultSchema.default(),
    val region: String? = null,
    val locale: String? = null,
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val metadata: Map<String, String>? = null,
    val webhookUrl: String? = null,
    val taskCategory: String = "GENERAL"
) {
    /**
     * Cleans and formats the task instructions for the CALL-E V2 API.
     * Removes redundant phone numbers or 'Call ... and' prefixes from the conversational prompt.
     */
    fun cleanInstructions(): String {
        var prompt = promptInstructions.trim()
        val extractedPhone = FORMATTED_PHONE_REGEX.find(prompt)?.value ?: ""
        if (extractedPhone.isNotBlank()) {
            prompt = prompt.replace(extractedPhone, "").trim()
        }

        if (prompt.startsWith("Call ", ignoreCase = true)) {
            prompt = prompt.substring(5).trim()
        }

        if (prompt.startsWith("and ", ignoreCase = true)) {
            prompt = prompt.substring(4).trim()
        }

        val actionKeywords = listOf("ask", "check", "inquire", "order", "book", "reserve", "find out", "tell", "request", "get", "when", "what", "how", "verify")
        val hasAction = actionKeywords.any { prompt.contains(it, ignoreCase = true) }

        return if (!hasAction && prompt.isNotBlank()) {
            "Ask $prompt regarding business hours, services offered, and general inquiries."
        } else {
            prompt.ifBlank { "Inquire about business details and services." }
        }
    }

    /**
     * Backward-compatibility helper for legacy V1 integrations.
     */
    fun toTaskString(): String {
        val phone = sanitizeE164Phone(toPhoneNumber)
        val instructions = cleanInstructions()
        return if (phone.isNotBlank()) {
            "Call $phone and $instructions"
        } else {
            "Search for $instructions, find their phone number, call them, and execute the request."
        }
    }
}

/**
 * Matches common international and national phone formats including punctuated variants.
 */
val FORMATTED_PHONE_REGEX = Regex("""(?:\+?\d{1,3}[\s-]?)?\(?\d{2,4}\)?[\s.-]?\d{3,4}[\s.-]?\d{3,5}|\+?\d{8,15}""")

private val E164_REGEX = Regex("""^\+[1-9]\d{7,14}$""")

/**
 * Validates whether a given phone string conforms strictly to ITU-T E.164 format.
 * Format: +<country_code><national_number> (total length 8 to 15 digits, starting with country code 1-9).
 */
fun isValidE164Phone(phone: String): Boolean {
    return phone.isNotBlank() && E164_REGEX.matches(phone)
}

/**
 * Sanitizes phone numbers into strict E.164 format (+1XXXXXXXXXX).
 * Returns an empty string if the raw input cannot be parsed into a valid E.164 number.
 */
fun sanitizeE164Phone(rawPhone: String): String {
    val clean = rawPhone.filter { it.isDigit() || it == '+' }
    if (clean.isBlank()) return ""

    val candidate = when {
        clean.startsWith("+") -> clean
        clean.length == 10 -> "+1$clean"
        clean.length == 11 && clean.startsWith("1") -> "+$clean"
        clean.length in 8..15 -> "+$clean"
        else -> ""
    }

    return if (isValidE164Phone(candidate)) candidate else ""
}

/**
 * Wire format for CALL-E V2 REST API POST body (`POST /v2/calls`).
 */
@Serializable
data class CallV2ApiRequest(
    val task: String,
    val phone: String,
    @SerialName("result_schema") val resultSchema: ResultSchema,
    val region: String? = null,
    val locale: String? = null,
    val metadata: Map<String, String>? = null,
    @SerialName("webhook_url") val webhookUrl: String? = null
)

/**
 * Legacy V1 Wire format (kept for backwards compatibility).
 */
@Serializable
data class CallEApiRequest(
    val task: String
)

/**
 * Telephony execution outcome.
 */
enum class CallOutcome {
    COMPLETED,
    NO_ANSWER,
    BUSY,
    DECLINED,
    UNKNOWN
}

/**
 * Business result readiness status in V2.
 * The SDK polls until result_status is not PENDING.
 */
enum class ResultStatus {
    PENDING,
    AVAILABLE,
    UNAVAILABLE,
    NOT_APPLICABLE,
    UNKNOWN
}

/**
 * Structured error returned by CALL-E V2 on execution or result extraction failure.
 */
@Serializable
data class CallErrorDetail(
    val code: String = "",
    val message: String = "",
    @SerialName("detail_code") val detailCode: String? = null,
    val details: JsonObject? = null
)

/**
 * Complete Call representation returned by CALL-E V2 (`GET /v2/calls/{id}` or `POST /v2/calls`).
 */
@Serializable
data class CallResponse(
    val id: String = "",
    @SerialName("call_id") val billingCallId: String? = null,
    val status: String = "",
    @SerialName("call_outcome") val callOutcomeRaw: String? = null,
    @SerialName("result_status") val resultStatusRaw: String? = null,
    val task: String = "",
    val phone: String? = null,
    val region: String? = null,
    val locale: String? = null,
    val transcript: List<TranscriptTurn> = emptyList(),
    val result: Map<String, JsonElement>? = null,
    val error: CallErrorDetail? = null,
    val metadata: Map<String, String>? = null,
    @SerialName("recording_url") val recordingUrl: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("completed_at") val completedAt: String? = null,
    // V1 legacy backwards compatibility fields
    val summary: String? = null,
    @SerialName("task_completed") val taskCompleted: Boolean? = null,
    @SerialName("failure_code") val failureCode: String? = null,
    @SerialName("failure_message") val failureMessage: String? = null,
    val recipients: List<CallRecipient>? = null
) {
    /** API Resource ID */
    val callId: String get() = id

    /** Typed telephone outcome */
    val callOutcome: CallOutcome
        get() = when (callOutcomeRaw?.lowercase()) {
            "completed" -> CallOutcome.COMPLETED
            "no_answer" -> CallOutcome.NO_ANSWER
            "busy" -> CallOutcome.BUSY
            "declined" -> CallOutcome.DECLINED
            else -> CallOutcome.UNKNOWN
        }

    /** Typed business result readiness */
    val resultStatus: ResultStatus
        get() = when (resultStatusRaw?.lowercase()) {
            "pending" -> ResultStatus.PENDING
            "available" -> ResultStatus.AVAILABLE
            "unavailable" -> ResultStatus.UNAVAILABLE
            "not_applicable" -> ResultStatus.NOT_APPLICABLE
            else -> ResultStatus.UNKNOWN
        }

    /**
     * True if result is ready (either available or unavailable/not_applicable).
     * Polling must continue while false.
     */
    val isResultReady: Boolean
        get() = resultStatus != ResultStatus.PENDING && resultStatus != ResultStatus.UNKNOWN

    /**
     * Backward-compatibility summary getter.
     * Inspects V2 result["summary"], then legacy summary, then recipients summary.
     */
    val bestSummary: String?
        get() {
            val v2Summary = result?.get("summary")?.toString()?.trim('"')
            if (!v2Summary.isNullOrBlank()) return v2Summary
            if (!summary.isNullOrBlank()) return summary
            val rSummary = recipients?.firstOrNull()?.summary
            if (!rSummary.isNullOrBlank()) return rSummary
            val aSummary = recipients?.firstOrNull()?.attempts?.firstOrNull()?.summary
            if (!aSummary.isNullOrBlank()) return aSummary
            return null
        }

    /** Backward-compatibility turns getter */
    val allTranscriptTurns: List<TranscriptTurn>
        get() {
            if (transcript.isNotEmpty()) return transcript
            val rTurns = recipients?.firstOrNull()?.attempts?.firstOrNull()?.transcriptTurns
            if (!rTurns.isNullOrEmpty()) return rTurns
            return emptyList()
        }

    /** Backward-compatibility recording URL */
    val bestRecordingUrl: String?
        get() {
            if (!recordingUrl.isNullOrBlank()) return recordingUrl
            if (!audioUrl.isNullOrBlank()) return audioUrl
            val rRecording = recipients?.firstOrNull()?.attempts?.firstOrNull()?.recordingUrl
            if (!rRecording.isNullOrBlank()) return rRecording
            return null
        }
}

/**
 * Transcript turn from a call conversation.
 */
@Serializable
data class TranscriptTurn(
    @SerialName("offset_seconds") val offsetSeconds: Int? = null,
    val speaker: String = "",
    val text: String = ""
)

/**
 * Lifecycle event observed during call execution (`GET /v2/calls/{id}/events`).
 */
@Serializable
data class CallLifecycleEvent(
    val id: String = "",
    val type: String = "",
    @SerialName("call_id") val callId: String = "",
    val level: String = "info",
    val status: String = "",
    val message: String = "",
    val details: JsonObject? = null,
    @SerialName("created_at") val createdAt: String = ""
)

/**
 * Paginated events response from `/v2/calls/{id}/events`.
 */
@Serializable
data class CallEventsPage(
    val events: List<CallLifecycleEvent> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null
)

// Legacy V1 models kept for backward compatibility:

@Serializable
data class CallRecipient(
    val id: String = "",
    val phones: List<String> = emptyList(),
    val status: String = "",
    val summary: String? = null,
    val attempts: List<CallAttempt> = emptyList()
)

@Serializable
data class CallAttempt(
    val id: String = "",
    val phone: String = "",
    val status: String = "",
    val summary: String? = null,
    @SerialName("recording_url") val recordingUrl: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("transcript_turns") val transcriptTurns: List<TranscriptTurn> = emptyList()
)

@Serializable
data class TaskResult(
    val taskId: String = "",
    val title: String = "",
    val isSuccess: Boolean = false,
    val summary: String = "",
    val detailsJson: String = "{}"
)

@Serializable
data class WebhookEvent(
    val callId: String = "",
    val status: String = "",
    val transcript: String = "",
    val result: TaskResult? = null
)

/**
 * Call status enum definition for standardized UI state rendering.
 */
enum class CallEStatus {
    READY,
    DISPATCHING,
    CALL_IN_PROGRESS,
    EXTRACTING_RESULT,
    SUCCESS,
    UNAVAILABLE,
    FAILED
}
