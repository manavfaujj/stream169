package com.example.stream.video

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

class EglHelper(
    private val encoderInputSurface: Surface,
    val targetWidth: Int,
    val targetHeight: Int,
    val screenWidth: Int,
    val screenHeight: Int
) {
    companion object {
        private const val TAG = "EglHelper"
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private const val VERTEX_SHADER = """
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """
    }

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var program = 0
    private var textureId = 0
    var surfaceTexture: SurfaceTexture? = null
        private set
    var displaySurface: Surface? = null
        private set

    private var aPositionLoc = 0
    private var aTextureCoordLoc = 0
    private var uTexMatrixLoc = 0
    private var sTextureLoc = 0

    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer
    private val texMatrix = FloatArray(16)
    private val cropMatrix = FloatArray(16)
    private val finalMatrix = FloatArray(16)

    @Volatile
    var hasDrawnAtLeastOnce = false
        private set

    // Full screen quad coordinates (Triangle Strip)
    private val quadVertices = floatArrayOf(
        -1.0f, -1.0f,
         1.0f, -1.0f,
        -1.0f,  1.0f,
         1.0f,  1.0f
    )

    init {
        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(quadVertices)
                position(0)
            }

        // Standard quad texture coordinates (0,0 to 1,1)
        val defaultTexCoords = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
        texCoordBuffer = ByteBuffer.allocateDirect(defaultTexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(defaultTexCoords)
                position(0)
            }

        Matrix.setIdentityM(cropMatrix, 0)
        Matrix.setIdentityM(texMatrix, 0)

        initEgl()
        initGl()
    }

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
        val config = configs[0] ?: throw RuntimeException("Unable to find suitable EGLConfig")

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Failed to create EGL context")
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, encoderInputSurface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create EGL window surface")
        }

        makeCurrent()
    }

    fun makeCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    private fun initGl() {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoordLoc = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
        sTextureLoc = GLES20.glGetUniformLocation(program, "sTexture")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(screenWidth, screenHeight)
        }
        surfaceTexture = st
        displaySurface = Surface(st)

        GLES20.glViewport(0, 0, targetWidth, targetHeight)
    }

    /**
     * Calculates crop transformation matrix to achieve exact 16:9 or 9:16 aspect ratio.
     * In horizontal: crops left and right sides without stretching.
     * In vertical: crops top and bottom without stretching.
     */
    fun updateCropCoordinates(
        screenWidth: Int,
        screenHeight: Int,
        isLandscape: Boolean
    ) {
        Matrix.setIdentityM(cropMatrix, 0)

        val screenAspect = screenWidth.toFloat() / screenHeight.toFloat()
        if (isLandscape) {
            val targetAspect = 16.0f / 9.0f
            if (screenAspect > targetAspect) {
                // Screen is wider than 16:9 -> crop left and right sides
                val scaleX = targetAspect / screenAspect
                val transX = (1.0f - scaleX) / 2.0f
                Matrix.translateM(cropMatrix, 0, transX, 0f, 0f)
                Matrix.scaleM(cropMatrix, 0, scaleX, 1.0f, 1.0f)
            } else if (screenAspect < targetAspect) {
                // Screen is narrower than 16:9 -> fit height and center
                val scaleY = screenAspect / targetAspect
                val transY = (1.0f - scaleY) / 2.0f
                Matrix.translateM(cropMatrix, 0, 0f, transY, 0f)
                Matrix.scaleM(cropMatrix, 0, 1.0f, scaleY, 1.0f)
            }
        } else {
            val targetAspect = 9.0f / 16.0f
            if (screenAspect < targetAspect) {
                // Screen is taller than 9:16 -> crop top and bottom
                val scaleY = screenAspect / targetAspect
                val transY = (1.0f - scaleY) / 2.0f
                Matrix.translateM(cropMatrix, 0, 0f, transY, 0f)
                Matrix.scaleM(cropMatrix, 0, 1.0f, scaleY, 1.0f)
            } else if (screenAspect > targetAspect) {
                // Screen is wider than 9:16 -> crop left and right
                val scaleX = targetAspect / screenAspect
                val transX = (1.0f - scaleX) / 2.0f
                Matrix.translateM(cropMatrix, 0, transX, 0f, 0f)
                Matrix.scaleM(cropMatrix, 0, scaleX, 1.0f, 1.0f)
            }
        }
    }

    fun drawFrame(timestampNs: Long, updateTex: Boolean) {
        makeCurrent()

        if (updateTex) {
            try {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(texMatrix)
                hasDrawnAtLeastOnce = true
            } catch (e: Exception) {
                Log.w(TAG, "updateTexImage error: ${e.message}")
            }
        }

        if (!hasDrawnAtLeastOnce) {
            return
        }

        GLES20.glViewport(0, 0, targetWidth, targetHeight)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        // Combine surfaceTexture hardware transform matrix with our crop matrix
        Matrix.multiplyMM(finalMatrix, 0, texMatrix, 0, cropMatrix, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, finalMatrix, 0)

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(sTextureLoc, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, timestampNs)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun release() {
        try {
            surfaceTexture?.release()
            displaySurface?.release()
            if (program != 0) {
                GLES20.glDeleteProgram(program)
            }
            if (textureId != 0) {
                val textures = intArrayOf(textureId)
                GLES20.glDeleteTextures(1, textures, 0)
            }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing EGL resources", e)
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vertexShader)
        GLES20.glAttachShader(p, fragmentShader)
        GLES20.glLinkProgram(p)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val info = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw RuntimeException("Could not link program: $info")
        }
        return p
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        val shader = GLES20.glCreateShader(shaderType)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Could not compile shader $shaderType: $info")
        }
        return shader
    }
}
