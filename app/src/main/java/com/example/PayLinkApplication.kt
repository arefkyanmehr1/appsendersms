package com.example

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.background.HeartbeatWorker
import com.example.background.InvoiceSyncWorker
import com.example.background.NotificationHelper
import com.example.core.security.SecureApiKeyStorage
import com.example.core.util.AppLogger
import com.example.data.local.PayLinkDatabase
import com.example.data.repository.PayLinkRepository
import com.example.sms.SmsProcessingCoordinator
import java.util.concurrent.TimeUnit

class PayLinkApplication : Application() {

    lateinit var secureStorage: SecureApiKeyStorage
        private set

    lateinit var database: PayLinkDatabase
        private set

    lateinit var repository: PayLinkRepository
        private set

    lateinit var smsProcessingCoordinator: SmsProcessingCoordinator
        private set

    override fun onCreate() {
        super.onCreate()

        AppLogger.i("PayLinkApplication initializing...")

        secureStorage = SecureApiKeyStorage(this)
        database = PayLinkDatabase.getInstance(this)
        repository = PayLinkRepository(this, secureStorage, database)
        smsProcessingCoordinator = SmsProcessingCoordinator(this, repository)

        NotificationHelper.createNotificationChannel(this)

        if (secureStorage.hasApiKey()) {
            scheduleBackgroundWorkers()
        }
    }

    fun scheduleBackgroundWorkers() {
        val workManager = WorkManager.getInstance(this)

        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Heartbeat Worker: Every 15 minutes
        val heartbeatRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "PayLinkHeartbeatWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            heartbeatRequest
        )

        // Invoice Sync Worker: Every 15 minutes
        val invoiceSyncRequest = PeriodicWorkRequestBuilder<InvoiceSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "PayLinkInvoiceSyncWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            invoiceSyncRequest
        )

        // Start Foreground Service to guarantee live background monitoring and instant notifications when app is closed
        try {
            com.example.background.InvoiceMonitorService.start(this)
        } catch (e: Exception) {
            AppLogger.e("Failed to start InvoiceMonitorService", e)
        }
    }

    fun cancelBackgroundWorkers() {
        val workManager = WorkManager.getInstance(this)
        workManager.cancelUniqueWork("PayLinkHeartbeatWork")
        workManager.cancelUniqueWork("PayLinkInvoiceSyncWork")
        try {
            com.example.background.InvoiceMonitorService.stop(this)
        } catch (e: Exception) {
            AppLogger.e("Failed to stop InvoiceMonitorService", e)
        }
    }
}
