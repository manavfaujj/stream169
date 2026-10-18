package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.stream.ScreenStreamService
import com.example.ui.DashboardScreen
import com.example.ui.theme.Stream169Theme

class MainActivity : ComponentActivity() {

    private val viewModel: StreamViewModel by viewModels()

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startStreamService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (!micGranted) {
            Toast.makeText(this, "Microphone permission is required for voice audio", Toast.LENGTH_SHORT).show()
        }
        requestScreenCapture()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Stream169Theme {
                DashboardScreen(
                    viewModel = viewModel,
                    onStartStreamClicked = { checkPermissionsAndStart() },
                    onStopStreamClicked = { stopStreamService() }
                )
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val config = viewModel.config.value
        val trimmedKey = config.streamKey.trim()
        val pathSegments = config.rtmpUrl.trim().substringAfter("://", "").substringAfter('/', "").split('/')
        val hasKeyInUrl = pathSegments.size >= 2 && pathSegments.last().length >= 4

        if (trimmedKey.isEmpty() && !hasKeyInUrl) {
            Toast.makeText(this, "Please enter your Stream Key to start streaming", Toast.LENGTH_SHORT).show()
            return
        }

        val requiredPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (config.isFaceCamEnabled) {
            requiredPermissions.add(Manifest.permission.CAMERA)
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        } else {
            requestScreenCapture()
        }
    }

    private fun requestScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startStreamService(resultCode: Int, data: Intent) {
        val config = viewModel.config.value
        val intent = Intent(this, ScreenStreamService::class.java).apply {
            action = ScreenStreamService.ACTION_START
            putExtra(ScreenStreamService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenStreamService.EXTRA_RESULT_DATA, data)
            putExtra(ScreenStreamService.EXTRA_RTMP_URL, config.rtmpUrl)
            putExtra(ScreenStreamService.EXTRA_STREAM_KEY, config.streamKey)
            putExtra(ScreenStreamService.EXTRA_ORIENTATION, config.orientation.name)
            putExtra(ScreenStreamService.EXTRA_RESOLUTION, config.resolution.name)
            putExtra(ScreenStreamService.EXTRA_FPS, config.fps)
            putExtra(ScreenStreamService.EXTRA_BITRATE, config.videoBitrate)
            putExtra(ScreenStreamService.EXTRA_MIC_ENABLED, config.isMicEnabled)
            putExtra(ScreenStreamService.EXTRA_MIC_VOLUME, config.micVolume)
            putExtra(ScreenStreamService.EXTRA_INTERNAL_AUDIO_ENABLED, config.isInternalAudioEnabled)
            putExtra(ScreenStreamService.EXTRA_INTERNAL_AUDIO_VOLUME, config.internalAudioVolume)
            putExtra(ScreenStreamService.EXTRA_FACECAM_ENABLED, config.isFaceCamEnabled)
            putExtra(ScreenStreamService.EXTRA_FACECAM_SHAPE, config.faceCamShape.name)
            putExtra(ScreenStreamService.EXTRA_FACECAM_SIZE, config.faceCamSizeDp)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopStreamService() {
        val intent = Intent(this, ScreenStreamService::class.java).apply {
            action = ScreenStreamService.ACTION_STOP
        }
        startService(intent)
    }
}
