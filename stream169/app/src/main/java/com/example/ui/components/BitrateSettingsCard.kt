package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
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
import com.example.stream.model.StreamResolution
import com.example.ui.theme.BorderProminent
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceActive
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BitrateSettingsCard(
    config: StreamConfig,
    onResolutionChanged: (StreamResolution) -> Unit,
    onVideoBitrateChanged: (Int) -> Unit,
    onAudioBitrateChanged: (Int) -> Unit,
    onFrameRateChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Quality Settings",
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Stream Quality",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Select quality preset",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1-Tap Presets (Very simple & intuitive)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QualityPresetPill(
                        title = "1080p • 60 FPS",
                        subtitle = "High Quality (6.0 Mbps)",
                        selected = config.resolution == StreamResolution.RES_1080P && config.frameRate == 60,
                        onClick = {
                            onResolutionChanged(StreamResolution.RES_1080P)
                            onFrameRateChanged(60)
                            onVideoBitrateChanged(6000)
                        },
                        modifier = Modifier.weight(1f)
                    )

                    QualityPresetPill(
                        title = "1080p • 30 FPS",
                        subtitle = "Standard (4.5 Mbps)",
                        selected = config.resolution == StreamResolution.RES_1080P && config.frameRate == 30,
                        onClick = {
                            onResolutionChanged(StreamResolution.RES_1080P)
                            onFrameRateChanged(30)
                            onVideoBitrateChanged(4500)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QualityPresetPill(
                        title = "720p • 60 FPS",
                        subtitle = "Smooth Gaming (3.5 Mbps)",
                        selected = config.resolution == StreamResolution.RES_720P && config.frameRate == 60,
                        onClick = {
                            onResolutionChanged(StreamResolution.RES_720P)
                            onFrameRateChanged(60)
                            onVideoBitrateChanged(3500)
                        },
                        modifier = Modifier.weight(1f)
                    )

                    QualityPresetPill(
                        title = "720p • 30 FPS",
                        subtitle = "Fast / Low Data (2.5 Mbps)",
                        selected = config.resolution == StreamResolution.RES_720P && config.frameRate == 30,
                        onClick = {
                            onResolutionChanged(StreamResolution.RES_720P)
                            onFrameRateChanged(30)
                            onVideoBitrateChanged(2500)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Bitrate Quality Presets
            Text(
                text = "Audio Bitrate",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QualityPresetPill(
                    title = "64k",
                    subtitle = "Voice",
                    selected = config.audioBitrateKbps == 64,
                    onClick = { onAudioBitrateChanged(64) },
                    modifier = Modifier.weight(1f).testTag("preset_audio_64")
                )
                QualityPresetPill(
                    title = "128k",
                    subtitle = "Standard",
                    selected = config.audioBitrateKbps == 128,
                    onClick = { onAudioBitrateChanged(128) },
                    modifier = Modifier.weight(1f).testTag("preset_audio_128")
                )
                QualityPresetPill(
                    title = "160k",
                    subtitle = "High",
                    selected = config.audioBitrateKbps == 160,
                    onClick = { onAudioBitrateChanged(160) },
                    modifier = Modifier.weight(1f).testTag("preset_audio_160")
                )
                QualityPresetPill(
                    title = "192k",
                    subtitle = "Hi-Fi",
                    selected = config.audioBitrateKbps == 192,
                    onClick = { onAudioBitrateChanged(192) },
                    modifier = Modifier.weight(1f).testTag("preset_audio_192")
                )
            }
        }
    }
}

@Composable
private fun QualityPresetPill(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PureWhite else DarkSurfaceElevated)
            .border(1.dp, if (selected) PureWhite else BorderSubtle, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) PureBlack else TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = if (selected) PureBlack.copy(alpha = 0.7f) else TextMuted
            )
        }
    }
}
