package com.example.stream.model

enum class StreamOrientation(val label: String, val aspectRatioText: String) {
    HORIZONTAL_16_9("Horizontal (16:9)", "Landscape gaming & wide streaming"),
    VERTICAL_9_16("Vertical (9:16)", "Shorts & vertical mobile stream")
}

enum class StreamResolution(val label: String, val width: Int, val height: Int, val defaultBitrate: Int) {
    RES_1080P("1080p FHD", 1920, 1080, 6_000_000),
    RES_720P("720p HD", 1280, 720, 3_500_000),
    RES_480P("480p SD", 854, 480, 1_800_000);

    fun getOutputDimensions(orientation: StreamOrientation): Pair<Int, Int> {
        return if (orientation == StreamOrientation.HORIZONTAL_16_9) {
            width to height
        } else {
            height to width
        }
    }
}

enum class FaceCamShape {
    CIRCLE,
    ROUNDED_SQUARE
}

enum class StreamStatus {
    IDLE,
    CONNECTING,
    LIVE,
    RECONNECTING,
    ERROR
}

data class StreamConfig(
    val rtmpUrl: String = "rtmp://a.rtmp.youtube.com/live2",
    val streamKey: String = "",
    val orientation: StreamOrientation = StreamOrientation.HORIZONTAL_16_9,
    val resolution: StreamResolution = StreamResolution.RES_720P,
    val fps: Int = 60,
    val videoBitrate: Int = 4_000_000,
    val audioBitrate: Int = 128_000,
    val sampleRate: Int = 44_100,
    val isMicEnabled: Boolean = true,
    val micVolume: Float = 1.0f,
    val isInternalAudioEnabled: Boolean = true,
    val internalAudioVolume: Float = 1.0f,
    val isFaceCamEnabled: Boolean = false,
    val faceCamShape: FaceCamShape = FaceCamShape.CIRCLE,
    val faceCamSizeDp: Int = 120
) {
    val fullRtmpUrl: String
        get() {
            val base = rtmpUrl.trim().trimEnd('/')
            val key = streamKey.trim()
            return if (key.isNotEmpty()) "$base/$key" else base
        }
}
