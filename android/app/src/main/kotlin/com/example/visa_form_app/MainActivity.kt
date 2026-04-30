package com.example.rendezvous_hb

import android.content.Intent
import android.provider.Settings
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.Locale

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.visa_form_app/accessibility"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "openAccessibilitySettings" -> {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    result.success(null)
                }
                "isAccessibilityServiceEnabled" -> {
                    result.success(isAccessibilityServiceEnabled())
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val lowerCaseServices = enabledServices.lowercase(Locale.getDefault())
        return lowerCaseServices.contains("myaccessibilityservice") ||
            lowerCaseServices.contains("com.example.assistant_hb.myaccessibilityservice") ||
            lowerCaseServices.contains("com.example.visa_form_app.myaccessibilityservice") ||
            lowerCaseServices.contains("com.example.visa_form_app/com.example.visa_form_app.myaccessibilityservice") ||
            lowerCaseServices.contains("com.example.rendezvous_hb.myaccessibilityservice") ||
            lowerCaseServices.contains("com.example.rendezvous_hb/com.example.rendezvous_hb.myaccessibilityservice")
    }
}
