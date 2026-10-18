package com.example.stream.data

import android.content.Context
import android.content.SharedPreferences
import com.example.stream.model.AudioSourceMode
import com.example.stream.model.FaceCamConfig
import com.example.stream.model.StreamConfig
import com.example.stream.model.StreamOrientation
import com.example.stream.model.StreamResolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StreamConfigManager(context: Context) {
    companion object {
        private const val PREFS_NAME = "stream_config_prefs"
        private const val KEY_RTMP_URL = "rtmp_url"
        private const val KEY_STREAM_KEY = "stream_key"
        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_RESOLUTION = "resolution"
        private const val KEY_VIDEO_BITRATE = "video_bitrate"
        private const val KEY_AUDIO_BITRATE = "audio_bitrate"
        private const val KEY_FRAME_RATE = "frame_rate"
        private const val KEY_AUDIO_MODE = "audio_mode"
        private const val KEY_MIC_VOLUME = "mic_volume"
        private const val KEY_INTERNAL_VOLUME = "internal_volume"
        private const val KEY_MIC_MUTED = "mic_muted"
        private const val KEY_INTERNAL_MUTED = "internal_muted"
        private const val KEY_CROP_EXTRA = "crop_extra"
        private const val KEY_FACECAM_ENABLED = "facecam_enabled"
        private const val KEY_FACECAM_SIZE = "facecam_size"
        private const val KEY_FACECAM_ZOOM = "facecam_zoom"
        private const val KEY_FACECAM_FRONT = "facecam_front"
        private const val KEY_FACECAM_CIRCLE = "facecam_circle"
        private const val KEY_FACECAM_OVERLAY = "facecam_overlay"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<StreamConfig> = _configFlow.asStateFlow()

    fun loadConfig(): StreamConfig {
        val rtmpUrl = prefs.getString(KEY_RTMP_URL, "rtmp://a.rtmp.youtube.com/live2") ?: "rtmp://a.rtmp.youtube.com/live2"
        val streamKey = prefs.getString(KEY_STREAM_KEY, "") ?: ""
        val orientationName = prefs.getString(KEY_ORIENTATION, StreamOrientation.PORTRAIT.name) ?: StreamOrientation.PORTRAIT.name
        val orientation = runCatching { StreamOrientation.valueOf(orientationName) }.getOrDefault(StreamOrientation.PORTRAIT)

        val resolutionName = prefs.getString(KEY_RESOLUTION, StreamResolution.RES_1080P.name) ?: StreamResolution.RES_1080P.name
        val resolution = runCatching { StreamResolution.valueOf(resolutionName) }.getOrDefault(StreamResolution.RES_1080P)

        val videoBitrate = prefs.getInt(KEY_VIDEO_BITRATE, 4500)
        val audioBitrate = prefs.getInt(KEY_AUDIO_BITRATE, 128)
        val frameRate = prefs.getInt(KEY_FRAME_RATE, 30)

        val audioModeName = prefs.getString(KEY_AUDIO_MODE, AudioSourceMode.BOTH.name) ?: AudioSourceMode.BOTH.name
        val audioMode = runCatching { AudioSourceMode.valueOf(audioModeName) }.getOrDefault(AudioSourceMode.BOTH)

        val micVol = prefs.getFloat(KEY_MIC_VOLUME, 1.0f)
        val intVol = prefs.getFloat(KEY_INTERNAL_VOLUME, 0.8f)
        val micMuted = prefs.getBoolean(KEY_MIC_MUTED, false)
        val intMuted = prefs.getBoolean(KEY_INTERNAL_MUTED, false)
        val cropExtra = prefs.getBoolean(KEY_CROP_EXTRA, true)

        val faceCam = FaceCamConfig(
            isEnabled = prefs.getBoolean(KEY_FACECAM_ENABLED, false),
            sizeDp = prefs.getInt(KEY_FACECAM_SIZE, 140),
            zoomLevel = prefs.getFloat(KEY_FACECAM_ZOOM, 1.0f),
            isFrontCamera = prefs.getBoolean(KEY_FACECAM_FRONT, true),
            isCircleShape = prefs.getBoolean(KEY_FACECAM_CIRCLE, false),
            isFloatingOverlayEnabled = prefs.getBoolean(KEY_FACECAM_OVERLAY, true)
        )

        return StreamConfig(
            rtmpUrl = rtmpUrl,
            streamKey = streamKey,
            orientation = orientation,
            resolution = resolution,
            videoBitrateKbps = videoBitrate,
            audioBitrateKbps = audioBitrate,
            frameRate = frameRate,
            audioMode = audioMode,
            micVolume = micVol,
            internalVolume = intVol,
            isMicMuted = micMuted,
            isInternalMuted = intMuted,
            cropExtraScreen = cropExtra,
            faceCam = faceCam
        )
    }

    fun updateConfig(config: StreamConfig) {
        prefs.edit()
            .putString(KEY_RTMP_URL, config.rtmpUrl)
            .putString(KEY_STREAM_KEY, config.streamKey)
            .putString(KEY_ORIENTATION, config.orientation.name)
            .putString(KEY_RESOLUTION, config.resolution.name)
            .putInt(KEY_VIDEO_BITRATE, config.videoBitrateKbps)
            .putInt(KEY_AUDIO_BITRATE, config.audioBitrateKbps)
            .putInt(KEY_FRAME_RATE, config.frameRate)
            .putString(KEY_AUDIO_MODE, config.audioMode.name)
            .putFloat(KEY_MIC_VOLUME, config.micVolume)
            .putFloat(KEY_INTERNAL_VOLUME, config.internalVolume)
            .putBoolean(KEY_MIC_MUTED, config.isMicMuted)
            .putBoolean(KEY_INTERNAL_MUTED, config.isInternalMuted)
            .putBoolean(KEY_CROP_EXTRA, config.cropExtraScreen)
            .putBoolean(KEY_FACECAM_ENABLED, config.faceCam.isEnabled)
            .putInt(KEY_FACECAM_SIZE, config.faceCam.sizeDp)
            .putFloat(KEY_FACECAM_ZOOM, config.faceCam.zoomLevel)
            .putBoolean(KEY_FACECAM_FRONT, config.faceCam.isFrontCamera)
            .putBoolean(KEY_FACECAM_CIRCLE, config.faceCam.isCircleShape)
            .putBoolean(KEY_FACECAM_OVERLAY, config.faceCam.isFloatingOverlayEnabled)
            .apply()

        _configFlow.value = config
    }
}
