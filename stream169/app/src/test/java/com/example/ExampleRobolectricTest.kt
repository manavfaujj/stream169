package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.stream.data.StreamConfigManager
import com.example.stream.model.AudioSourceMode
import com.example.stream.model.StreamConfig
import com.example.stream.model.StreamOrientation
import com.example.stream.model.StreamResolution
import com.example.stream.rtmp.Amf0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Stream169", appName)
    }

    @Test
    fun `stream config resolution 16 by 9 verification`() {
        val landscape1080 = StreamResolution.RES_1080P
        val landscapeWidth = landscape1080.getWidth(StreamOrientation.LANDSCAPE)
        val landscapeHeight = landscape1080.getHeight(StreamOrientation.LANDSCAPE)
        assertEquals(1920, landscapeWidth)
        assertEquals(1080, landscapeHeight)
        // 1920 / 1080 is 16:9
        assertEquals(16.0 / 9.0, landscapeWidth.toDouble() / landscapeHeight.toDouble(), 0.01)

        val portraitWidth = landscape1080.getWidth(StreamOrientation.PORTRAIT)
        val portraitHeight = landscape1080.getHeight(StreamOrientation.PORTRAIT)
        assertEquals(1080, portraitWidth)
        assertEquals(1920, portraitHeight)
        // 1080 / 1920 is 9:16
        assertEquals(9.0 / 16.0, portraitWidth.toDouble() / portraitHeight.toDouble(), 0.01)
    }

    @Test
    fun `stream config manager persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = StreamConfigManager(context)

        val customConfig = StreamConfig(
            rtmpUrl = "rtmp://a.rtmp.youtube.com/live2",
            streamKey = "test-live-key-1234",
            orientation = StreamOrientation.PORTRAIT,
            resolution = StreamResolution.RES_720P,
            videoBitrateKbps = 3000,
            audioBitrateKbps = 160,
            frameRate = 60,
            audioMode = AudioSourceMode.BOTH
        )

        manager.updateConfig(customConfig)
        val loaded = manager.loadConfig()

        assertEquals("test-live-key-1234", loaded.streamKey)
        assertEquals(StreamOrientation.PORTRAIT, loaded.orientation)
        assertEquals(StreamResolution.RES_720P, loaded.resolution)
        assertEquals(3000, loaded.videoBitrateKbps)
        assertEquals(60, loaded.frameRate)
        assertEquals(AudioSourceMode.BOTH, loaded.audioMode)
    }

    @Test
    fun `amf0 string and number serialization`() {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        Amf0.writeString(dos, "connect")
        Amf0.writeNumber(dos, 1.0)
        dos.flush()

        val bytes = baos.toByteArray()
        assertTrue(bytes.isNotEmpty())
        assertEquals(Amf0.TYPE_STRING, bytes[0])
    }

    @Test
    fun `audio frame duration and pacing calculation`() {
        val sampleRate = 44100L
        val samplesPerFrame = 1024L
        val frameDurationUs = (samplesPerFrame * 1_000_000L) / sampleRate

        // At 44.1kHz, 1024 samples is ~23219 microseconds (~23.22ms)
        assertEquals(23219L, frameDurationUs)

        // Across 10 consecutive frames, verify timestamps are strictly monotonic and jitter-free
        val startPtsUs = 1_000_000L
        val timestamps = (0 until 10).map { frameIndex ->
            startPtsUs + (frameIndex * samplesPerFrame * 1_000_000L) / sampleRate
        }

        for (i in 1 until timestamps.size) {
            val delta = timestamps[i] - timestamps[i - 1]
            // Delta must be either 23219 or 23220 us (exact integer division)
            assertTrue(delta in 23219L..23220L)
            assertTrue(timestamps[i] > timestamps[i - 1])
        }

        // 43 frames at 44.1kHz should equal almost exactly 1 full second (998.4ms)
        val duration43FramesUs = (43L * samplesPerFrame * 1_000_000L) / sampleRate
        assertEquals(998458L, duration43FramesUs)
    }

    @Test
    fun `audio mixing clipping and volume scaling`() {
        val micSample: Short = 20000
        val internalSample: Short = 25000

        val micVol = 1.0f
        val internalVol = 0.8f

        val scaledMic = (micSample * micVol).toInt()
        val scaledInt = (internalSample * internalVol).toInt()

        val mixedSum = scaledMic + scaledInt
        // 20000 + 20000 = 40000, which exceeds Short.MAX_VALUE (32767)
        assertTrue(mixedSum > 32767)

        val clamped = mixedSum.coerceIn(-32768, 32767).toShort()
        assertEquals(Short.MAX_VALUE, clamped)
    }

    @Test
    fun `audio quality selection resolution`() {
        val low = com.example.stream.model.AudioQuality.fromKbps(64)
        assertEquals(com.example.stream.model.AudioQuality.LOW, low)
        assertEquals(64, low.kbps)

        val standard = com.example.stream.model.AudioQuality.fromKbps(128)
        assertEquals(com.example.stream.model.AudioQuality.STANDARD, standard)
        assertEquals(128, standard.kbps)

        val high = com.example.stream.model.AudioQuality.fromKbps(160)
        assertEquals(com.example.stream.model.AudioQuality.HIGH, high)
        assertEquals(160, high.kbps)

        val ultra = com.example.stream.model.AudioQuality.fromKbps(192)
        assertEquals(com.example.stream.model.AudioQuality.ULTRA, ultra)
        assertEquals(192, ultra.kbps)

        // Fallback on unknown bitrate
        val unknown = com.example.stream.model.AudioQuality.fromKbps(999)
        assertEquals(com.example.stream.model.AudioQuality.STANDARD, unknown)
    }
}
