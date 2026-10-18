package com.example.stream.rtmp

import android.util.Log
import com.example.stream.model.StreamConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocketFactory

class RtmpClient(
    private val config: StreamConfig,
    private val onStatusChanged: (String) -> Unit,
    private val onBitrateUpdated: (Int) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "RtmpClient"
        private const val DEFAULT_CHUNK_SIZE = 4096 // Standard OBS / YouTube recommended chunk size for low jitter
        private const val CSID_CONTROL = 2
        private const val CSID_COMMAND = 3
        private const val CSID_AUDIO = 4
        private const val CSID_VIDEO = 6

        private const val MSG_SET_CHUNK_SIZE = 1
        private const val MSG_USER_CONTROL = 4
        private const val MSG_AUDIO = 8
        private const val MSG_VIDEO = 9
        private const val MSG_DATA_AMF0 = 18
        private const val MSG_COMMAND_AMF0 = 20

        private const val AUDIO_QUEUE_CAPACITY = 100
        private const val VIDEO_QUEUE_CAPACITY = 200
        private const val MAX_RECONNECT_ATTEMPTS = 15
    }

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val isConnected = AtomicBoolean(false)
    private val isStreaming = AtomicBoolean(false)
    private val isReconnecting = AtomicBoolean(false)

    // Separate queues to ensure audio is prioritized and never delayed behind heavy video frames
    private val audioQueue = LinkedBlockingQueue<RtmpPacket>(AUDIO_QUEUE_CAPACITY)
    private val videoQueue = LinkedBlockingQueue<RtmpPacket>(VIDEO_QUEUE_CAPACITY)
    private var sendThread: Thread? = null
    private var readerThread: Thread? = null
    private var reconnectThread: Thread? = null

    private val totalBytesSent = AtomicLong(0)
    private var lastBitrateCalcTime = System.currentTimeMillis()
    private var bytesSinceLastCalc = 0L

    private var streamId = 1
    private var transactionId = 1.0

    // Synchronized stream start timestamp shared between video and audio for zero AV drift
    @Volatile private var streamStartPtsUs = -1L
    private var lastAudioTimestamp = -1
    private var lastVideoTimestamp = -1

    // Cached SPS & PPS for seamless re-sync after reconnect
    @Volatile private var cachedSps: ByteArray? = null
    @Volatile private var cachedPps: ByteArray? = null

    // Callback to request immediate IDR keyframe after reconnect
    var onRequestSyncFrame: (() -> Unit)? = null

    data class RtmpPacket(
        val type: Int,
        val csid: Int,
        val timestamp: Int,
        val data: ByteArray,
        val isKeyframe: Boolean = false
    )

    private class IncompleteMessage(
        val csid: Int,
        var fmt: Int = 0,
        var type: Int = 0,
        var length: Int = 0,
        var bytesRead: Int = 0,
        var buffer: ByteArray = ByteArray(0)
    )

    fun start(): Boolean {
        try {
            onStatusChanged("Connecting to RTMP server...")
            connectAndHandshake(isReconnect = false)

            isStreaming.set(true)
            isConnected.set(true)
            onStatusChanged("LIVE")

            startSenderThread()
            startReaderThread()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "RTMP initial connection failed", e)
            onError(e.message ?: "Connection failed")
            close()
            return false
        }
    }

    @Synchronized
    private fun connectAndHandshake(isReconnect: Boolean) {
        val (isSsl, host, port, appName) = parseUrl(config.rtmpUrl)
        Log.d(TAG, "Connecting to $host:$port ($appName), SSL=$isSsl, isReconnect=$isReconnect")

        val baseSocket = if (isSsl) {
            SSLSocketFactory.getDefault().createSocket()
        } else {
            Socket()
        }

        // Enable TCP Keep-Alive and optimize send/receive buffers for multi-hour stability
        baseSocket.keepAlive = true
        baseSocket.tcpNoDelay = true
        baseSocket.sendBufferSize = 1024 * 1024 // 1MB buffer to smooth out network hiccups
        baseSocket.receiveBufferSize = 64 * 1024
        baseSocket.soTimeout = 10000 // 10s handshake timeout
        baseSocket.connect(InetSocketAddress(host, port), 8000)

        socket = baseSocket
        inputStream = BufferedInputStream(baseSocket.getInputStream(), 65536)
        outputStream = BufferedOutputStream(baseSocket.getOutputStream(), 65536)

        doHandshake()
        setChunkSize(DEFAULT_CHUNK_SIZE)

        sendConnect(appName, host, port, isSsl)
        readResponsesUntilConnectResult()

        sendReleaseStream(config.streamKey)
        sendFCPublish(config.streamKey)
        sendCreateStream()
        readResponsesUntilCreateStreamResult()

        sendPublish(config.streamKey)
        sendMetaData(
            width = config.resolution.getWidth(config.orientation),
            height = config.resolution.getHeight(config.orientation),
            videoBitrate = config.videoBitrateKbps,
            fps = config.frameRate,
            audioBitrate = config.audioBitrateKbps
        )

        // Send AAC Sequence Header (44100Hz stereo)
        sendAacSequenceHeader()

        // Re-send AVC sequence header if cached
        val sps = cachedSps
        val pps = cachedPps
        if (sps != null && pps != null) {
            sendAvcSequenceHeader(sps, pps)
        }

        // Remove socket timeout for ongoing live streaming
        baseSocket.soTimeout = 0
    }

    /**
     * Resilient Auto-Reconnect:
     * When network drops or socket encounters EOF/Broken pipe, don't terminate the stream!
     * Attempt automatic reconnection in background while keeping encoders alive.
     */
    private fun triggerReconnect(reason: String) {
        if (!isStreaming.get()) return
        if (!isReconnecting.compareAndSet(false, true)) {
            // Already reconnecting
            return
        }

        Log.w(TAG, "Triggering RTMP auto-reconnect due to: $reason")
        reconnectThread = Thread({
            var attempt = 0
            while (isStreaming.get() && attempt < MAX_RECONNECT_ATTEMPTS) {
                attempt++
                val delayMs = (attempt * 1000L).coerceAtMost(5000L)
                onStatusChanged("Reconnecting to YouTube (attempt $attempt/$MAX_RECONNECT_ATTEMPTS)...")
                Log.d(TAG, "Reconnect attempt $attempt in ${delayMs}ms...")

                try {
                    Thread.sleep(delayMs)
                } catch (ie: InterruptedException) {
                    break
                }

                if (!isStreaming.get()) break

                // Safely close existing dead socket
                try {
                    outputStream?.close()
                } catch (_: Exception) {}
                try {
                    inputStream?.close()
                } catch (_: Exception) {}
                try {
                    socket?.close()
                } catch (_: Exception) {}

                try {
                    connectAndHandshake(isReconnect = true)
                    isConnected.set(true)
                    isReconnecting.set(false)
                    onStatusChanged("LIVE")
                    Log.i(TAG, "Successfully reconnected to YouTube RTMP!")

                    // Request fresh IDR keyframe from encoder immediately
                    onRequestSyncFrame?.invoke()

                    // Restart reader thread if needed
                    startReaderThread()
                    return@Thread
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect attempt $attempt failed: ${e.message}")
                }
            }

            // If we exhausted all attempts while streaming was requested
            if (isStreaming.get()) {
                isStreaming.set(false)
                isReconnecting.set(false)
                onError("Stream interrupted: Unable to reconnect to YouTube after $MAX_RECONNECT_ATTEMPTS attempts. Please check network.")
                close()
            }
        }, "RtmpReconnectThread").apply { start() }
    }

    private fun parseUrl(url: String): Quadruple<Boolean, String, Int, String> {
        val cleanUrl = url.trim()
        val isSsl = cleanUrl.startsWith("rtmps://", ignoreCase = true)
        val defaultPort = if (isSsl) 443 else 1935

        val uri = URI(cleanUrl)
        val host = uri.host ?: "a.rtmp.youtube.com"
        val port = if (uri.port != -1) uri.port else defaultPort
        val path = (uri.path ?: "/live2").trimStart('/')
        val app = if (path.isEmpty()) "live2" else path.split('/')[0]

        return Quadruple(isSsl, host, port, app)
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun doHandshake() {
        val out = outputStream ?: throw IllegalStateException("OutputStream null")
        val input = inputStream ?: throw IllegalStateException("InputStream null")

        out.write(0x03)

        val c1 = ByteArray(1536)
        val random = SecureRandom()
        random.nextBytes(c1)
        c1[0] = 0; c1[1] = 0; c1[2] = 0; c1[3] = 0
        c1[4] = 0; c1[5] = 0; c1[6] = 0; c1[7] = 0
        out.write(c1)
        out.flush()

        val s0 = input.read()
        if (s0 != 0x03) {
            throw IllegalStateException("Invalid RTMP handshake S0 version: $s0")
        }

        val s1 = readFully(input, 1536)
        val s2 = readFully(input, 1536)

        out.write(s1)
        out.flush()
    }

    private fun readFully(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var totalRead = 0
        while (totalRead < size) {
            val read = input.read(buffer, totalRead, size - totalRead)
            if (read == -1) throw IllegalStateException("Unexpected EOF in RTMP handshake")
            totalRead += read
        }
        return buffer
    }

    private fun setChunkSize(chunkSize: Int) {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeInt(chunkSize)
        dos.flush()

        sendChunk(
            csid = CSID_CONTROL,
            type = MSG_SET_CHUNK_SIZE,
            timestamp = 0,
            streamId = 0,
            payload = baos.toByteArray()
        )
    }

    private fun sendConnect(appName: String, host: String, port: Int, isSsl: Boolean) {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        Amf0.writeString(dos, "connect")
        Amf0.writeNumber(dos, transactionId++)

        val scheme = if (isSsl) "rtmps" else "rtmp"
        val tcUrl = "$scheme://$host:$port/$appName"

        val params = linkedMapOf<String, Any?>(
            "app" to appName,
            "flashVer" to "FMLE/3.0 (compatible; FMSc/1.0)",
            "swfUrl" to "",
            "tcUrl" to tcUrl,
            "fpad" to false,
            "capabilities" to 15.0,
            "audioCodecs" to 3575.0,
            "videoCodecs" to 252.0,
            "videoFunction" to 1.0
        )
        Amf0.writeObject(dos, params)
        dos.flush()

        sendChunk(CSID_COMMAND, MSG_COMMAND_AMF0, 0, 0, baos.toByteArray())
    }

    private fun sendReleaseStream(streamKey: String) {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        Amf0.writeString(dos, "releaseStream")
        Amf0.writeNumber(dos, transactionId++)
        Amf0.writeNull(dos)
        Amf0.writeString(dos, streamKey)
        dos.flush()
        sendChunk(CSID_COMMAND, MSG_COMMAND_AMF0, 0, 0, baos.toByteArray())
    }

    private fun sendFCPublish(streamKey: String) {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        Amf0.writeString(dos, "FCPublish")
        Amf0.writeNumber(dos, transactionId++)
        Amf0.writeNull(dos)
        Amf0.writeString(dos, streamKey)
        dos.flush()
        sendChunk(CSID_COMMAND, MSG_COMMAND_AMF0, 0, 0, baos.toByteArray())
    }

    private fun sendCreateStream() {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        Amf0.writeString(dos, "createStream")
        Amf0.writeNumber(dos, transactionId++)
        Amf0.writeNull(dos)
        dos.flush()
        sendChunk(CSID_COMMAND, MSG_COMMAND_AMF0, 0, 0, baos.toByteArray())
    }

    private fun sendPublish(streamKey: String) {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        Amf0.writeString(dos, "publish")
        Amf0.writeNumber(dos, transactionId++)
        Amf0.writeNull(dos)
        Amf0.writeString(dos, streamKey)
        Amf0.writeString(dos, "live")
        dos.flush()
        sendChunk(CSID_COMMAND, MSG_COMMAND_AMF0, 0, streamId, baos.toByteArray())
    }

    private fun sendMetaData(width: Int, height: Int, videoBitrate: Int, fps: Int, audioBitrate: Int) {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        Amf0.writeString(dos, "@setDataFrame")
        Amf0.writeString(dos, "onMetaData")

        val meta = linkedMapOf<String, Any?>(
            "duration" to 0.0,
            "width" to width.toDouble(),
            "height" to height.toDouble(),
            "videodatarate" to videoBitrate.toDouble(),
            "framerate" to fps.toDouble(),
            "videocodecid" to 7.0, // AVC / H.264
            "audiodatarate" to audioBitrate.toDouble(),
            "audiosamplerate" to 44100.0,
            "audiosamplesize" to 16.0,
            "stereo" to true,
            "audiocodecid" to 10.0 // AAC
        )
        Amf0.writeEcmaArray(dos, meta)
        dos.flush()

        sendChunk(CSID_COMMAND, MSG_DATA_AMF0, 0, streamId, baos.toByteArray())
    }

    private fun sendAacSequenceHeader() {
        val payload = byteArrayOf(
            0xAF.toByte(), // AAC, 44kHz, 16bit, Stereo
            0x00,          // AAC sequence header
            0x12, 0x10    // AudioSpecificConfig: AAC-LC 44100Hz Stereo
        )
        sendChunk(CSID_AUDIO, MSG_AUDIO, 0, streamId, payload)
    }

    fun sendAvcSequenceHeader(sps: ByteArray, pps: ByteArray) {
        if (sps.size < 4 || pps.isEmpty()) return

        // Cache for reconnect
        cachedSps = sps
        cachedPps = pps

        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        dos.writeByte(0x17) // Keyframe, AVC
        dos.writeByte(0x00) // AVC sequence header
        dos.writeByte(0x00) // Composition time
        dos.writeByte(0x00)
        dos.writeByte(0x00)

        dos.writeByte(0x01) // configurationVersion
        dos.writeByte(sps[1].toInt())
        dos.writeByte(sps[2].toInt())
        dos.writeByte(sps[3].toInt())
        dos.writeByte(0xFF)
        dos.writeByte(0xE1)
        dos.writeShort(sps.size)
        dos.write(sps)
        dos.writeByte(0x01)
        dos.writeShort(pps.size)
        dos.write(pps)
        dos.flush()

        sendChunk(CSID_VIDEO, MSG_VIDEO, 0, streamId, baos.toByteArray())
    }

    @Synchronized
    private fun getStreamTimestamp(presentationTimeUs: Long): Int {
        if (streamStartPtsUs < 0) {
            streamStartPtsUs = presentationTimeUs
        }
        val diffUs = presentationTimeUs - streamStartPtsUs
        return if (diffUs < 0) 0 else (diffUs / 1000).toInt()
    }

    @Synchronized
    private fun getVideoTimestamp(presentationTimeUs: Long): Int {
        val calculated = getStreamTimestamp(presentationTimeUs)
        val ts = if (lastVideoTimestamp < 0) calculated else maxOf(lastVideoTimestamp, calculated)
        lastVideoTimestamp = ts
        return ts
    }

    @Synchronized
    private fun getAudioTimestamp(presentationTimeUs: Long): Int {
        val calculated = getStreamTimestamp(presentationTimeUs)
        val ts = if (lastAudioTimestamp < 0) calculated else maxOf(lastAudioTimestamp, calculated)
        lastAudioTimestamp = ts
        return ts
    }

    fun queueVideoFrame(avccPayload: ByteArray, isKeyframe: Boolean, presentationTimeUs: Long) {
        if (!isStreaming.get()) return

        val timestamp = getVideoTimestamp(presentationTimeUs)

        val baos = ByteArrayOutputStream(avccPayload.size + 5)
        val dos = DataOutputStream(baos)

        dos.writeByte(if (isKeyframe) 0x17 else 0x27)
        dos.writeByte(0x01) // AVC NALU
        dos.writeByte(0x00) // Composition time offset
        dos.writeByte(0x00)
        dos.writeByte(0x00)

        dos.write(avccPayload)
        dos.flush()

        val packet = RtmpPacket(
            type = MSG_VIDEO,
            csid = CSID_VIDEO,
            timestamp = timestamp,
            data = baos.toByteArray(),
            isKeyframe = isKeyframe
        )

        // Congestion-controlled non-blocking queueing:
        // Drop older non-keyframes if queue is full, but always guarantee IDR keyframes get through!
        if (!videoQueue.offer(packet)) {
            if (isKeyframe) {
                val iterator = videoQueue.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    if (!p.isKeyframe) {
                        iterator.remove()
                        break
                    }
                }
                videoQueue.offer(packet)
            }
        }
    }

    fun queueAudioFrame(rawAac: ByteArray, presentationTimeUs: Long) {
        if (!isStreaming.get()) return

        val timestamp = getAudioTimestamp(presentationTimeUs)

        val baos = ByteArrayOutputStream(rawAac.size + 2)
        baos.write(0xAF) // AAC, 44kHz, 16-bit, stereo
        baos.write(0x01) // AAC raw frame
        baos.write(rawAac)

        val packet = RtmpPacket(
            type = MSG_AUDIO,
            csid = CSID_AUDIO,
            timestamp = timestamp,
            data = baos.toByteArray()
        )

        // Audio has high priority: ensure continuous enqueueing without dropping audio frames
        while (!audioQueue.offer(packet) && isStreaming.get()) {
            audioQueue.poll()
        }
    }

    private fun startSenderThread() {
        sendThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            while (isStreaming.get()) {
                if (isReconnecting.get()) {
                    // Pause packet sending while reconnecting to socket
                    try {
                        Thread.sleep(100)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }

                try {
                    // 1. Always prioritize audio packets immediately to completely eliminate transmission jitter
                    val audioPacket = audioQueue.poll()
                    if (audioPacket != null) {
                        sendChunk(audioPacket.csid, audioPacket.type, audioPacket.timestamp, streamId, audioPacket.data)
                        continue
                    }

                    // 2. Transmit next video packet if no audio is waiting
                    val videoPacket = videoQueue.poll(10, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (videoPacket != null) {
                        sendChunk(videoPacket.csid, videoPacket.type, videoPacket.timestamp, streamId, videoPacket.data)
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Sender thread write failure: ${e.message}")
                    if (isStreaming.get()) {
                        triggerReconnect("Write error: ${e.message}")
                    }
                }
            }
        }, "RtmpSenderThread").apply { start() }
    }

    /**
     * Reader thread continuously parses incoming RTMP chunks from YouTube.
     * Responds to Ping Requests (event 6 -> event 7), keeping stream active on YouTube's ingestion servers.
     */
    private fun startReaderThread() {
        readerThread?.interrupt()
        readerThread = Thread({
            var serverChunkSize = 128
            val channels = HashMap<Int, IncompleteMessage>()

            while (isStreaming.get() && !isReconnecting.get()) {
                val input = inputStream ?: break
                try {
                    val b0 = input.read()
                    if (b0 == -1) {
                        if (isStreaming.get() && !isReconnecting.get()) {
                            Log.w(TAG, "Server closed RTMP socket")
                            triggerReconnect("Server closed connection")
                        }
                        break
                    }

                    val fmt = (b0 shr 6) and 0x03
                    var csid = b0 and 0x3F
                    if (csid == 0) {
                        csid = 64 + (input.read() and 0xFF)
                    } else if (csid == 1) {
                        val c1 = input.read() and 0xFF
                        val c2 = input.read() and 0xFF
                        csid = 64 + c1 + (c2 shl 8)
                    }

                    val msg = channels.getOrPut(csid) { IncompleteMessage(csid = csid) }

                    if (fmt == 0) {
                        readInt24(input) // timestamp
                        val length = readInt24(input)
                        val type = input.read() and 0xFF
                        readInt32LE(input) // streamId
                        msg.fmt = fmt
                        msg.type = type
                        msg.length = length
                        msg.bytesRead = 0
                        msg.buffer = ByteArray(length)
                    } else if (fmt == 1) {
                        readInt24(input) // timestamp delta
                        val length = readInt24(input)
                        val type = input.read() and 0xFF
                        msg.fmt = fmt
                        msg.type = type
                        msg.length = length
                        msg.bytesRead = 0
                        msg.buffer = ByteArray(length)
                    } else if (fmt == 2) {
                        readInt24(input) // timestamp delta
                        msg.bytesRead = 0
                        msg.buffer = ByteArray(msg.length)
                    } else {
                        // fmt == 3: continuation chunk
                    }

                    val toRead = Math.min(serverChunkSize, msg.length - msg.bytesRead)
                    if (toRead > 0) {
                        var readSoFar = 0
                        while (readSoFar < toRead) {
                            val r = input.read(msg.buffer, msg.bytesRead + readSoFar, toRead - readSoFar)
                            if (r == -1) throw IllegalStateException("EOF reading RTMP payload")
                            readSoFar += r
                        }
                        msg.bytesRead += toRead
                    }

                    if (msg.bytesRead >= msg.length) {
                        handleServerMessage(msg.type, msg.buffer)
                        if (msg.type == MSG_SET_CHUNK_SIZE && msg.buffer.size >= 4) {
                            serverChunkSize = ((msg.buffer[0].toInt() and 0xFF) shl 24) or
                                             ((msg.buffer[1].toInt() and 0xFF) shl 16) or
                                             ((msg.buffer[2].toInt() and 0xFF) shl 8) or
                                             (msg.buffer[3].toInt() and 0xFF)
                            Log.d(TAG, "Server chunk size updated to $serverChunkSize")
                        }
                        msg.bytesRead = 0
                    }
                } catch (e: Exception) {
                    if (isStreaming.get() && !isReconnecting.get()) {
                        Log.d(TAG, "Reader thread interrupted/error: ${e.message}")
                        triggerReconnect("Read error: ${e.message}")
                    }
                    break
                }
            }
        }, "RtmpReaderThread").apply { start() }
    }

    private fun handleServerMessage(type: Int, payload: ByteArray) {
        if (type == MSG_USER_CONTROL && payload.size >= 6) {
            val eventType = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
            if (eventType == 6) { // Ping Request
                val responsePayload = ByteArray(6)
                responsePayload[0] = 0x00
                responsePayload[1] = 0x07 // Event 7: Ping Response
                System.arraycopy(payload, 2, responsePayload, 2, 4)
                sendChunk(CSID_CONTROL, MSG_USER_CONTROL, 0, 0, responsePayload)
                Log.d(TAG, "Replied to YouTube RTMP Ping Request with Ping Response")
            }
        }
    }

    private fun readInt24(input: InputStream): Int {
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if (b1 == -1 || b2 == -1 || b3 == -1) throw IllegalStateException("EOF reading int24")
        return ((b1 and 0xFF) shl 16) or ((b2 and 0xFF) shl 8) or (b3 and 0xFF)
    }

    private fun readInt32LE(input: InputStream): Int {
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        val b4 = input.read()
        if (b1 == -1 || b2 == -1 || b3 == -1 || b4 == -1) throw IllegalStateException("EOF reading int32LE")
        return (b1 and 0xFF) or ((b2 and 0xFF) shl 8) or ((b3 and 0xFF) shl 16) or ((b4 and 0xFF) shl 24)
    }

    @Synchronized
    private fun sendChunk(csid: Int, type: Int, timestamp: Int, streamId: Int, payload: ByteArray) {
        val out = outputStream ?: return
        val length = payload.size

        val hasExtendedTs = timestamp >= 0xFFFFFF
        val ts = if (hasExtendedTs) 0xFFFFFF else timestamp
        val header = ByteArray(if (hasExtendedTs) 16 else 12)
        var pos = 0

        header[pos++] = (csid and 0x3F).toByte()
        header[pos++] = ((ts shr 16) and 0xFF).toByte()
        header[pos++] = ((ts shr 8) and 0xFF).toByte()
        header[pos++] = (ts and 0xFF).toByte()

        header[pos++] = ((length shr 16) and 0xFF).toByte()
        header[pos++] = ((length shr 8) and 0xFF).toByte()
        header[pos++] = (length and 0xFF).toByte()

        header[pos++] = (type and 0xFF).toByte()

        header[pos++] = (streamId and 0xFF).toByte()
        header[pos++] = ((streamId shr 8) and 0xFF).toByte()
        header[pos++] = ((streamId shr 16) and 0xFF).toByte()
        header[pos++] = ((streamId shr 24) and 0xFF).toByte()

        if (hasExtendedTs) {
            header[pos++] = ((timestamp shr 24) and 0xFF).toByte()
            header[pos++] = ((timestamp shr 16) and 0xFF).toByte()
            header[pos++] = ((timestamp shr 8) and 0xFF).toByte()
            header[pos++] = (timestamp and 0xFF).toByte()
        }

        out.write(header, 0, pos)

        var offset = 0
        while (offset < length) {
            val bytesToWrite = Math.min(DEFAULT_CHUNK_SIZE, length - offset)
            out.write(payload, offset, bytesToWrite)
            offset += bytesToWrite

            if (offset < length) {
                out.write((0xC0 or (csid and 0x3F)))
                if (hasExtendedTs) {
                    out.write((timestamp shr 24) and 0xFF)
                    out.write((timestamp shr 16) and 0xFF)
                    out.write((timestamp shr 8) and 0xFF)
                    out.write(timestamp and 0xFF)
                }
            }
        }
        out.flush()

        val bytes = pos + length
        totalBytesSent.addAndGet(bytes.toLong())
        bytesSinceLastCalc += bytes

        val now = System.currentTimeMillis()
        if (now - lastBitrateCalcTime >= 1000) {
            val kbps = ((bytesSinceLastCalc * 8) / (now - lastBitrateCalcTime)).toInt()
            onBitrateUpdated(kbps)
            bytesSinceLastCalc = 0
            lastBitrateCalcTime = now
        }
    }

    private fun readResponsesUntilConnectResult() {
        val input = inputStream ?: return
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val buffer = ByteArray(Math.min(input.available(), 4096))
                input.read(buffer)
                break
            }
            Thread.sleep(50)
        }
    }

    private fun readResponsesUntilCreateStreamResult() {
        val input = inputStream ?: return
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val buffer = ByteArray(Math.min(input.available(), 4096))
                input.read(buffer)
                break
            }
            Thread.sleep(50)
        }
    }

    fun getTotalBytesSent(): Long = totalBytesSent.get()

    fun close() {
        isStreaming.set(false)
        isConnected.set(false)
        isReconnecting.set(false)

        try {
            sendThread?.interrupt()
            sendThread = null
        } catch (_: Exception) {}

        try {
            readerThread?.interrupt()
            readerThread = null
        } catch (_: Exception) {}

        try {
            reconnectThread?.interrupt()
            reconnectThread = null
        } catch (_: Exception) {}

        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}

        socket = null
        inputStream = null
        outputStream = null
        audioQueue.clear()
        videoQueue.clear()
        streamStartPtsUs = -1L
        lastAudioTimestamp = -1
        lastVideoTimestamp = -1
        onStatusChanged("STOPPED")
    }
}
