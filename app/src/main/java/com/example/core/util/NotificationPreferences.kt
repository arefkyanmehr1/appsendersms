package com.example.core.util

import android.content.Context
import android.content.SharedPreferences

class NotificationPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isPendingNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_PENDING_NOTIF_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PENDING_NOTIF_ENABLED, value).apply()

    var isVerifiedNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VERIFIED_NOTIF_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VERIFIED_NOTIF_ENABLED, value).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()

    var invoiceTimeoutMinutes: Int
        get() = prefs.getInt(KEY_INVOICE_TIMEOUT_MINUTES, 15)
        set(value) = prefs.edit().putInt(KEY_INVOICE_TIMEOUT_MINUTES, value).apply()

    companion object {
        private const val PREFS_NAME = "paylink_notification_prefs"
        private const val KEY_PENDING_NOTIF_ENABLED = "pref_pending_notif_enabled"
        private const val KEY_VERIFIED_NOTIF_ENABLED = "pref_verified_notif_enabled"
        private const val KEY_SOUND_ENABLED = "pref_sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "pref_vibration_enabled"
        private const val KEY_INVOICE_TIMEOUT_MINUTES = "pref_invoice_timeout_minutes"
    }
}
