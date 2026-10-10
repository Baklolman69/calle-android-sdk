package com.calle.sdk

import com.calle.sdk.models.CallAttempt
import com.calle.sdk.models.CallOutcome
import com.calle.sdk.models.CallRecipient
import com.calle.sdk.models.CallRequest
import com.calle.sdk.models.CallResponse
import com.calle.sdk.models.ResultSchema
import com.calle.sdk.models.ResultStatus
import com.calle.sdk.models.TranscriptTurn
import com.calle.sdk.models.isValidE164Phone
import com.calle.sdk.models.sanitizeE164Phone
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallEModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun testE164Validation() {
        assertTrue(isValidE164Phone("+15550199000"))
        assertTrue(isValidE164Phone("+442071838750"))
        assertFalse(isValidE164Phone("15550199000"))
        assertFalse(isValidE164Phone(""))
        assertFalse(isValidE164Phone("+012345678"))
    }

    @Test
    fun testPhoneSanitization() {
        assertEquals("+15550199000", sanitizeE164Phone("5550199000"))
        assertEquals("+15550199000", sanitizeE164Phone("+1 (555) 019-9000"))
        assertEquals("+15550199000", sanitizeE164Phone("1-555-019-9000"))
        assertEquals("", sanitizeE164Phone("123"))
    }

    @Test
    fun testCallRequestCleanInstructions() {
        val requestWithPrefix = CallRequest(
            toPhoneNumber = "+15550199000",
            promptInstructions = "Call +15550199000 and ask if they have vegan options tonight"
        )
        assertEquals("ask if they have vegan options tonight", requestWithPrefix.cleanInstructions())

        val requestSimple = CallRequest(
            toPhoneNumber = "+15550199000",
            promptInstructions = "ask if they have vegan options tonight"
        )
        assertEquals("ask if they have vegan options tonight", requestSimple.cleanInstructions())
    }

    @Test
    fun testCallRequestToTaskStringBackwardCompatibility() {
        val request = CallRequest(
            toPhoneNumber = "+15550199000",
            promptInstructions = "ask if they have vegan options tonight"
        )
        val taskString = request.toTaskString()
        assertEquals("Call +15550199000 and ask if they have vegan options tonight", taskString)
    }

    @Test
    fun testResultSchemaDefault() {
        val defaultSchema = ResultSchema.default()
        assertEquals("object", defaultSchema.type)
        assertFalse(defaultSchema.additionalProperties)
        assertTrue(defaultSchema.properties.containsKey("summary"))
        assertTrue(defaultSchema.properties.containsKey("task_completed"))
        assertEquals("string", defaultSchema.properties["summary"]?.type)
        assertEquals("boolean", defaultSchema.properties["task_completed"]?.type)

        val encodedJson = json.encodeToString(ResultSchema.serializer(), defaultSchema)
        assertTrue(encodedJson.contains("\"type\":\"object\""))
        assertTrue(encodedJson.contains("\"additionalProperties\":false"))
    }

    @Test
    fun testV2CallResponseAvailableDeserialization() {
        val v2RawJson = """
            {
              "id": "call_123456",
              "call_id": "billing_987654321",
              "status": "completed",
              "call_outcome": "completed",
              "result_status": "available",
              "task": "Ask opening hours tonight.",
              "phone": "+15550199000",
              "transcript": [
                {"speaker": "bot", "offset_seconds": 0, "text": "Hello, are you open tonight?"},
                {"speaker": "user", "offset_seconds": 3, "text": "Yes, until 10 PM."}
              ],
              "result": {
                "summary": "The restaurant is open until 10 PM tonight.",
                "task_completed": true
              },
              "created_at": "2026-10-10T10:00:00Z",
              "completed_at": "2026-10-10T10:01:00Z"
            }
        """.trimIndent()

        val response = json.decodeFromString<CallResponse>(v2RawJson)

        assertEquals("call_123456", response.id)
        assertEquals("billing_987654321", response.billingCallId)
        assertEquals("completed", response.status)
        assertEquals(CallOutcome.COMPLETED, response.callOutcome)
        assertEquals(ResultStatus.AVAILABLE, response.resultStatus)
        assertTrue(response.isResultReady)

        assertEquals(2, response.transcript.size)
        assertEquals("bot", response.transcript[0].speaker)
        assertEquals("Hello, are you open tonight?", response.transcript[0].text)

        assertEquals("The restaurant is open until 10 PM tonight.", response.bestSummary)
        assertNull(response.error)
    }

    @Test
    fun testV2CallResponseBusyUnavailableDeserialization() {
        val v2RawJson = """
            {
              "id": "call_busy_123",
              "call_id": "billing_busy_456",
              "status": "completed",
              "call_outcome": "busy",
              "result_status": "unavailable",
              "task": "Confirm reservation",
              "transcript": [],
              "result": null,
              "error": null
            }
        """.trimIndent()

        val response = json.decodeFromString<CallResponse>(v2RawJson)

        assertEquals(CallOutcome.BUSY, response.callOutcome)
        assertEquals(ResultStatus.UNAVAILABLE, response.resultStatus)
        assertTrue(response.isResultReady) // Result is ready (unavailable, not pending)
        assertNull(response.result)
        assertNull(response.bestSummary)
    }

    @Test
    fun testV2CallResponsePendingReadinessRule() {
        val v2PendingJson = """
            {
              "id": "call_pending_123",
              "status": "completed",
              "call_outcome": "completed",
              "result_status": "pending",
              "result": null
            }
        """.trimIndent()

        val response = json.decodeFromString<CallResponse>(v2PendingJson)

        // Telephone status is "completed", BUT result_status is "pending"!
        assertEquals("completed", response.status)
        assertEquals(ResultStatus.PENDING, response.resultStatus)
        assertFalse(response.isResultReady) // MUST NOT be treated as ready!
    }

    @Test
    fun testV1LegacyCallResponseFallback() {
        val attemptTurn = TranscriptTurn(offsetSeconds = 1, speaker = "bot", text = "Hello from V1!")
        val attempt = CallAttempt(
            id = "att_1",
            status = "completed",
            summary = "V1 Reservation confirmed",
            transcriptTurns = listOf(attemptTurn)
        )
        val recipient = CallRecipient(
            id = "rec_1",
            status = "completed",
            attempts = listOf(attempt)
        )
        val response = CallResponse(
            id = "call_legacy_999",
            status = "SUCCESS",
            recipients = listOf(recipient)
        )

        assertEquals("V1 Reservation confirmed", response.bestSummary)
        assertEquals(1, response.allTranscriptTurns.size)
        assertEquals("Hello from V1!", response.allTranscriptTurns[0].text)
    }

    @Test
    fun testFormattedPhoneCleaningAndExtraction() {
        // Formatted number with parentheses and dashes
        val req1 = CallRequest(
            toPhoneNumber = "+15550199000",
            promptInstructions = "Call (555) 019-9000 and check table availability"
        )
        assertEquals("check table availability", req1.cleanInstructions())

        // Formatted number without action keyword should still strip 'and '
        val req2 = CallRequest(
            toPhoneNumber = "+15550199000",
            promptInstructions = "Call +15550199000 and pizza delivery"
        )
        assertEquals("Ask pizza delivery regarding business hours, services offered, and general inquiries.", req2.cleanInstructions())
    }

    @Test
    fun testDeviceContactResolverEarliestDelimiter() {
        val resolver = com.calle.sdk.data.DeviceContactResolver()
        // " in " occurs before " to "
        val name1 = resolver.extractNameFromPrompt("Call Acme in New York to ask hours")
        assertEquals("Acme", name1)

        val name2 = resolver.extractNameFromPrompt("Call Mom and ask about dinner")
        assertEquals("Mom", name2)
    }

    @Test
    fun testClientDemoModeAndBlankKeySecurity() {
        val demoClient = CallEClient(apiKey = "DEMO_KEY")
        assertTrue(demoClient.isDemoMode)

        val emptyClient = CallEClient(apiKey = "")
        assertFalse(emptyClient.isDemoMode)
    }
}
