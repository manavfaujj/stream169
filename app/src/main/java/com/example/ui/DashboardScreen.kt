package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stream.camera.FaceCamOverlayService
import com.example.stream.model.StreamOrientation
import com.example.ui.components.AudioMixerCard
import com.example.ui.components.BitrateSettingsCard
import com.example.ui.components.CropPreviewCard
import com.example.ui.components.FaceCamCard
import com.example.ui.components.LongStreamKeepAliveCard
import com.example.ui.components.StreamConfigCard
import com.example.ui.components.StreamStatusCard
import com.example.ui.components.YouTubeGuideDialog
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: StreamViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()

    var showGuideDialog by remember { mutableStateOf(false) }

    // MediaProjection screen capture launcher
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startStream(result.resultCode, result.data!!)
            // If Face Cam floating overlay is enabled, show it
            if (config.faceCam.isEnabled && config.faceCam.isFloatingOverlayEnabled) {
                FaceCamOverlayService.show(context)
            }
        } else {
            Toast.makeText(context, "Screen capture permission is required to stream", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher for Audio and Notifications
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!recordAudioGranted) {
            Toast.makeText(context, "Microphone permission denied. Microphone audio will be disabled.", Toast.LENGTH_SHORT).show()
        }
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PureWhite),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = PureBlack,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Stream169",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(DarkSurfaceElevated)
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "16:9 LIVE",
                                        color = TextPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "Zero Black Bars • Simple Studio",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showGuideDialog = true },
                        modifier = Modifier.testTag("btn_top_guide")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Help Guide",
                            tint = PureWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Live Stream Status & Main Start/Stop Action
                item {
                    StreamStatusCard(
                        telemetry = telemetry,
                        config = config,
                        onStartStream = {
                            if (config.streamKey.isBlank()) {
                                Toast.makeText(context, "Please enter your YouTube Stream Key below first", Toast.LENGTH_LONG).show()
                                return@StreamStatusCard
                            }

                            val hasRecordAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            val hasNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (hasRecordAudio && hasNotifications) {
                                val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                            } else {
                                val neededPermissions = mutableListOf<String>()
                                if (!hasRecordAudio) neededPermissions.add(Manifest.permission.RECORD_AUDIO)
                                if (!hasNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissionsLauncher.launch(neededPermissions.toTypedArray())
                            }
                        },
                        onStopStream = {
                            viewModel.stopStream()
                            FaceCamOverlayService.hide(context)
                        }
                    )
                }

                // 2. YouTube Stream Key Input Card
                item {
                    StreamConfigCard(
                        config = config,
                        onStreamKeyChanged = { viewModel.updateStreamKey(it) },
                        onRtmpUrlChanged = { viewModel.updateRtmpUrl(it) },
                        onTestConnection = { callback ->
                            viewModel.testConnection(callback)
                        },
                        onOpenGuide = { showGuideDialog = true }
                    )
                }

                // 3. Long Stream Protection Card (Keep Active, Auto-Reconnect, Prevent Sleep)
                item {
                    LongStreamKeepAliveCard()
                }

                // 4. Stream Aspect Ratio & Smart Crop Card
                item {
                    CropPreviewCard(
                        orientation = config.orientation,
                        resolution = config.resolution,
                        onOrientationChanged = { newOrientation ->
                            viewModel.setOrientation(newOrientation)
                        }
                    )
                }

                // 5. Face Cam Card (Clean Front Camera • No Marks • Auto-hide Buttons)
                item {
                    FaceCamCard(
                        faceCamConfig = config.faceCam,
                        onEnabledChanged = { viewModel.setFaceCamEnabled(it) },
                        onSizeChanged = { viewModel.setFaceCamSize(it) },
                        onZoomChanged = { viewModel.setFaceCamZoom(it) },
                        onFrontCameraChanged = { viewModel.setFaceCamFront(it) },
                        onCircleShapeChanged = { viewModel.setFaceCamCircle(it) },
                        onOverlayChanged = { viewModel.setFaceCamOverlay(it) }
                    )
                }

                // 5. Audio Sources & Volume Mixer Card
                item {
                    AudioMixerCard(
                        config = config,
                        telemetry = telemetry,
                        onAudioModeChanged = { viewModel.setAudioMode(it) },
                        onMicVolumeChanged = { viewModel.setMicVolume(it) },
                        onInternalVolumeChanged = { viewModel.setInternalVolume(it) },
                        onToggleMicMute = { viewModel.toggleMicMute() },
                        onToggleInternalMute = { viewModel.toggleInternalMute() },
                        onAudioBitrateChanged = { viewModel.setAudioBitrate(it) }
                    )
                }

                // 6. Stream Quality Presets Card
                item {
                    BitrateSettingsCard(
                        config = config,
                        onResolutionChanged = { viewModel.setResolution(it) },
                        onVideoBitrateChanged = { viewModel.setVideoBitrate(it) },
                        onAudioBitrateChanged = { viewModel.setAudioBitrate(it) },
                        onFrameRateChanged = { viewModel.setFrameRate(it) }
                    )
                }
            }
        }

        if (showGuideDialog) {
            YouTubeGuideDialog(onDismiss = { showGuideDialog = false })
        }
    }
}
