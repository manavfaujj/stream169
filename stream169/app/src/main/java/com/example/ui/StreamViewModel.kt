package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.stream.StreamController
import com.example.stream.data.StreamConfigManager
import com.example.stream.model.AudioSourceMode
import com.example.stream.model.StreamConfig
import com.example.stream.model.StreamOrientation
import com.example.stream.model.StreamResolution
import com.example.stream.model.StreamStatus
import com.example.stream.model.StreamTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.SSLSocketFactory

class StreamViewModel(application: Application) : AndroidViewModel(application) {
    private val configManager = StreamConfigManager(application)

    val config: StateFlow<StreamConfig> = configManager.configFlow
    val telemetry: StateFlow<StreamTelemetry> = StreamController.telemetry

    fun updateStreamKey(key: String) {
        val current = config.value
        configManager.updateConfig(current.copy(streamKey = key.trim()))
    }

    fun updateRtmpUrl(url: String) {
        val current = config.value
        configManager.updateConfig(current.copy(rtmpUrl = url.trim()))
    }

    fun setOrientation(orientation: StreamOrientation) {
        val current = config.value
        configManager.updateConfig(current.copy(orientation = orientation))
    }

    fun setResolution(resolution: StreamResolution) {
        val current = config.value
        configManager.updateConfig(
            current.copy(
                resolution = resolution,
                videoBitrateKbps = resolution.defaultBitrateKbps
            )
        )
    }

    fun setVideoBitrate(kbps: Int) {
        val current = config.value
        configManager.updateConfig(current.copy(videoBitrateKbps = kbps))
    }

    fun setAudioBitrate(kbps: Int) {
        val current = config.value
        configManager.updateConfig(current.copy(audioBitrateKbps = kbps))
    }

    fun setFrameRate(fps: Int) {
        val current = config.value
        configManager.updateConfig(current.copy(frameRate = fps))
    }

    fun setAudioMode(mode: AudioSourceMode) {
        val current = config.value
        configManager.updateConfig(current.copy(audioMode = mode))
    }

    fun setMicVolume(vol: Float) {
        val current = config.value
        configManager.updateConfig(current.copy(micVolume = vol))
    }

    fun setInternalVolume(vol: Float) {
        val current = config.value
        configManager.updateConfig(current.copy(internalVolume = vol))
    }

    fun toggleMicMute() {
        val context = getApplication<Application>()
        if (telemetry.value.status == StreamStatus.STREAMING) {
            StreamController.toggleMicMute(context)
        } else {
            val current = config.value
            configManager.updateConfig(current.copy(isMicMuted = !current.isMicMuted))
        }
    }

    fun toggleInternalMute() {
        val context = getApplication<Application>()
        if (telemetry.value.status == StreamStatus.STREAMING) {
            StreamController.toggleInternalMute(context)
        } else {
            val current = config.value
            configManager.updateConfig(current.copy(isInternalMuted = !current.isInternalMuted))
        }
    }

    fun setCropExtraScreen(enabled: Boolean) {
        val current = config.value
        configManager.updateConfig(current.copy(cropExtraScreen = enabled))
    }

    fun setFaceCamEnabled(enabled: Boolean) {
        val current = config.value
        configManager.updateConfig(
            current.copy(faceCam = current.faceCam.copy(isEnabled = enabled))
        )
    }

    fun setFaceCamSize(sizeDp: Int) {
        val current = config.value
        configManager.updateConfig(
            current.copy(faceCam = current.faceCam.copy(sizeDp = sizeDp))
        )
    }

    fun setFaceCamZoom(zoom: Float) {
        val current = config.value
        configManager.updateConfig(
            current.copy(faceCam = current.faceCam.copy(zoomLevel = zoom))
        )
    }

    fun setFaceCamFront(isFront: Boolean) {
        val current = config.value
        configManager.updateConfig(
            current.copy(faceCam = current.faceCam.copy(isFrontCamera = isFront))
        )
    }

    fun setFaceCamCircle(isCircle: Boolean) {
        val current = config.value
        configManager.updateConfig(
            current.copy(faceCam = current.faceCam.copy(isCircleShape = isCircle))
        )
    }

    fun setFaceCamOverlay(enabled: Boolean) {
        val current = config.value
        configManager.updateConfig(
            current.copy(faceCam = current.faceCam.copy(isFloatingOverlayEnabled = enabled))
        )
    }

    fun startStream(resultCode: Int, data: Intent) {
        val context = getApplication<Application>()
        StreamController.startService(context, config.value, resultCode, data)
    }

    fun stopStream() {
        val context = getApplication<Application>()
        StreamController.stopService(context)
    }

    fun testConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanUrl = config.value.rtmpUrl.trim()
                val isSsl = cleanUrl.startsWith("rtmps://", ignoreCase = true)
                val defaultPort = if (isSsl) 443 else 1935
                val uri = URI(cleanUrl)
                val host = uri.host ?: "a.rtmp.youtube.com"
                val port = if (uri.port != -1) uri.port else defaultPort

                val socket = if (isSsl) {
                    SSLSocketFactory.getDefault().createSocket()
                } else {
                    Socket()
                }
                socket.connect(InetSocketAddress(host, port), 5000)
                socket.close()

                withContext(Dispatchers.Main) {
                    onResult(true, "Successfully reached YouTube RTMP server ($host:$port)")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Connection test failed: ${e.message}")
                }
            }
        }
    }
}
