package com.example.stream.model

enum class StreamOrientation(val title: String, val ratioLabel: String, val width1080: Int, val height1080: Int) {
    LANDSCAPE("Horizontal (Landscape)", "16:9 (1920x1080)", 1920, 1080),
    PORTRAIT("Vertical (Portrait)", "9:16 (1080x1920)", 1080, 1920);

    val isPortrait: Boolean get() = this == PORTRAIT
}

enum class StreamResolution(val label: String, val landscapeW: Int, val landscapeH: Int, val portraitW: Int, val portraitH: Int, val defaultBitrateKbps: Int) {
    RES_1080P("1080p (Full HD)", 1920, 1080, 1080, 1920, 4500),
    RES_720P("720p (HD)", 1280, 720, 720, 1280, 2500),
    RES_480P("480p (SD)", 848, 480, 480, 848, 1200);

    fun getWidth(orientation: StreamOrientation): Int =
        if (orientation == StreamOrientation.LANDSCAPE) landscapeW else portraitW

    fun getHeight(orientation: StreamOrientation): Int =
        if (orientation == StreamOrientation.LANDSCAPE) landscapeH else portraitH
}

enum class AudioSourceMode(val label: String, val description: String) {
    BOTH("Internal + Microphone", "Stream game/app audio mixed with your voice"),
    MIC_ONLY("Microphone Only", "Stream only your microphone commentary"),
    INTERNAL_ONLY("Internal Audio Only", "Stream only system/game audio, no mic"),
    MUTED("Muted (Silence)", "No sound transmitted")
}

enum class AudioQuality(
    val kbps: Int,
    val title: String,
    val subtitle: String
) {
    LOW(64, "64 kbps", "Low Data (Voice)"),
    STANDARD(128, "128 kbps", "Standard (YouTube Default)"),
    HIGH(160, "160 kbps", "High Quality"),
    ULTRA(192, "192 kbps", "Ultra / Hi-Fi");

    companion object {
        fun fromKbps(kbps: Int): AudioQuality =
            entries.find { it.kbps == kbps } ?: STANDARD
    }
}

data class FaceCamConfig(
    val isEnabled: Boolean = false,
    val sizeDp: Int = 140, // 100=Small, 140=Medium, 190=Large
    val zoomLevel: Float = 1.0f,
    val isFrontCamera: Boolean = true,
    val isCircleShape: Boolean = false,
    val isFloatingOverlayEnabled: Boolean = true
)

data class StreamConfig(
    val rtmpUrl: String = "rtmp://a.rtmp.youtube.com/live2",
    val streamKey: String = "",
    val orientation: StreamOrientation = StreamOrientation.PORTRAIT,
    val resolution: StreamResolution = StreamResolution.RES_1080P,
    val videoBitrateKbps: Int = 4500,
    val audioBitrateKbps: Int = 128,
    val frameRate: Int = 30,
    val audioMode: AudioSourceMode = AudioSourceMode.BOTH,
    val micVolume: Float = 1.0f,
    val internalVolume: Float = 0.8f,
    val isMicMuted: Boolean = false,
    val isInternalMuted: Boolean = false,
    val cropExtraScreen: Boolean = true,
    val faceCam: FaceCamConfig = FaceCamConfig()
) {
    val fullStreamUrl: String
        get() {
            val base = rtmpUrl.trimEnd('/')
            val key = streamKey.trim()
            return if (key.isEmpty()) base else "$base/$key"
        }
}

enum class StreamStatus {
    IDLE,
    PREPARING,
    CONNECTING,
    STREAMING,
    RECONNECTING,
    ERROR,
    STOPPED
}

data class StreamTelemetry(
    val status: StreamStatus = StreamStatus.IDLE,
    val durationSeconds: Long = 0,
    val currentBitrateKbps: Int = 0,
    val currentFps: Float = 0.0f,
    val totalBytesSent: Long = 0,
    val droppedFrames: Int = 0,
    val micLevelDb: Float = -60f,
    val internalLevelDb: Float = -60f,
    val errorMessage: String? = null
) {
    val formattedDuration: String
        get() {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val formattedDataSent: String
        get() {
            val mb = totalBytesSent / (1024.0 * 1024.0)
            return if (mb > 1024.0) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                String.format("%.1f MB", mb)
            }
        }
}
