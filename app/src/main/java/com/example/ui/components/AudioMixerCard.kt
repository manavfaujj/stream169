package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stream.model.AudioSourceMode
import com.example.stream.model.StreamConfig
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
fun AudioMixerCard(
    config: StreamConfig,
    telemetry: StreamTelemetry,
    onAudioModeChanged: (AudioSourceMode) -> Unit,
    onMicVolumeChanged: (Float) -> Unit,
    onInternalVolumeChanged: (Float) -> Unit,
    onToggleMicMute: () -> Unit,
    onToggleInternalMute: () -> Unit,
    onAudioBitrateChanged: (Int) -> Unit = {},
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
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Audio Mixer",
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Audio Sources & Quality",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Mix game sound, voice, and select audio bitrate",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Audio Mode Selector Pills (Pure Monochrome)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AudioModePill(
                    label = "Both Mixed",
                    selected = config.audioMode == AudioSourceMode.BOTH,
                    onClick = { onAudioModeChanged(AudioSourceMode.BOTH) },
                    modifier = Modifier.weight(1f).testTag("chip_audio_both")
                )
                AudioModePill(
                    label = "Mic Only",
                    selected = config.audioMode == AudioSourceMode.MIC_ONLY,
                    onClick = { onAudioModeChanged(AudioSourceMode.MIC_ONLY) },
                    modifier = Modifier.weight(1f).testTag("chip_audio_mic")
                )
                AudioModePill(
                    label = "Game Audio",
                    selected = config.audioMode == AudioSourceMode.INTERNAL_ONLY,
                    onClick = { onAudioModeChanged(AudioSourceMode.INTERNAL_ONLY) },
                    modifier = Modifier.weight(1f).testTag("chip_audio_internal")
                )
                AudioModePill(
                    label = "Mute",
                    selected = config.audioMode == AudioSourceMode.MUTED,
                    onClick = { onAudioModeChanged(AudioSourceMode.MUTED) },
                    modifier = Modifier.weight(0.7f).testTag("chip_audio_mute")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Audio Quality Selection Feature
            Text(
                text = "Audio Quality / Bitrate",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AudioQualityPill(
                    label = "64k",
                    sub = "Voice",
                    selected = config.audioBitrateKbps == 64,
                    onClick = { onAudioBitrateChanged(64) },
                    modifier = Modifier.weight(1f).testTag("chip_audio_quality_64")
                )
                AudioQualityPill(
                    label = "128k",
                    sub = "Standard",
                    selected = config.audioBitrateKbps == 128,
                    onClick = { onAudioBitrateChanged(128) },
                    modifier = Modifier.weight(1f).testTag("chip_audio_quality_128")
                )
                AudioQualityPill(
                    label = "160k",
                    sub = "High",
                    selected = config.audioBitrateKbps == 160,
                    onClick = { onAudioBitrateChanged(160) },
                    modifier = Modifier.weight(1f).testTag("chip_audio_quality_160")
                )
                AudioQualityPill(
                    label = "192k",
                    sub = "Hi-Fi",
                    selected = config.audioBitrateKbps == 192,
                    onClick = { onAudioBitrateChanged(192) },
                    modifier = Modifier.weight(1f).testTag("chip_audio_quality_192")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Microphone Channel
            AudioChannelRow(
                title = "Microphone (Voice)",
                icon = if (config.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                isMuted = config.isMicMuted || config.audioMode == AudioSourceMode.INTERNAL_ONLY || config.audioMode == AudioSourceMode.MUTED,
                volume = config.micVolume,
                levelDb = telemetry.micLevelDb,
                onVolumeChanged = onMicVolumeChanged,
                onToggleMute = onToggleMicMute,
                muteTag = "btn_mute_mic"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Internal Audio Channel
            AudioChannelRow(
                title = "Internal Audio (Game / Media)",
                icon = if (config.isInternalMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                isMuted = config.isInternalMuted || config.audioMode == AudioSourceMode.MIC_ONLY || config.audioMode == AudioSourceMode.MUTED,
                volume = config.internalVolume,
                levelDb = telemetry.internalLevelDb,
                onVolumeChanged = onInternalVolumeChanged,
                onToggleMute = onToggleInternalMute,
                muteTag = "btn_mute_internal"
            )
        }
    }
}

@Composable
private fun AudioModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) PureWhite else DarkSurfaceElevated)
            .border(1.dp, if (selected) PureWhite else BorderSubtle, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) PureBlack else TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun AudioQualityPill(
    label: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) PureWhite else DarkSurfaceElevated)
            .border(1.dp, if (selected) PureWhite else BorderSubtle, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) PureBlack else TextPrimary,
                maxLines = 1
            )
            Text(
                text = sub,
                fontSize = 9.sp,
                color = if (selected) PureBlack.copy(alpha = 0.7f) else TextMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AudioChannelRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isMuted: Boolean,
    volume: Float,
    levelDb: Float,
    onVolumeChanged: (Float) -> Unit,
    onToggleMute: () -> Unit,
    muteTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(28.dp).testTag(muteTag)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Toggle Mute",
                        tint = if (isMuted) TextMuted else PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = if (isMuted) TextMuted else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = if (isMuted) "MUTED" else "${(volume * 100).toInt()}%",
                color = if (isMuted) TextMuted else TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Clean Monochrome VU meter
        MonochromeVuMeter(
            levelDb = if (isMuted) -60f else levelDb,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        )

        // Volume Slider (Monochrome)
        Slider(
            value = volume,
            onValueChange = onVolumeChanged,
            valueRange = 0.0f..1.5f,
            enabled = !isMuted,
            colors = SliderDefaults.colors(
                thumbColor = PureWhite,
                activeTrackColor = PureWhite,
                inactiveTrackColor = BorderSubtle,
                disabledThumbColor = TextMuted,
                disabledActiveTrackColor = BorderSubtle
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
private fun MonochromeVuMeter(
    levelDb: Float,
    modifier: Modifier = Modifier
) {
    val fraction = ((levelDb + 60f) / 60f).coerceIn(0.0f, 1.0f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 80),
        label = "vu_anim"
    )

    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(DarkBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedFraction)
                .background(PureWhite)
        )
    }
}
