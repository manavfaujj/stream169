package com.example.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.stream.audio.AudioMixerEngine
import com.example.stream.facecam.FloatingFaceCamView
import com.example.stream.model.FaceCamShape
import com.example.stream.model.StreamConfig
import com.example.stream.model.StreamOrientation
import com.example.stream.model.StreamResolution
import com.example.stream.model.StreamStatus
import com.example.stream.rtmp.RtmpClient
import com.example.stream.video.ScreenCropperEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenStreamService : Service() {

    companion object {
        private const val TAG = "ScreenStreamService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "stream_service_channel"

        const val ACTION_START = "com.example.stream.ACTION_START"
        const val ACTION_STOP = "com.example.stream.ACTION_STOP"
        const val ACTION_UPDATE_AUDIO = "com.example.stream.ACTION_UPDATE_AUDIO"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        const val EXTRA_RTMP_URL = "extra_rtmp_url"
        const val EXTRA_STREAM_KEY = "extra_stream_key"
        const val EXTRA_ORIENTATION = "extra_orientation"
        const val EXTRA_RESOLUTION = "extra_resolution"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_BITRATE = "extra_bitrate"
        const val EXTRA_MIC_ENABLED = "extra_mic_enabled"
        const val EXTRA_MIC_VOLUME = "extra_mic_volume"
        const val EXTRA_INTERNAL_AUDIO_ENABLED = "extra_internal_audio_enabled"
        const val EXTRA_INTERNAL_AUDIO_VOLUME = "extra_internal_audio_volume"
        const val EXTRA_FACECAM_ENABLED = "extra_facecam_enabled"
        const val EXTRA_FACECAM_SHAPE = "extra_facecam_shape"
        const val EXTRA_FACECAM_SIZE = "extra_facecam_size"

        private val _streamStatusFlow = MutableStateFlow(StreamStatus.IDLE)
        val streamStatusFlow = _streamStatusFlow.asStateFlow()

        private val _statusMessageFlow = MutableStateFlow("Ready to stream")
        val statusMessageFlow = _statusMessageFlow.asStateFlow()
    }

    private var mediaProjection: MediaProjection? = null
    private var rtmpClient: RtmpClient? = null
    private var videoEncoder: ScreenCropperEncoder? = null
    private var audioEngine: AudioMixerEngine? = null
    private var floatingFaceCam: FloatingFaceCamView? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

                if (resultCode != 0 && resultData != null) {
                    val config = parseConfigFromIntent(intent)
                    startForegroundServiceWithNotification("Connecting to RTMP...")
                    startStreaming(resultCode, resultData, config)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopStreaming()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_UPDATE_AUDIO -> {
                val micVol = intent.getFloatExtra(EXTRA_MIC_VOLUME, 1f)
                val intVol = intent.getFloatExtra(EXTRA_INTERNAL_AUDIO_VOLUME, 1f)
                val micEn = intent.getBooleanExtra(EXTRA_MIC_ENABLED, true)
                val intEn = intent.getBooleanExtra(EXTRA_INTERNAL_AUDIO_ENABLED, true)

                audioEngine?.apply {
                    micVolume = micVol
                    internalVolume = intVol
                    isMicEnabled = micEn
                    isInternalAudioEnabled = intEn
                }
            }
        }

        return START_STICKY
    }

    private fun parseConfigFromIntent(intent: Intent): StreamConfig {
        val rtmpUrl = intent.getStringExtra(EXTRA_RTMP_URL) ?: "rtmp://a.rtmp.youtube.com/live2"
        val streamKey = intent.getStringExtra(EXTRA_STREAM_KEY) ?: ""
        val orientation = StreamOrientation.valueOf(
            intent.getStringExtra(EXTRA_ORIENTATION) ?: StreamOrientation.HORIZONTAL_16_9.name
        )
        val resolution = StreamResolution.valueOf(
            intent.getStringExtra(EXTRA_RESOLUTION) ?: StreamResolution.RES_720P.name
        )
        val fps = intent.getIntExtra(EXTRA_FPS, 60)
        val bitrate = intent.getIntExtra(EXTRA_BITRATE, 4_000_000)

        val micEnabled = intent.getBooleanExtra(EXTRA_MIC_ENABLED, true)
        val micVol = intent.getFloatExtra(EXTRA_MIC_VOLUME, 1f)
        val intAudioEnabled = intent.getBooleanExtra(EXTRA_INTERNAL_AUDIO_ENABLED, true)
        val intAudioVol = intent.getFloatExtra(EXTRA_INTERNAL_AUDIO_VOLUME, 1f)

        val faceCamEnabled = intent.getBooleanExtra(EXTRA_FACECAM_ENABLED, false)
        val faceCamShape = FaceCamShape.valueOf(
            intent.getStringExtra(EXTRA_FACECAM_SHAPE) ?: FaceCamShape.CIRCLE.name
        )
        val faceCamSize = intent.getIntExtra(EXTRA_FACECAM_SIZE, 120)

        return StreamConfig(
            rtmpUrl = rtmpUrl,
            streamKey = streamKey,
            orientation = orientation,
            resolution = resolution,
            fps = fps,
            videoBitrate = bitrate,
            isMicEnabled = micEnabled,
            micVolume = micVol,
            isInternalAudioEnabled = intAudioEnabled,
            internalAudioVolume = intAudioVol,
            isFaceCamEnabled = faceCamEnabled,
            faceCamShape = faceCamShape,
            faceCamSizeDp = faceCamSize
        )
    }

    private fun startStreaming(resultCode: Int, resultData: Intent, config: StreamConfig) {
        _streamStatusFlow.value = StreamStatus.CONNECTING
        _statusMessageFlow.value = "Connecting to RTMP server..."

        // 1. Get MediaProjection
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        // Unified timestamp timeline
        val baseTimeUs = System.nanoTime() / 1000L

        // 2. Initialize RTMP Client
        rtmpClient = RtmpClient(
            config = config,
            onStatusChanged = { status ->
                _statusMessageFlow.value = status
                if (status == "LIVE") {
                    _streamStatusFlow.value = StreamStatus.LIVE
                    updateNotification("Streaming LIVE to YouTube")
                } else if (status.startsWith("Reconnecting")) {
                    _streamStatusFlow.value = StreamStatus.RECONNECTING
                    updateNotification("Reconnecting stream...")
                }
            },
            onError = { error ->
                _streamStatusFlow.value = StreamStatus.ERROR
                _statusMessageFlow.value = error
                updateNotification("Stream Error: $error")
            }
        ).apply {
            onRequestSyncFrame = {
                videoEncoder?.requestSyncFrame()
            }
        }

        // 3. Initialize AudioMixerEngine (hardware-paced, zero jitter, continuous AAC PTS)
        audioEngine = AudioMixerEngine(
            config = config,
            mediaProjection = mediaProjection,
            onAacPacket = { data, ptsMs, isHeader ->
                rtmpClient?.sendAudioData(data, ptsMs, isHeader)
            }
        )

        // 4. Initialize ScreenCropperEncoder (H.264 AVC)
        val proj = mediaProjection
        if (proj != null) {
            videoEncoder = ScreenCropperEncoder(
                config = config,
                mediaProjection = proj,
                onEncodedSample = { data, ptsMs, isKeyframe, isHeader ->
                    rtmpClient?.sendVideoData(data, ptsMs, isKeyframe, isHeader)
                }
            )
        }

        // 5. Start Floating Face Cam overlay if requested
        if (config.isFaceCamEnabled) {
            try {
                floatingFaceCam = FloatingFaceCamView(this)
                floatingFaceCam?.show(config.faceCamShape, config.faceCamSizeDp)
            } catch (e: Exception) {
                Log.w(TAG, "Floating Face Cam failed to start: ${e.message}")
            }
        }

        // 6. Connect & Start Pipelines
        rtmpClient?.start()
        audioEngine?.start(baseTimeUs)
        videoEncoder?.start(baseTimeUs)
    }

    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Stream169:KeepAliveWakeLock").apply {
                setReferenceCounted(false)
                acquire(12 * 3600 * 1000L) // 12 hours
            }

            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm.createWifiLock(wifiMode, "Stream169:KeepAliveWifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire power/wifi locks: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wifiLock = null
        } catch (_: Exception) {}
    }

    private fun stopStreaming() {
        _streamStatusFlow.value = StreamStatus.IDLE
        _statusMessageFlow.value = "Stream stopped"

        floatingFaceCam?.dismiss()
        floatingFaceCam = null

        audioEngine?.stop()
        audioEngine = null

        videoEncoder?.stop()
        videoEncoder = null

        rtmpClient?.stop()
        rtmpClient = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
    }

    private fun startForegroundServiceWithNotification(content: String) {
        val notification = buildNotification(content)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            type
        } else 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(content: String) {
        val notification = buildNotification(content)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenStreamService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stream 169 - Screen Broadcast")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Stream", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Screen Streaming",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows live streaming status and controls"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopStreaming()
        releaseLocks()
        super.onDestroy()
    }
}
