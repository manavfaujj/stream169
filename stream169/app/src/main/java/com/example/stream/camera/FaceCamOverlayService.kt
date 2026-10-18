package com.example.stream.camera

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.stream.StreamController
import kotlin.math.abs

class FaceCamOverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "com.example.stream.camera.ACTION_SHOW"
        const val ACTION_HIDE = "com.example.stream.camera.ACTION_HIDE"

        private var isRunning = false
        fun isServiceRunning(): Boolean = isRunning

        fun show(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, FaceCamOverlayService::class.java).apply {
                    action = ACTION_SHOW
                }
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            val intent = Intent(context, FaceCamOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingRootView: FrameLayout? = null
    private var previewView: PreviewView? = null
    private var controlsContainer: LinearLayout? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    private val lifecycleOwner = CustomServiceLifecycleOwner()
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentCamera: Camera? = null

    private var isFrontCamera = true
    private var currentZoom = 1.0f
    private var currentSizeDp = 140 // 100=Small, 140=Medium, 190=Large

    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable {
        hideControls()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.start()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                removeFloatingWindow()
                stopSelf()
            }
            ACTION_SHOW, null -> {
                if (!Settings.canDrawOverlays(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (floatingRootView == null) {
                    initFloatingWindow()
                    startCamera()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val sizePx = dpToPx(currentSizeDp)
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(24)
            y = dpToPx(96)
        }
        windowLayoutParams = params

        // Root container
        val root = FrameLayout(this).apply {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val radius = view.width * 0.12f
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
        }
        floatingRootView = root

        // 1. Camera Preview View (Pure camera with NO marks/watermarks)
        val pView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        previewView = pView
        root.addView(pView)

        // 2. Controls Container (Auto-hiding overlay buttons)
        val controls = createControlsView()
        controlsContainer = controls
        root.addView(controls)

        // Drag and tap handling
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    showControlsWithTimer()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(root, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        toggleControls()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(root, params)
            showControlsWithTimer()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createControlsView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#99000000")) // Semi-transparent dark
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // Top row: Flip camera & Close button
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 4)

                addView(createButton("Flip") {
                    isFrontCamera = !isFrontCamera
                    startCamera()
                    resetTimer()
                })

                val space = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(8), 1)
                }
                addView(space)

                addView(createButton("X") {
                    removeFloatingWindow()
                    stopSelf()
                })
            }
            addView(topRow)

            // Middle row: Size buttons (S, M, L)
            val sizeRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 2, 0, 2)

                addView(createButton("S") {
                    updateSize(100)
                    resetTimer()
                })
                addView(createButton("M") {
                    updateSize(140)
                    resetTimer()
                })
                addView(createButton("L") {
                    updateSize(190)
                    resetTimer()
                })
            }
            addView(sizeRow)

            // Bottom row: Zoom buttons (1x, 1.5x, 2x)
            val zoomRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 2, 0, 4)

                addView(createButton("1x") {
                    applyZoom(1.0f)
                    resetTimer()
                })
                addView(createButton("1.5x") {
                    applyZoom(1.5f)
                    resetTimer()
                })
                addView(createButton("2x") {
                    applyZoom(2.0f)
                    resetTimer()
                })
            }
            addView(zoomRow)
        }
    }

    private fun createButton(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(dpToPx(6), dpToPx(3), dpToPx(6), dpToPx(3))

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(4).toFloat()
                setColor(Color.parseColor("#33FFFFFF"))
                setStroke(dpToPx(1), Color.WHITE)
            }
            background = bg

            setOnClickListener { onClick() }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            layoutParams = lp
        }
    }

    private fun updateSize(sizeDp: Int) {
        currentSizeDp = sizeDp
        val px = dpToPx(sizeDp)
        windowLayoutParams?.let { params ->
            params.width = px
            params.height = px
            windowManager?.updateViewLayout(floatingRootView, params)
        }
    }

    private fun applyZoom(zoom: Float) {
        currentZoom = zoom
        currentCamera?.cameraControl?.setZoomRatio(zoom)
    }

    private fun showControlsWithTimer() {
        controlsContainer?.visibility = View.VISIBLE
        controlsContainer?.animate()?.alpha(1f)?.setDuration(150)?.start()
        resetTimer()
    }

    private fun hideControls() {
        controlsContainer?.animate()
            ?.alpha(0f)
            ?.setDuration(250)
            ?.withEndAction {
                controlsContainer?.visibility = View.GONE
            }
            ?.start()
    }

    private fun toggleControls() {
        if (controlsContainer?.visibility == View.VISIBLE) {
            hideControls()
        } else {
            showControlsWithTimer()
        }
    }

    private fun resetTimer() {
        mainHandler.removeCallbacks(autoHideRunnable)
        mainHandler.postDelayed(autoHideRunnable, 3500)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val preview = Preview.Builder().build().also {
                    previewView?.surfaceProvider?.let { sp ->
                        it.setSurfaceProvider(sp)
                    }
                }

                currentCamera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )
                currentCamera?.cameraControl?.setZoomRatio(currentZoom)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun removeFloatingWindow() {
        mainHandler.removeCallbacks(autoHideRunnable)
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}

        floatingRootView?.let { root ->
            try {
                windowManager?.removeView(root)
            } catch (_: Exception) {}
            floatingRootView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        removeFloatingWindow()
        lifecycleOwner.stop()
    }
}

/**
 * Lightweight custom LifecycleOwner for hosting CameraX in a Service.
 */
class CustomServiceLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    fun start() {
        registry.currentState = Lifecycle.State.STARTED
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
