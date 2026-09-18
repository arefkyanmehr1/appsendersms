package com.example.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.PayLinkApplication
import com.example.core.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PayLinkSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val app = context.applicationContext as? PayLinkApplication ?: return

        // Only process if user is connected
        if (!app.secureStorage.hasApiKey()) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        val sender = messages.firstOrNull()?.originatingAddress
        val bodyBuilder = StringBuilder()
        for (msg in messages) {
            msg.messageBody?.let { bodyBuilder.append(it) }
        }
        val fullBody = bodyBuilder.toString()

        if (fullBody.isBlank()) {
            return
        }

        // Use goAsync for reliable background processing from BroadcastReceiver
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.smsProcessingCoordinator.processIncomingSms(sender, fullBody)
            } catch (e: Exception) {
                AppLogger.e("Error processing incoming SMS in receiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
