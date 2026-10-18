package com.example.stream.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.stream.model.AudioSourceMode
import com.example.stream.model.StreamConfig
import com.example.stream.rtmp.RtmpClient
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AudioMixerEngine(
    private var config: StreamConfig,
    private val mediaProjection: MediaProjection?,
    private val rtmpClient: RtmpClient,
    private val onLevelsUpdated: (micDb: Float, internalDb: Float) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "AudioMixerEngine"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_COUNT = 2
        private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val SAMPLES_PER_CHANNEL = 1024
        private const val SAMPLES_PER_FRAME = SAMPLES_PER_CHANNEL * CHANNEL_COUNT // 2048 shorts (1024 stereo frames)
        private const val QUEUE_CAPACITY = 16
    }

    private var micRecord: AudioRecord? = null
    private var internalRecord: AudioRecord? = null
    private var audioCodec: MediaCodec? = null

    private val isRunning = AtomicBoolean(false)
    private var micThread: Thread? = null
    private var internalThread: Thread? = null
    private var mixerThread: Thread? = null
    private var drainThread: Thread? = null

    // Jitter-free FIFO queues for continuous, uninterrupted PCM frames
    private val micQueue = ArrayBlockingQueue<ShortArray>(QUEUE_CAPACITY)
    private val internalQueue = ArrayBlockingQueue<ShortArray>(QUEUE_CAPACITY)

    fun updateConfig(newConfig: StreamConfig) {
        val oldBitrate = this.config.audioBitrateKbps
        this.config = newConfig
        if (oldBitrate != newConfig.audioBitrateKbps && audioCodec != null) {
            try {
                val params = android.os.Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, newConfig.audioBitrateKbps * 1000)
                }
                audioCodec?.setParameters(params)
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(8192)
            val bufferSizeInBytes = (minBuf * 4).coerceAtLeast(16384)

            // 1. Initialize Microphone AudioRecord
            if (config.audioMode == AudioSourceMode.BOTH || config.audioMode == AudioSourceMode.MIC_ONLY) {
                try {
                    val record = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSizeInBytes
                    )
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        record.startRecording()
                        micRecord = record
                    } else {
                        Log.w(TAG, "Mic AudioRecord failed to initialize")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start Mic recording: ${e.message}")
                }
            }

            // 2. Initialize Internal Audio AudioRecord (Android 10+ with MediaProjection)
            if ((config.audioMode == AudioSourceMode.BOTH || config.audioMode == AudioSourceMode.INTERNAL_ONLY)
                && mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ) {
                try {
                    val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build()

                    val internalFormat = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()

                    val record = AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(captureConfig)
                        .setAudioFormat(internalFormat)
                        .setBufferSizeInBytes(bufferSizeInBytes)
                        .build()

                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        record.startRecording()
                        internalRecord = record
                    } else {
                        Log.w(TAG, "Internal AudioRecord failed to initialize")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start Internal Audio recording: ${e.message}")
                }
            }

            // 3. Initialize AAC Audio MediaCodec
            val format = MediaFormat.createAudioFormat(AUDIO_MIME, SAMPLE_RATE, CHANNEL_COUNT).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, config.audioBitrateKbps * 1000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, SAMPLES_PER_FRAME * 2 * 2)
            }

            val codec = MediaCodec.createEncoderByType(AUDIO_MIME)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            audioCodec = codec

            isRunning.set(true)

            // Start capture and mixer loops
            startMicCaptureLoop()
            startInternalCaptureLoop()
            startMixerLoop()
            startDrainLoop()

        } catch (e: Exception) {
            Log.e(TAG, "Audio engine start error", e)
            onError(e.message ?: "Failed to start audio engine")
        }
    }

    private fun startMicCaptureLoop() {
        val record = micRecord ?: return
        micThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = ShortArray(SAMPLES_PER_FRAME)
            var accumulated = 0

            while (isRunning.get()) {
                val toRead = SAMPLES_PER_FRAME - accumulated
                val read = record.read(buffer, accumulated, toRead)
                if (read > 0) {
                    accumulated += read
                    if (accumulated == SAMPLES_PER_FRAME) {
                        val frame = ShortArray(SAMPLES_PER_FRAME)
                        System.arraycopy(buffer, 0, frame, 0, SAMPLES_PER_FRAME)

                        // If queue is full, drop oldest frame to maintain bounded low latency
                        while (!micQueue.offer(frame) && isRunning.get()) {
                            micQueue.poll()
                        }
                        accumulated = 0
                    }
                } else if (read < 0) {
                    Log.w(TAG, "Mic read error: $read")
                    try { Thread.sleep(10) } catch (_: Exception) {}
                }
            }
        }, "MicCaptureLoop").apply { start() }
    }

    private fun startInternalCaptureLoop() {
        val record = internalRecord ?: return
        internalThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = ShortArray(SAMPLES_PER_FRAME)
            var accumulated = 0

            while (isRunning.get()) {
                val toRead = SAMPLES_PER_FRAME - accumulated
                val read = record.read(buffer, accumulated, toRead)
                if (read > 0) {
                    accumulated += read
                    if (accumulated == SAMPLES_PER_FRAME) {
                        val frame = ShortArray(SAMPLES_PER_FRAME)
                        System.arraycopy(buffer, 0, frame, 0, SAMPLES_PER_FRAME)

                        // If queue is full, drop oldest frame to maintain bounded low latency
                        while (!internalQueue.offer(frame) && isRunning.get()) {
                            internalQueue.poll()
                        }
                        accumulated = 0
                    }
                } else if (read < 0) {
                    Log.w(TAG, "Internal audio read error: $read")
                    try { Thread.sleep(10) } catch (_: Exception) {}
                }
            }
        }, "InternalCaptureLoop").apply { start() }
    }

    private fun startMixerLoop() {
        mixerThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val codec = audioCodec ?: return@Thread
            val mixedShorts = ShortArray(SAMPLES_PER_FRAME)
            val silentFrame = ShortArray(SAMPLES_PER_FRAME)
            val byteBuffer = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME * 2).order(ByteOrder.nativeOrder())

            var audioFrameIndex = 0L
            var audioStartPtsUs = -1L

            // Calculates strictly monotonic, mathematically constant PTS per frame: exactly 1024 / 44100 sec
            fun getNextAudioPtsUs(): Long {
                val nowUs = System.nanoTime() / 1000L
                if (audioStartPtsUs < 0) {
                    audioStartPtsUs = nowUs
                }
                val expectedPtsUs = audioStartPtsUs + (audioFrameIndex * SAMPLES_PER_CHANNEL.toLong() * 1_000_000L) / SAMPLE_RATE
                audioFrameIndex++

                // Gently resynchronize if clock ever drifted by > 150ms (e.g. system sleep/suspend)
                if (Math.abs(expectedPtsUs - nowUs) > 150_000L) {
                    audioStartPtsUs = nowUs
                    return nowUs
                }
                return expectedPtsUs
            }

            while (isRunning.get()) {
                try {
                    val currentConfig = config
                    val isMicActive = !currentConfig.isMicMuted &&
                            (currentConfig.audioMode == AudioSourceMode.BOTH || currentConfig.audioMode == AudioSourceMode.MIC_ONLY) &&
                            micRecord != null
                    val isInternalActive = !currentConfig.isInternalMuted &&
                            (currentConfig.audioMode == AudioSourceMode.BOTH || currentConfig.audioMode == AudioSourceMode.INTERNAL_ONLY) &&
                            internalRecord != null
                    val isMuted = currentConfig.audioMode == AudioSourceMode.MUTED || (!isMicActive && !isInternalActive)

                    var micSamples: ShortArray? = null
                    var internalSamples: ShortArray? = null

                    if (isMicActive) {
                        // Hardware clock from microphone AudioRecord paces the mixer loop
                        micSamples = micQueue.poll(40, TimeUnit.MILLISECONDS)
                        if (isInternalActive) {
                            internalSamples = internalQueue.poll()
                            // Align internal audio queue to prevent latency accumulation
                            if (internalQueue.size > 2) {
                                internalQueue.poll()
                            }
                        }
                    } else if (isInternalActive) {
                        // Hardware clock from internal audio AudioRecord paces the mixer loop
                        internalSamples = internalQueue.poll(40, TimeUnit.MILLISECONDS)
                    } else {
                        // Muted or no audio sources active: maintain constant frame cadence (~23.2ms)
                        Thread.sleep(23)
                    }

                    if (!isRunning.get()) break

                    // If an active source timed out (e.g. quiet period or OS pause), provide continuous silence
                    // so the stream never has gaps in its audio timeline
                    val activeMic = if (isMicActive) (micSamples ?: silentFrame) else null
                    val activeInternal = if (isInternalActive) (internalSamples ?: silentFrame) else null

                    var micSumSquares = 0.0
                    var intSumSquares = 0.0

                    val micVol = currentConfig.micVolume
                    val intVol = currentConfig.internalVolume

                    for (i in 0 until SAMPLES_PER_FRAME) {
                        val m = if (isMicActive && activeMic != null) {
                            val v = activeMic[i] * micVol
                            micSumSquares += v * v
                            v.toInt()
                        } else 0

                        val n = if (isInternalActive && activeInternal != null) {
                            val v = activeInternal[i] * intVol
                            intSumSquares += v * v
                            v.toInt()
                        } else 0

                        val sum = m + n
                        val mixed = if (isMuted) 0 else sum.coerceIn(-32768, 32767)
                        mixedShorts[i] = mixed.toShort()
                    }

                    // Compute live VU decibels
                    val micRms = if (activeMic != null) Math.sqrt(micSumSquares / SAMPLES_PER_FRAME) else 0.0
                    val intRms = if (activeInternal != null) Math.sqrt(intSumSquares / SAMPLES_PER_FRAME) else 0.0
                    val micDb = if (micRms > 0) (20 * Math.log10(micRms / 32767.0)).toFloat().coerceIn(-60f, 0f) else -60f
                    val intDb = if (intRms > 0) (20 * Math.log10(intRms / 32767.0)).toFloat().coerceIn(-60f, 0f) else -60f
                    onLevelsUpdated(micDb, intDb)

                    // Jitter-free continuous presentation timestamp in microseconds
                    val audioPtsUs = getNextAudioPtsUs()

                    // Feed PCM frame into MediaCodec
                    byteBuffer.clear()
                    byteBuffer.asShortBuffer().put(mixedShorts, 0, SAMPLES_PER_FRAME)

                    var inputIndex = codec.dequeueInputBuffer(10000)
                    var retries = 0
                    while (inputIndex < 0 && retries < 3 && isRunning.get()) {
                        retries++
                        inputIndex = codec.dequeueInputBuffer(10000)
                    }

                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            byteBuffer.position(0)
                            inputBuffer.put(byteBuffer)
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                SAMPLES_PER_FRAME * 2,
                                audioPtsUs,
                                0
                            )
                        }
                    } else {
                        Log.w(TAG, "Audio encoder input buffer unavailable, dropped frame to prevent lockup")
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.w(TAG, "Audio mixer loop error: ${e.message}")
                    }
                }
            }
        }, "AudioMixerLoop").apply { start() }
    }

    private fun startDrainLoop() {
        drainThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val codec = audioCodec ?: return@Thread
            val bufferInfo = MediaCodec.BufferInfo()

            while (isRunning.get()) {
                try {
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (!isConfig) {
                                val aacData = ByteArray(bufferInfo.size)
                                outputBuffer.get(aacData)

                                // Strip ADTS header if encoder added it (starts with 0xFFF)
                                val cleanAac = if (aacData.size > 7 && (aacData[0].toInt() and 0xFF) == 0xFF && (aacData[1].toInt() and 0xF0) == 0xF0) {
                                    val stripped = ByteArray(aacData.size - 7)
                                    System.arraycopy(aacData, 7, stripped, 0, stripped.size)
                                    stripped
                                } else {
                                    aacData
                                }

                                rtmpClient.queueAudioFrame(cleanAac, bufferInfo.presentationTimeUs)
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.e(TAG, "Audio drain error", e)
                    }
                    break
                }
            }
        }, "AudioDrainLoop").apply { start() }
    }

    fun stop() {
        isRunning.set(false)

        try {
            micThread?.interrupt()
            micThread = null
        } catch (_: Exception) {}

        try {
            internalThread?.interrupt()
            internalThread = null
        } catch (_: Exception) {}

        try {
            mixerThread?.interrupt()
            mixerThread = null
        } catch (_: Exception) {}

        try {
            drainThread?.interrupt()
            drainThread = null
        } catch (_: Exception) {}

        micQueue.clear()
        internalQueue.clear()

        try {
            micRecord?.stop()
            micRecord?.release()
            micRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mic record", e)
        }

        try {
            internalRecord?.stop()
            internalRecord?.release()
            internalRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping internal record", e)
        }

        try {
            audioCodec?.stop()
            audioCodec?.release()
            audioCodec = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio codec", e)
        }
    }
}
