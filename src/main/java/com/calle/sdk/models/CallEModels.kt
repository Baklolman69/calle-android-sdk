package com.calle.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Core CALL-E SDK Models representing API requests, responses, and call execution state.
 * Based on the official CALL-E OpenAPI spec at https://docs.heycall-e.com
 */

/**
 * Internal request model that builds the CALL-E API "task" string
 * from phone number + prompt instructions.
 */
data class CallRequest(
    val toPhoneNumber: String,
    val promptInstructions: String,
    val taskCategory: String = "GENERAL"
) {
    /**
     * Builds the task string for CALL-E API.
     * Guarantees the output task string starts with `Call <E164_PHONE> and <action>`
     * and auto-appends action instructions if prompt only contains business name/address.
     */
    fun toTaskString(): String {
        val prompt = promptInstructions.trim()
        val phoneRegex = Regex("""\+?\d{10,15}""")
        
        val extractedPhoneInPrompt = phoneRegex.find(prompt)?.value ?: ""
        val rawPhone = if (toPhoneNumber.isNotBlank()) toPhoneNumber else extractedPhoneInPrompt
        val phone = sanitizeE164Phone(rawPhone)

        var cleanPrompt = if (extractedPhoneInPrompt.isNotBlank()) {
            prompt.replace(extractedPhoneInPrompt, "").trim()
        } else {
            prompt
        }

        if (cleanPrompt.startsWith("Call ", ignoreCase = true)) {
            cleanPrompt = cleanPrompt.substring(5).trim()
        }

        val actionKeywords = listOf("ask", "check", "inquire", "order", "book", "reserve", "find out", "tell", "request", "get", "when", "what", "how", "verify")
        val hasAction = actionKeywords.any { cleanPrompt.contains(it, ignoreCase = true) }

        val finalInstructions = if (!hasAction && cleanPrompt.isNotBlank()) {
            "ask $cleanPrompt regarding business hours, services offered, and general inquiries."
        } else if (cleanPrompt.startsWith("and ", ignoreCase = true)) {
            cleanPrompt.substring(4).trim()
        } else {
            cleanPrompt.ifBlank { "inquire about business details and services." }
        }

        return if (phone.isNotBlank() && phone.length >= 10) {
            "Call $phone and $finalInstructions"
        } else {
            "Search for $finalInstructions, find their phone number, call them, and execute the request."
        }
    }
}

/**
 * Sanitizes phone numbers into E.164 format (+1XXXXXXXXXX).
 */
fun sanitizeE164Phone(rawPhone: String): String {
    val clean = rawPhone.filter { it.isDigit() || it == '+' }
    if (clean.isBlank()) return ""
    return when {
        clean.startsWith("+") -> clean
        clean.length == 10 -> "+1$clean"
        clean.length == 11 && clean.startsWith("1") -> "+$clean"
        else -> "+$clean"
    }
}

/**
 * Wire format for the CALL-E REST API POST body.
 */
@Serializable
data class CallEApiRequest(
    val task: String
)

/**
 * Full Response from CALL-E API matching actual backend JSON.
 */
@Serializable
data class CallResponse(
    val id: String = "",
    val status: String = "",
    val task: String = "",
    val summary: String? = null,
    @SerialName("recording_url") val recordingUrl: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("task_completed") val taskCompleted: Boolean? = null,
    @SerialName("failure_code") val failureCode: String? = null,
    @SerialName("failure_message") val failureMessage: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("transcript_turns") val topLevelTurns: List<TranscriptTurn>? = null,
    val recipients: List<CallRecipient>? = null
) {
    val callId: String get() = id

    /** Extracts transcript turns from top-level or nested recipients[0].attempts[0] */
    val allTranscriptTurns: List<TranscriptTurn>
        get() {
            if (!topLevelTurns.isNullOrEmpty()) return topLevelTurns
            val recipientTurns = recipients?.firstOrNull()?.attempts?.firstOrNull()?.transcriptTurns
            if (!recipientTurns.isNullOrEmpty()) return recipientTurns
            return emptyList()
        }

    /** Extracts summary from top-level, recipient, or attempt */
    val bestSummary: String?
        get() {
            if (!summary.isNullOrBlank()) return summary
            val rSummary = recipients?.firstOrNull()?.summary
            if (!rSummary.isNullOrBlank()) return rSummary
            val aSummary = recipients?.firstOrNull()?.attempts?.firstOrNull()?.summary
            if (!aSummary.isNullOrBlank()) return aSummary
            return null
        }

    /** Extracts audio recording URL from top-level, recipient, or attempt */
    val bestRecordingUrl: String?
        get() {
            if (!recordingUrl.isNullOrBlank()) return recordingUrl
            if (!audioUrl.isNullOrBlank()) return audioUrl
            val rRecording = recipients?.firstOrNull()?.attempts?.firstOrNull()?.recordingUrl
            if (!rRecording.isNullOrBlank()) return rRecording
            val rAudio = recipients?.firstOrNull()?.attempts?.firstOrNull()?.audioUrl
            if (!rAudio.isNullOrBlank()) return rAudio
            return null
        }
}

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

/**
 * Transcript turn from a call attempt.
 */
@Serializable
data class TranscriptTurn(
    @SerialName("offset_seconds") val offsetSeconds: Int = 0,
    val speaker: String = "",
    val text: String = ""
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
    SUCCESS,
    FAILED
}

