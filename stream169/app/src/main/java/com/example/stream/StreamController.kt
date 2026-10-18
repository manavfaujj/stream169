package com.example.stream

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import com.example.stream.model.StreamConfig
import com.example.stream.model.StreamStatus
import com.example.stream.model.StreamTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object StreamController {
    private val _telemetry = MutableStateFlow(StreamTelemetry())
    val telemetry: StateFlow<StreamTelemetry> = _telemetry.asStateFlow()

    private val _activeConfig = MutableStateFlow<StreamConfig?>(null)
    val activeConfig: StateFlow<StreamConfig?> = _activeConfig.asStateFlow()

    var mediaProjectionIntent: Intent? = null
    var mediaProjectionResultCode: Int = 0

    fun updateTelemetry(update: (StreamTelemetry) -> StreamTelemetry) {
        _telemetry.value = update(_telemetry.value)
    }

    fun setStatus(status: StreamStatus, errorMessage: String? = null) {
        _telemetry.value = _telemetry.value.copy(
            status = status,
            errorMessage = errorMessage
        )
    }

    fun setActiveConfig(config: StreamConfig?) {
        _activeConfig.value = config
    }

    fun resetTelemetry() {
        _telemetry.value = StreamTelemetry(status = StreamStatus.IDLE)
    }

    fun startService(context: Context, config: StreamConfig, resultCode: Int, intentData: Intent) {
        this.mediaProjectionResultCode = resultCode
        this.mediaProjectionIntent = intentData
        this.setActiveConfig(config)
        this.setStatus(StreamStatus.PREPARING)

        val serviceIntent = Intent(context, ScreenStreamService::class.java).apply {
            action = ScreenStreamService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun stopService(context: Context) {
        val serviceIntent = Intent(context, ScreenStreamService::class.java).apply {
            action = ScreenStreamService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }

    fun toggleMicMute(context: Context) {
        val serviceIntent = Intent(context, ScreenStreamService::class.java).apply {
            action = ScreenStreamService.ACTION_TOGGLE_MIC
        }
        context.startService(serviceIntent)
    }

    fun toggleInternalMute(context: Context) {
        val serviceIntent = Intent(context, ScreenStreamService::class.java).apply {
            action = ScreenStreamService.ACTION_TOGGLE_INTERNAL
        }
        context.startService(serviceIntent)
    }
}
