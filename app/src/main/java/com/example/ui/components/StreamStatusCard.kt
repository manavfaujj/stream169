package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stream.model.StreamConfig
import com.example.stream.model.StreamStatus
import com.example.stream.model.StreamTelemetry
import com.example.ui.theme.BorderProminent
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceActive
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StreamStatusCard(
    telemetry: StreamTelemetry,
    config: StreamConfig,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isStreaming = telemetry.status == StreamStatus.STREAMING
    val isConnecting = telemetry.status == StreamStatus.CONNECTING || telemetry.status == StreamStatus.PREPARING

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Status Indicator & Aspect Ratio Label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isStreaming) PureWhite else TextMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isStreaming -> "LIVE • YOUTUBE"
                            isConnecting -> "CONNECTING..."
                            telemetry.status == StreamStatus.ERROR -> "STREAM ERROR"
                            else -> "READY TO STREAM"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = config.orientation.ratioLabel,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Primary Action Stream Button (High-contrast pure monochrome)
            Button(
                onClick = {
                    if (isStreaming || isConnecting) {
                        onStopStream()
                    } else {
                        onStartStream()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStreaming) DarkSurfaceActive else PureWhite,
                    contentColor = if (isStreaming) PureWhite else PureBlack
                ),
                shape = RoundedCornerShape(8.dp),
                border = if (isStreaming) androidx.compose.foundation.BorderStroke(1.dp, PureWhite) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("stream_action_button")
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = PureBlack
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Connecting to YouTube...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else if (isStreaming) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "STOP LIVE STREAM",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        Icons.Default.CastConnected,
                        contentDescription = null,
                        tint = PureBlack,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "START STREAM",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Real-time Telemetry Grid (Visible during or after streaming)
            if (isStreaming || telemetry.durationSeconds > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(label = "DURATION", value = telemetry.formattedDuration, modifier = Modifier.weight(1f))
                    MetricBox(label = "BITRATE", value = "${telemetry.currentBitrateKbps} k", modifier = Modifier.weight(1f))
                    MetricBox(label = "FPS", value = String.format("%.0f", telemetry.currentFps), modifier = Modifier.weight(1f))
                    MetricBox(label = "SENT", value = telemetry.formattedDataSent, modifier = Modifier.weight(1f))
                }
            }

            // Error display if any
            AnimatedVisibility(visible = telemetry.errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(DarkSurfaceElevated, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderProminent, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = telemetry.errorMessage ?: "",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DarkBackground, RoundedCornerShape(6.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
