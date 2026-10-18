package com.example.stream.facecam

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.stream.model.FaceCamShape

class FloatingFaceCamView(private val context: Context) : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var containerView: FrameLayout? = null
    private var previewView: PreviewView? = null
    private var controlsLayout: FrameLayout? = null

    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var currentSizePx = 320
    private var shape = FaceCamShape.CIRCLE

    private val autoHideHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable {
        controlsLayout?.visibility = View.GONE
    }

    private var params: WindowManager.LayoutParams? = null

    init {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(shape: FaceCamShape, sizeDp: Int) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w("FloatingFaceCamView", "Overlay permission not granted")
            return
        }
        if (containerView != null) return

        this.shape = shape
        val density = context.resources.displayMetrics.density
        currentSizePx = (sizeDp * density).toInt()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            currentSizePx,
            currentSizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 120
        }

        containerView = FrameLayout(context).apply {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    if (shape == FaceCamShape.CIRCLE) {
                        outline.setOval(0, 0, view.width, view.height)
                    } else {
                        outline.setRoundRect(0, 0, view.width, view.height, 24f * density)
                    }
                }
            }
        }

        previewView = PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        containerView?.addView(previewView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        // Overlay controls that auto-hide
        setupControls()

        // Drag listener
        setupDragListener()

        windowManager.addView(containerView, params)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        startCamera()
        scheduleHideControls()
    }

    private fun setupControls() {
        controlsLayout = FrameLayout(context).apply {
            visibility = View.VISIBLE
            setBackgroundColor(0x55000000)
        }

        val flipIcon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_rotate)
            setColorFilter(0xFFFFFFFF.toInt())
            setOnClickListener {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
                startCamera()
                scheduleHideControls()
            }
        }
        val iconParams = FrameLayout.LayoutParams(60, 60).apply {
            gravity = Gravity.CENTER
        }
        controlsLayout?.addView(flipIcon, iconParams)

        containerView?.addView(controlsLayout, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var hasMoved = false

        containerView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        hasMoved = true
                        params?.x = initialX + dx
                        params?.y = initialY + dy
                        windowManager.updateViewLayout(containerView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasMoved) {
                        // Toggle control buttons and auto-hide
                        if (controlsLayout?.visibility == View.VISIBLE) {
                            controlsLayout?.visibility = View.GONE
                        } else {
                            controlsLayout?.visibility = View.VISIBLE
                            scheduleHideControls()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun scheduleHideControls() {
        autoHideHandler.removeCallbacks(hideControlsRunnable)
        autoHideHandler.postDelayed(hideControlsRunnable, 3000)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView?.surfaceProvider)
                }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (e: Exception) {
                Log.e("FloatingFaceCamView", "Camera binding failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun dismiss() {
        autoHideHandler.removeCallbacks(hideControlsRunnable)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        if (containerView != null) {
            try {
                windowManager.removeView(containerView)
            } catch (_: Exception) {}
            containerView = null
            previewView = null
            controlsLayout = null
        }
    }
}
