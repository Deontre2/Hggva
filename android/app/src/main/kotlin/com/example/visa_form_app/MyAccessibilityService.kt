package com.example.visa_form_app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.rendezvous_hb.MainActivity


class MyAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.className == "com.google.android.apps.messaging.ui.conversation.ConversationActivity") {
            // Wait for a few seconds to make sure the OTP is received
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }, 5000)
        }
    }

    override fun onInterrupt() {
        // This method is called when the service is interrupted
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Toast.makeText(this, "Accessibility Service Connected", Toast.LENGTH_SHORT).show()
    }
}
