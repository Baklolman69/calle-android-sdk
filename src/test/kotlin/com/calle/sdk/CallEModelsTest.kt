package com.calle.sdk

import com.calle.sdk.models.CallAttempt
import com.calle.sdk.models.CallRecipient
import com.calle.sdk.models.CallRequest
import com.calle.sdk.models.CallResponse
import com.calle.sdk.models.TranscriptTurn
import com.calle.sdk.models.isValidE164Phone
import com.calle.sdk.models.sanitizeE164Phone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallEModelsTest {

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
    fun testCallRequestToTaskStringWithPhoneAndAction() {
        val request = CallRequest(
            toPhoneNumber = "+15550199000",
            promptInstructions = "ask if they have vegan options tonight"
        )
        val taskString = request.toTaskString()
        assertEquals("Call +15550199000 and ask if they have vegan options tonight", taskString)
    }

    @Test
    fun testCallRequestToTaskStringWithoutActionAppendsDefault() {
        val request = CallRequest(
            toPhoneNumber = "+15550199000",
            promptInstructions = "Luigi's Pizza 123 Main St"
        )
        val taskString = request.toTaskString()
        assertTrue(taskString.startsWith("Call +15550199000 and ask Luigi's Pizza 123 Main St"))
    }

    @Test
    fun testCallResponseHelperMethods() {
        val attemptTurn = TranscriptTurn(offsetSeconds = 1, speaker = "bot", text = "Hello!")
        val attempt = CallAttempt(
            id = "att_1",
            status = "completed",
            summary = "Reservation confirmed",
            transcriptTurns = listOf(attemptTurn)
        )
        val recipient = CallRecipient(
            id = "rec_1",
            status = "completed",
            attempts = listOf(attempt)
        )
        val response = CallResponse(
            id = "call_999",
            status = "SUCCESS",
            recipients = listOf(recipient)
        )

        assertEquals("Reservation confirmed", response.bestSummary)
        assertEquals(1, response.allTranscriptTurns.size)
        assertEquals("Hello!", response.allTranscriptTurns[0].text)
    }
}
