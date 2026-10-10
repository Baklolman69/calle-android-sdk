package com.calle.sdk.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calle.sdk.models.CallEStatus
import com.calle.sdk.models.CallOutcome
import com.calle.sdk.models.CallResponse
import com.calle.sdk.models.ResultStatus

/**
 * Reusable Jetpack Compose Status Badge displaying live CALL-E V2 execution states.
 * Supports READY, DISPATCHING, CALL_IN_PROGRESS, EXTRACTING_RESULT, SUCCESS, UNAVAILABLE, FAILED.
 */
@Composable
fun CallEStatusBadge(
    status: CallEStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            CallEStatus.READY -> Color(0xFF64748B)
            CallEStatus.DISPATCHING -> Color(0xFFF59E0B)
            CallEStatus.CALL_IN_PROGRESS -> Color(0xFF3B82F6)
            CallEStatus.EXTRACTING_RESULT -> Color(0xFF8B5CF6)
            CallEStatus.SUCCESS -> Color(0xFF10B981)
            CallEStatus.UNAVAILABLE -> Color(0xFFF97316)
            CallEStatus.FAILED -> Color(0xFFEF4444)
        },
        label = "BadgeColorAnimation"
    )

    val labelText = when (status) {
        CallEStatus.READY -> "Ready"
        CallEStatus.DISPATCHING -> "Dispatching..."
        CallEStatus.CALL_IN_PROGRESS -> "Call Active"
        CallEStatus.EXTRACTING_RESULT -> "Extracting..."
        CallEStatus.SUCCESS -> "Completed"
        CallEStatus.UNAVAILABLE -> "Unavailable"
        CallEStatus.FAILED -> "Failed"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaPulse"
    )

    val isAnimated = status == CallEStatus.DISPATCHING ||
            status == CallEStatus.CALL_IN_PROGRESS ||
            status == CallEStatus.EXTRACTING_RESULT

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(statusColor.copy(alpha = 0.15f))
            .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 4.dp else 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 6.dp else 8.dp)
                .alpha(if (isAnimated) alphaAnim else 1.0f)
                .clip(CircleShape)
                .background(statusColor)
        )

        Text(
            text = labelText,
            color = statusColor,
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Convenient overload accepting a CALL-E V2 [CallResponse] directly.
 * Maps V2 `call_outcome` and `result_status` accurately to UI state.
 */
@Composable
fun CallEStatusBadge(
    response: CallResponse,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val mappedStatus = when {
        response.error != null || response.resultStatus == ResultStatus.NOT_APPLICABLE -> CallEStatus.FAILED
        response.resultStatus == ResultStatus.AVAILABLE -> CallEStatus.SUCCESS
        response.resultStatus == ResultStatus.UNAVAILABLE -> CallEStatus.UNAVAILABLE
        response.resultStatus == ResultStatus.PENDING -> {
            if (response.status.equals("completed", ignoreCase = true)) {
                CallEStatus.EXTRACTING_RESULT
            } else if (response.status.equals("in_progress", ignoreCase = true)) {
                CallEStatus.CALL_IN_PROGRESS
            } else {
                CallEStatus.DISPATCHING
            }
        }
        else -> CallEStatus.READY
    }

    CallEStatusBadge(
        status = mappedStatus,
        modifier = modifier,
        compact = compact
    )
}
