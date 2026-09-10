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

/**
 * Reusable Jetpack Compose Status Badge displaying live CALL-E task states.
 * Supports READY, DISPATCHING, CALL_IN_PROGRESS, SUCCESS, FAILED.
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
            CallEStatus.SUCCESS -> Color(0xFF10B981)
            CallEStatus.FAILED -> Color(0xFFEF4444)
        },
        label = "BadgeColorAnimation"
    )

    val labelText = when (status) {
        CallEStatus.READY -> "Ready"
        CallEStatus.DISPATCHING -> "Dispatching..."
        CallEStatus.CALL_IN_PROGRESS -> "Call Active"
        CallEStatus.SUCCESS -> "Completed"
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

    val isAnimated = status == CallEStatus.DISPATCHING || status == CallEStatus.CALL_IN_PROGRESS

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
