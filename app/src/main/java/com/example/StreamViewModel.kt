package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.stream.ScreenStreamService
import com.example.stream.model.FaceCamShape
import com.example.stream.model.StreamConfig
import com.example.stream.model.StreamOrientation
import com.example.stream.model.StreamResolution
import com.example.stream.model.StreamStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StreamViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("stream169_prefs", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<StreamConfig> = _config.asStateFlow()

    val streamStatus: StateFlow<StreamStatus> = ScreenStreamService.streamStatusFlow
    val statusMessage: StateFlow<String> = ScreenStreamService.statusMessageFlow

    private fun loadConfig(): StreamConfig {
        val rtmpUrl = prefs.getString("rtmp_url", "rtmp://a.rtmp.youtube.com/live2") ?: "rtmp://a.rtmp.youtube.com/live2"
        val streamKey = prefs.getString("stream_key", "") ?: ""
        val orientation = StreamOrientation.valueOf(
            prefs.getString("orientation", StreamOrientation.HORIZONTAL_16_9.name) ?: StreamOrientation.HORIZONTAL_16_9.name
        )
        val resolution = StreamResolution.valueOf(
            prefs.getString("resolution", StreamResolution.RES_720P.name) ?: StreamResolution.RES_720P.name
        )
        val fps = prefs.getInt("fps", 60)
        val videoBitrate = prefs.getInt("video_bitrate", 4_000_000)
        val micVol = prefs.getFloat("mic_volume", 1.0f)
        val intAudioVol = prefs.getFloat("internal_volume", 1.0f)
        val micEn = prefs.getBoolean("mic_enabled", true)
        val intEn = prefs.getBoolean("internal_enabled", true)
        val faceCamEn = prefs.getBoolean("facecam_enabled", false)
        val faceCamShape = FaceCamShape.valueOf(
            prefs.getString("facecam_shape", FaceCamShape.CIRCLE.name) ?: FaceCamShape.CIRCLE.name
        )
        val faceCamSize = prefs.getInt("facecam_size", 120)

        return StreamConfig(
            rtmpUrl = rtmpUrl,
            streamKey = streamKey,
            orientation = orientation,
            resolution = resolution,
            fps = fps,
            videoBitrate = videoBitrate,
            micVolume = micVol,
            internalAudioVolume = intAudioVol,
            isMicEnabled = micEn,
            isInternalAudioEnabled = intEn,
            isFaceCamEnabled = faceCamEn,
            faceCamShape = faceCamShape,
            faceCamSizeDp = faceCamSize
        )
    }

    private fun saveConfig(newConfig: StreamConfig) {
        _config.value = newConfig
        prefs.edit().apply {
            putString("rtmp_url", newConfig.rtmpUrl)
            putString("stream_key", newConfig.streamKey)
            putString("orientation", newConfig.orientation.name)
            putString("resolution", newConfig.resolution.name)
            putInt("fps", newConfig.fps)
            putInt("video_bitrate", newConfig.videoBitrate)
            putFloat("mic_volume", newConfig.micVolume)
            putFloat("internal_volume", newConfig.internalAudioVolume)
            putBoolean("mic_enabled", newConfig.isMicEnabled)
            putBoolean("internal_enabled", newConfig.isInternalAudioEnabled)
            putBoolean("facecam_enabled", newConfig.isFaceCamEnabled)
            putString("facecam_shape", newConfig.faceCamShape.name)
            putInt("facecam_size", newConfig.faceCamSizeDp)
            apply()
        }
    }

    fun setRtmpUrl(url: String) {
        saveConfig(_config.value.copy(rtmpUrl = url))
    }

    fun setStreamKey(key: String) {
        saveConfig(_config.value.copy(streamKey = key))
    }

    fun setOrientation(orientation: StreamOrientation) {
        saveConfig(_config.value.copy(orientation = orientation))
    }

    fun setResolution(res: StreamResolution) {
        saveConfig(_config.value.copy(resolution = res, videoBitrate = res.defaultBitrate))
    }

    fun setFps(fps: Int) {
        saveConfig(_config.value.copy(fps = fps))
    }

    fun setVideoBitrate(bitrate: Int) {
        saveConfig(_config.value.copy(videoBitrate = bitrate))
    }

    fun setMicVolume(vol: Float) {
        val updated = _config.value.copy(micVolume = vol)
        saveConfig(updated)
        dispatchAudioUpdate(updated)
    }

    fun setMicEnabled(enabled: Boolean) {
        val updated = _config.value.copy(isMicEnabled = enabled)
        saveConfig(updated)
        dispatchAudioUpdate(updated)
    }

    fun setInternalAudioVolume(vol: Float) {
        val updated = _config.value.copy(internalAudioVolume = vol)
        saveConfig(updated)
        dispatchAudioUpdate(updated)
    }

    fun setInternalAudioEnabled(enabled: Boolean) {
        val updated = _config.value.copy(isInternalAudioEnabled = enabled)
        saveConfig(updated)
        dispatchAudioUpdate(updated)
    }

    fun setFaceCamEnabled(enabled: Boolean) {
        saveConfig(_config.value.copy(isFaceCamEnabled = enabled))
    }

    fun setFaceCamShape(shape: FaceCamShape) {
        saveConfig(_config.value.copy(faceCamShape = shape))
    }

    fun setFaceCamSize(sizeDp: Int) {
        saveConfig(_config.value.copy(faceCamSizeDp = sizeDp))
    }

    private fun dispatchAudioUpdate(c: StreamConfig) {
        if (streamStatus.value == StreamStatus.LIVE || streamStatus.value == StreamStatus.CONNECTING) {
            val intent = Intent(getApplication(), ScreenStreamService::class.java).apply {
                action = ScreenStreamService.ACTION_UPDATE_AUDIO
                putExtra(ScreenStreamService.EXTRA_MIC_VOLUME, c.micVolume)
                putExtra(ScreenStreamService.EXTRA_INTERNAL_AUDIO_VOLUME, c.internalAudioVolume)
                putExtra(ScreenStreamService.EXTRA_MIC_ENABLED, c.isMicEnabled)
                putExtra(ScreenStreamService.EXTRA_INTERNAL_AUDIO_ENABLED, c.isInternalAudioEnabled)
            }
            getApplication<Application>().startService(intent)
        }
    }
}
