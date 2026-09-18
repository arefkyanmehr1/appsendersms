package com.example.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Setup : Screen("setup", "راه‌اندازی")
    object Dashboard : Screen("dashboard", "پیشخوان")
    object PendingInvoices : Screen("pending_invoices", "فاکتورهای در انتظار")
    object TransactionHistory : Screen("transaction_history", "تاریخچه تراکنش‌ها")
    object PaymentActivity : Screen("payment_activity", "فعالیت پیامک‌ها")
    object Settings : Screen("settings", "تنظیمات")
}
