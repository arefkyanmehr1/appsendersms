package com.example.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.util.CurrencyUtils
import com.example.core.util.NotificationPreferences

object NotificationHelper {

    // Distinct Channels for verified payments and incoming pending invoices
    const val CHANNEL_VERIFIED_ID = "paylink_payment_verified"
    private const val CHANNEL_VERIFIED_NAME = "پرداخت‌های تأیید شده"
    private const val CHANNEL_VERIFIED_DESC = "اعلان‌های فوری مربوط به تأیید پرداخت خودکار فاکتورها"

    const val CHANNEL_PENDING_ID = "paylink_pending_invoices"
    private const val CHANNEL_PENDING_NAME = "فاکتورهای در انتظار جدید"
    private const val CHANNEL_PENDING_DESC = "اعلان‌های مربوط به ثبت فاکتور جدید در سایت"

    const val CHANNEL_SERVICE_ID = "paylink_service_channel"
    private const val CHANNEL_SERVICE_NAME = "وضعیت سرویس پس‌زمینه"
    private const val CHANNEL_SERVICE_DESC = "فعالیت مداوم برنامه در پس‌زمینه جهت دریافت و بررسی فوری سفارش‌ها"

    // Legacy channel cleanup / alias
    private const val LEGACY_CHANNEL_ID = "paylink_payment_notifications"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Remove legacy channel if present to avoid confusion in system settings
            try {
                notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            } catch (_: Exception) {
            }

            // 1. Channel for Verified Payments (Green light, vibration, high priority)
            val verifiedChannel = NotificationChannel(
                CHANNEL_VERIFIED_ID,
                CHANNEL_VERIFIED_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_VERIFIED_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                enableLights(true)
                lightColor = Color.GREEN
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(verifiedChannel)

            // 2. Channel for Pending Invoices (Amber/Orange light, prompt heads-up)
            val pendingChannel = NotificationChannel(
                CHANNEL_PENDING_ID,
                CHANNEL_PENDING_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_PENDING_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                enableLights(true)
                lightColor = Color.parseColor("#F59E0B")
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(pendingChannel)

            // 3. Channel for Background Monitoring Service (Low importance / silent ongoing notification)
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                CHANNEL_SERVICE_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_SERVICE_DESC
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Show rich, neat notification when a payment is successfully verified.
     */
    fun showPaymentVerifiedNotification(
        context: Context,
        orderId: String,
        amount: Long,
        bankName: String? = null,
        trackingCode: String? = null
    ) {
        val prefs = NotificationPreferences(context)
        if (!prefs.isVerifiedNotificationEnabled) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "transaction_history")
            putExtra("highlight_order_id", orderId)
        }
        val requestCode = ("verified_$orderId").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedRials = CurrencyUtils.formatRials(amount)
        val formattedTomans = CurrencyUtils.formatTomans(amount)

        val shortTitle = "پرداخت تأیید شد ($formattedTomans)"
        val shortContent = "سفارش $orderId به مبلغ $formattedRials تأیید شد."

        val bigText = buildString {
            append("شماره سفارش: $orderId\n")
            append("مبلغ: $formattedRials (معادل $formattedTomans)")
            if (!bankName.isNullOrBlank()) {
                append("\nبانک عامل: $bankName")
            }
            if (!trackingCode.isNullOrBlank()) {
                append("\nکد رهگیری: $trackingCode")
            }
            append("\nوضعیت: با موفقیت در سایت تأیید و تسویه شد")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_VERIFIED_ID)
            .setSmallIcon(R.drawable.ic_stat_paylink)
            .setColor(0xFF10B981.toInt()) // Emerald Green
            .setContentTitle(shortTitle)
            .setContentText(shortContent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("✅ پرداخت سفارش $orderId تأیید شد")
                    .bigText(bigText)
                    .setSummaryText("تأیید آنی PayLink")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (prefs.isSoundEnabled) {
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(defaultSoundUri)
        } else {
            builder.setSound(null)
        }

        if (prefs.isVibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 250, 150, 250))
        } else {
            builder.setVibrate(longArrayOf(0))
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(requestCode, builder.build())
    }

    /**
     * Show rich, neat notification when a brand new pending invoice is detected.
     */
    fun showNewInvoicePendingNotification(
        context: Context,
        orderId: String,
        amount: Long,
        expiresInMinutes: Int? = null
    ) {
        val prefs = NotificationPreferences(context)
        if (!prefs.isPendingNotificationEnabled) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "pending_invoices")
            putExtra("highlight_order_id", orderId)
        }
        val requestCode = ("pending_$orderId").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedRials = CurrencyUtils.formatRials(amount)
        val formattedTomans = CurrencyUtils.formatTomans(amount)

        val shortTitle = "فاکتور جدید در انتظار پرداخت ($formattedTomans)"
        val shortContent = "سفارش $orderId به مبلغ $formattedRials ثبت شد."

        val bigText = buildString {
            append("شماره سفارش: $orderId\n")
            append("مبلغ قابل پرداخت: $formattedRials\n")
            append("معادل: $formattedTomans")
            if (expiresInMinutes != null && expiresInMinutes > 0) {
                append("\nمهلت پرداخت: $expiresInMinutes دقیقه")
            }
            append("\nدر انتظار دریافت پیامک بانکی مشتری جهت تأیید آنی...")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_PENDING_ID)
            .setSmallIcon(R.drawable.ic_stat_paylink)
            .setColor(0xFFF59E0B.toInt()) // Amber/Orange
            .setContentTitle(shortTitle)
            .setContentText(shortContent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("⏳ فاکتور جدید در انتظار پرداخت (سفارش $orderId)")
                    .bigText(bigText)
                    .setSummaryText("فاکتور جدید در سایت")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (prefs.isSoundEnabled) {
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(defaultSoundUri)
        } else {
            builder.setSound(null)
        }

        if (prefs.isVibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 200, 100, 200))
        } else {
            builder.setVibrate(longArrayOf(0))
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(requestCode, builder.build())
    }
}
