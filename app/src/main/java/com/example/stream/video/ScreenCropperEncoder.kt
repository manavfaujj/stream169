package com.example.stream.video

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.example.stream.model.StreamConfig
import com.example.stream.model.StreamOrientation
import com.example.stream.rtmp.RtmpClient
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCropperEncoder(
    private val config: StreamConfig,
    private val mediaProjection: MediaProjection,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val screenDpi: Int,
    private val rtmpClient: RtmpClient,
    private val onFpsUpdated: (Float) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "ScreenCropperEncoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
    }

    private var mediaCodec: MediaCodec? = null
    private var eglHelper: EglHelper? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null

    private var drainThread: Thread? = null
    private val isRunning = AtomicBoolean(false)

    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()

    private var lastFrameDrawTime = 0L
    private var lastKeyframeRequestTime = 0L
    private val frameIntervalMs = (1000L / config.frameRate.coerceAtLeast(15)).coerceAtLeast(16L)

    private var hasSentAvcHeader = false
    private var lastSps: ByteArray? = null
    private var lastPps: ByteArray? = null

    data class Nalu(val type: Int, val data: ByteArray)

    private val frameRepeaterRunnable = object : Runnable {
        override fun run() {
            if (!isRunning.get()) return

            val now = System.currentTimeMillis()
            val egl = eglHelper
            if (egl != null) {
                // If no hardware frame was produced recently by VirtualDisplay (static screen),
                // redraw the last frame to keep video packets flowing continuously to YouTube.
                if (now - lastFrameDrawTime >= frameIntervalMs) {
                    try {
                        val timestampNs = System.nanoTime()
                        egl.drawFrame(timestampNs, updateTex = false)
                        lastFrameDrawTime = now
                        calculateFps()
                    } catch (e: Exception) {
                        Log.w(TAG, "Frame repeater draw error: ${e.message}")
                    }
                }

                // Force IDR Keyframe every 2 seconds to fulfill YouTube Live GOP requirements
                if (now - lastKeyframeRequestTime >= 2000L) {
                    requestSyncFrame()
                    lastKeyframeRequestTime = now
                }
            }

            renderHandler?.postDelayed(this, frameIntervalMs)
        }
    }

    fun start() {
        try {
            val isLandscape = config.orientation == StreamOrientation.LANDSCAPE
            val targetWidth = config.resolution.getWidth(config.orientation)
            val targetHeight = config.resolution.getHeight(config.orientation)

            // Use actual screen dimensions directly without forcing horizontal rotation
            val captureWidth = screenWidth
            val captureHeight = screenHeight

            Log.d(TAG, "Starting encoder: target=${targetWidth}x${targetHeight}, capture=${captureWidth}x${captureHeight}, isLandscape=$isLandscape")

            val format = MediaFormat.createVideoFormat(MIME_TYPE, targetWidth, targetHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrateKbps * 1000)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                try {
                    setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                } catch (_: Exception) {}
            }

            val codec = MediaCodec.createEncoderByType(MIME_TYPE)
            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (e: Exception) {
                Log.w(TAG, "Configure with VBR failed, retrying standard format: ${e.message}")
                val fallbackFormat = MediaFormat.createVideoFormat(MIME_TYPE, targetWidth, targetHeight).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrateKbps * 1000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                }
                codec.configure(fallbackFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            val inputSurface = codec.createInputSurface()
            codec.start()
            mediaCodec = codec

            renderThread = HandlerThread("GLRenderThread").apply { start() }
            val handler = Handler(renderThread!!.looper)
            renderHandler = handler

            // Initialize OpenGL context on render thread
            handler.post {
                try {
                    val egl = EglHelper(
                        encoderInputSurface = inputSurface,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        screenWidth = captureWidth,
                        screenHeight = captureHeight
                    )
                    egl.updateCropCoordinates(
                        screenWidth = captureWidth,
                        screenHeight = captureHeight,
                        isLandscape = isLandscape
                    )
                    eglHelper = egl

                    val displaySurface = egl.displaySurface
                    virtualDisplay = mediaProjection.createVirtualDisplay(
                        "Stream169Display",
                        captureWidth,
                        captureHeight,
                        screenDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        displaySurface,
                        null,
                        handler
                    )

                    egl.surfaceTexture?.setOnFrameAvailableListener({
                        if (isRunning.get()) {
                            val timestampNs = System.nanoTime()
                            egl.drawFrame(timestampNs, updateTex = true)
                            lastFrameDrawTime = System.currentTimeMillis()
                            calculateFps()
                        }
                    }, handler)

                    // Start frame repeater to ensure YouTube never experiences stream starvation on static screens
                    lastFrameDrawTime = System.currentTimeMillis()
                    lastKeyframeRequestTime = System.currentTimeMillis()
                    handler.post(frameRepeaterRunnable)

                } catch (e: Exception) {
                    Log.e(TAG, "EGL / VirtualDisplay setup failed", e)
                    onError("Failed to setup display capture: ${e.message}")
                }
            }

            isRunning.set(true)
            startDrainLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Screen encoder start failed", e)
            onError(e.message ?: "Failed to initialize video encoder")
        }
    }

    fun requestSyncFrame() {
        try {
            val bundle = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            mediaCodec?.setParameters(bundle)
        } catch (_: Exception) {}
    }

    private fun calculateFps() {
        frameCount++
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsTimestamp
        if (elapsed >= 1000) {
            val fps = (frameCount * 1000f) / elapsed
            onFpsUpdated(fps)
            frameCount = 0
            lastFpsTimestamp = now
        }
    }

    private fun startDrainLoop() {
        drainThread = Thread({
            val codec = mediaCodec ?: return@Thread
            val bufferInfo = MediaCodec.BufferInfo()

            while (isRunning.get()) {
                try {
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            val isKeyframe = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0

                            val bytes = ByteArray(bufferInfo.size)
                            outputBuffer.get(bytes)

                            val nalus = splitAnnexB(bytes)
                            var hasIdr = false

                            for (nalu in nalus) {
                                if (nalu.type == 7) {
                                    lastSps = nalu.data
                                } else if (nalu.type == 8) {
                                    lastPps = nalu.data
                                } else if (nalu.type == 5) {
                                    hasIdr = true
                                }
                            }

                            if (lastSps != null && lastPps != null && (!hasSentAvcHeader || isConfig)) {
                                rtmpClient.sendAvcSequenceHeader(lastSps!!, lastPps!!)
                                hasSentAvcHeader = true
                            }

                            if (!isConfig) {
                                // Build standard AVCC payload where each NALU is prefixed with 4-byte length
                                val avccBaos = ByteArrayOutputStream()
                                val avccDos = DataOutputStream(avccBaos)

                                for (nalu in nalus) {
                                    // Slices: 1 (non-IDR), 5 (IDR), 6 (SEI), etc.
                                    if (nalu.type != 7 && nalu.type != 8) {
                                        avccDos.writeInt(nalu.data.size)
                                        avccDos.write(nalu.data)
                                    }
                                }
                                avccDos.flush()
                                val avccPayload = avccBaos.toByteArray()

                                if (avccPayload.isNotEmpty()) {
                                    rtmpClient.queueVideoFrame(
                                        avccPayload = avccPayload,
                                        isKeyframe = isKeyframe || hasIdr,
                                        presentationTimeUs = bufferInfo.presentationTimeUs
                                    )
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = codec.outputFormat
                        Log.d(TAG, "Encoder output format changed: $newFormat")
                        val sps = newFormat.getByteBuffer("csd-0")
                        val pps = newFormat.getByteBuffer("csd-1")
                        if (sps != null && pps != null) {
                            val spsBytes = stripStartCode(sps)
                            val ppsBytes = stripStartCode(pps)
                            lastSps = spsBytes
                            lastPps = ppsBytes
                            rtmpClient.sendAvcSequenceHeader(spsBytes, ppsBytes)
                            hasSentAvcHeader = true
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.e(TAG, "Drain loop error", e)
                    }
                    break
                }
            }
        }, "VideoDrainThread").apply { start() }
    }

    private fun stripStartCode(buffer: ByteBuffer): ByteArray {
        val size = buffer.remaining()
        val data = ByteArray(size)
        buffer.get(data)
        if (size >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 0.toByte() && data[3] == 1.toByte()) {
            val clean = ByteArray(size - 4)
            System.arraycopy(data, 4, clean, 0, size - 4)
            return clean
        } else if (size >= 3 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 1.toByte()) {
            val clean = ByteArray(size - 3)
            System.arraycopy(data, 3, clean, 0, size - 3)
            return clean
        }
        return data
    }

    private fun splitAnnexB(data: ByteArray): List<Nalu> {
        val nalus = mutableListOf<Nalu>()
        val offsets = mutableListOf<Pair<Int, Int>>() // Pair(startCodeIndex, headerLength)
        var i = 0
        val len = data.size
        while (i < len - 2) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (data[i + 2] == 1.toByte()) {
                    offsets.add(Pair(i, 3))
                    i += 3
                    continue
                } else if (i < len - 3 && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                    offsets.add(Pair(i, 4))
                    i += 4
                    continue
                }
            }
            i++
        }

        if (offsets.isEmpty()) {
            if (data.isNotEmpty()) {
                val nalType = data[0].toInt() and 0x1F
                nalus.add(Nalu(nalType, data))
            }
            return nalus
        }

        for (k in 0 until offsets.size) {
            val (startPos, headerLen) = offsets[k]
            val naluStart = startPos + headerLen
            val naluEnd = if (k + 1 < offsets.size) offsets[k + 1].first else len
            val naluSize = naluEnd - naluStart
            if (naluSize > 0) {
                val naluBytes = ByteArray(naluSize)
                System.arraycopy(data, naluStart, naluBytes, 0, naluSize)
                val nalType = naluBytes[0].toInt() and 0x1F
                nalus.add(Nalu(nalType, naluBytes))
            }
        }
        return nalus
    }

    fun stop() {
        isRunning.set(false)
        try {
            drainThread?.interrupt()
            drainThread = null
        } catch (_: Exception) {}

        renderHandler?.let { handler ->
            handler.removeCallbacks(frameRepeaterRunnable)
            handler.post {
                try {
                    virtualDisplay?.release()
                    virtualDisplay = null
                    eglHelper?.release()
                    eglHelper = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping EGL", e)
                }
            }
        }

        try {
            renderThread?.quitSafely()
            renderThread = null
        } catch (_: Exception) {}

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaCodec", e)
        }
    }
}
