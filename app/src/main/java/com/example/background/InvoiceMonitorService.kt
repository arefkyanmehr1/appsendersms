package com.example.background

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.PayLinkApplication
import com.example.R
import com.example.core.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Highly resilient Foreground Service that guarantees continuous monitoring
 * of pending invoices and delivers instant heads-up notifications even when the app is closed.
 */
class InvoiceMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppLogger.i("InvoiceMonitorService created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i("InvoiceMonitorService started.")

        val notification = createForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startPolling()

        return START_STICKY
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = serviceScope.launch {
            val app = applicationContext as? PayLinkApplication ?: return@launch
            while (isActive) {
                try {
                    if (app.secureStorage.hasApiKey()) {
                        // Polling pending invoices automatically syncs and triggers notifications for new invoices
                        app.repository.getPendingInvoices(limit = 50)
                    }
                } catch (e: Exception) {
                    AppLogger.w("Background poll failed: ${e.message}")
                }
                // One background poller; SMS verification itself is event-driven.
                delay(15_000)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        AppLogger.i("App swiped away from recent tasks, scheduling immediate restart of InvoiceMonitorService.")
        try {
            val restartServiceIntent = Intent(applicationContext, InvoiceMonitorService::class.java).also {
                it.setPackage(packageName)
            }
            val restartServicePendingIntent = PendingIntent.getService(
                this, 1, restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent
            )
        } catch (e: Exception) {
            AppLogger.e("Failed to schedule service restart onTaskRemoved", e)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SERVICE_ID)
            .setSmallIcon(R.drawable.ic_stat_paylink)
            .setContentTitle("سرویس نظارت و تأیید هوشمند فعال است")
            .setContentText("بررسی خودکار پیامک‌ها و فاکتورهای جدید حتی در پس‌زمینه")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.i("InvoiceMonitorService destroyed.")
        pollJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 9001

        fun start(context: Context) {
            val intent = Intent(context, InvoiceMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, InvoiceMonitorService::class.java)
            context.stopService(intent)
        }
    }
}
