package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PayLinkApplication
import com.example.core.network.ErrorType
import com.example.core.network.NetworkResult
import com.example.core.network.toPersianMessage
import com.example.core.util.DeviceUtils
import com.example.data.api.WebAuthResult
import com.example.data.api.WebAuthService
import com.example.data.local.entity.ProcessedPaymentEntity
import com.example.data.model.AccountStatusData
import com.example.data.model.PendingInvoice
import com.example.data.model.TransactionItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AuthState {
    object NeedsSetup : AuthState()
    object Connected : AuthState()
    data class AccountDisabled(val message: String) : AuthState()
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val data: AccountStatusData? = null,
    val isServerReachable: Boolean = true,
    val isInternetAvailable: Boolean = true,
    val isAccountActive: Boolean = true,
    val batteryLevel: Int = 100,
    val deviceId: String = "",
    val errorMessage: String? = null
)

data class InvoicesUiState(
    val isLoading: Boolean = false,
    val invoices: List<PendingInvoice> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val rejectingOrderIds: Set<String> = emptySet(),
    val verifyingOrderIds: Set<String> = emptySet()
)

data class TransactionsUiState(
    val isLoading: Boolean = false,
    val transactions: List<TransactionItem> = emptyList(),
    val currentPage: Int = 1,
    val total: Int = 0,
    val errorMessage: String? = null
)

class PayLinkViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PayLinkApplication
    private val repository = app.repository
    private val secureStorage = app.secureStorage

    private val _authState = MutableStateFlow<AuthState>(
        if (secureStorage.hasApiKey()) AuthState.Connected else AuthState.NeedsSetup
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _dashboardState = MutableStateFlow(DashboardUiState())
    val dashboardState: StateFlow<DashboardUiState> = _dashboardState.asStateFlow()

    private val _invoicesState = MutableStateFlow(InvoicesUiState())
    val invoicesState: StateFlow<InvoicesUiState> = _invoicesState.asStateFlow()

    private val _transactionsState = MutableStateFlow(TransactionsUiState())
    val transactionsState: StateFlow<TransactionsUiState> = _transactionsState.asStateFlow()

    val activityLogs: StateFlow<List<ProcessedPaymentEntity>> =
        repository.processedPaymentsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _setupError = MutableStateFlow<String?>(null)
    val setupError: StateFlow<String?> = _setupError.asStateFlow()

    private val themePrefs = application.getSharedPreferences("paylink_theme_prefs", Application.MODE_PRIVATE)
    // Default to Light Mode (false) as requested by user
    private val _isDarkMode = MutableStateFlow(themePrefs.getBoolean("pref_dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val notificationPrefs = com.example.core.util.NotificationPreferences(application)
    private val _isPendingNotifEnabled = MutableStateFlow(notificationPrefs.isPendingNotificationEnabled)
    val isPendingNotifEnabled: StateFlow<Boolean> = _isPendingNotifEnabled.asStateFlow()

    private val _isVerifiedNotifEnabled = MutableStateFlow(notificationPrefs.isVerifiedNotificationEnabled)
    val isVerifiedNotifEnabled: StateFlow<Boolean> = _isVerifiedNotifEnabled.asStateFlow()

    private val _isNotifSoundEnabled = MutableStateFlow(notificationPrefs.isSoundEnabled)
    val isNotifSoundEnabled: StateFlow<Boolean> = _isNotifSoundEnabled.asStateFlow()

    private val _isNotifVibrationEnabled = MutableStateFlow(notificationPrefs.isVibrationEnabled)
    val isNotifVibrationEnabled: StateFlow<Boolean> = _isNotifVibrationEnabled.asStateFlow()

    private val _invoiceTimeoutMinutes = MutableStateFlow(notificationPrefs.invoiceTimeoutMinutes)
    val invoiceTimeoutMinutes: StateFlow<Int> = _invoiceTimeoutMinutes.asStateFlow()

    fun toggleDarkMode() {
        val newMode = !_isDarkMode.value
        _isDarkMode.value = newMode
        themePrefs.edit().putBoolean("pref_dark_mode", newMode).apply()
    }

    fun setPendingNotifEnabled(enabled: Boolean) {
        _isPendingNotifEnabled.value = enabled
        notificationPrefs.isPendingNotificationEnabled = enabled
    }

    fun setVerifiedNotifEnabled(enabled: Boolean) {
        _isVerifiedNotifEnabled.value = enabled
        notificationPrefs.isVerifiedNotificationEnabled = enabled
    }

    fun setNotifSoundEnabled(enabled: Boolean) {
        _isNotifSoundEnabled.value = enabled
        notificationPrefs.isSoundEnabled = enabled
    }

    fun setNotifVibrationEnabled(enabled: Boolean) {
        _isNotifVibrationEnabled.value = enabled
        notificationPrefs.isVibrationEnabled = enabled
    }

    fun setInvoiceTimeoutMinutes(minutes: Int) {
        val validMinutes = minutes.coerceIn(2, 120)
        _invoiceTimeoutMinutes.value = validMinutes
        notificationPrefs.invoiceTimeoutMinutes = validMinutes
    }

    fun clearInvoiceMessages() {
        _invoicesState.value = _invoicesState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }

    fun rejectInvoice(orderId: String, reason: String = "سفارش توسط پذیرنده رد شد", amount: Long = 0L) {
        viewModelScope.launch {
            _invoicesState.value = _invoicesState.value.copy(
                rejectingOrderIds = _invoicesState.value.rejectingOrderIds + orderId, errorMessage = null
            )
            when (val result = repository.rejectInvoice(orderId, reason, amount)) {
                is NetworkResult.Success -> {
                    _invoicesState.value = _invoicesState.value.copy(
                        rejectingOrderIds = _invoicesState.value.rejectingOrderIds - orderId,
                        invoices = _invoicesState.value.invoices.filter { it.orderId != orderId },
                        successMessage = "سفارش ${'$'}orderId با موفقیت توسط سرور لغو شد.", errorMessage = null
                    )
                    refreshTransactions(1)
                }
                is NetworkResult.Error -> {
                    _invoicesState.value = _invoicesState.value.copy(
                        rejectingOrderIds = _invoicesState.value.rejectingOrderIds - orderId,
                        errorMessage = "رد سفارش ناموفق بود: ${'$'}{result.errorType.toPersianMessage(result.message)}"
                    )
                    refreshPendingInvoices()
                }
                is NetworkResult.Exception -> {
                    _invoicesState.value = _invoicesState.value.copy(
                        rejectingOrderIds = _invoicesState.value.rejectingOrderIds - orderId,
                        errorMessage = "ارتباط با سرور برای رد سفارش برقرار نشد."
                    )
                    refreshPendingInvoices()
                }
            }
            launch { delay(5000); clearInvoiceMessages() }
        }
    }

    fun verifyInvoiceManually(
        orderId: String,
        amount: Long,
        trackingCode: String? = null,
        cardLast4: String? = null
    ) {
        viewModelScope.launch {
            // Optimistic update: mark as verifying and remove from pending list immediately
            _invoicesState.value = _invoicesState.value.copy(
                verifyingOrderIds = _invoicesState.value.verifyingOrderIds + orderId,
                invoices = _invoicesState.value.invoices.filter { it.orderId != orderId }
            )

            when (val result = repository.verifyInvoiceManually(orderId, amount, trackingCode, cardLast4)) {
                is NetworkResult.Success -> {
                    _invoicesState.value = _invoicesState.value.copy(
                        verifyingOrderIds = _invoicesState.value.verifyingOrderIds - orderId,
                        successMessage = "سفارش $orderId با موفقیت تأیید شد و وب‌هوک تحویل سرویس برای ربات ارسال گردید.",
                        errorMessage = null
                    )
                    refreshDashboard()
                    refreshTransactions(1)
                }
                is NetworkResult.Error -> {
                    _invoicesState.value = _invoicesState.value.copy(
                        verifyingOrderIds = _invoicesState.value.verifyingOrderIds - orderId,
                        errorMessage = "خطا در تأیید دستی: ${result.errorType.toPersianMessage(result.message)}"
                    )
                    repository.getPendingInvoices(limit = 50)
                }
                is NetworkResult.Exception -> {
                    _invoicesState.value = _invoicesState.value.copy(
                        verifyingOrderIds = _invoicesState.value.verifyingOrderIds - orderId,
                        errorMessage = "عدم برقراری ارتباط با سرور جهت تأیید دستی."
                    )
                    repository.getPendingInvoices(limit = 50)
                }
            }

            launch {
                delay(5000)
                clearInvoiceMessages()
            }
        }
    }

    private var livePollingJob: Job? = null

    private fun startLivePolling() {
        livePollingJob?.cancel()
        livePollingJob = viewModelScope.launch {
            while (true) {
                delay(4_000) // Fast 4-second polling while app is active for instant notification & state updates
                if (secureStorage.hasApiKey()) {
                    repository.getPendingInvoices(limit = 50)
                }
            }
        }
    }

    private fun stopLivePolling() {
        livePollingJob?.cancel()
        livePollingJob = null
    }

    init {
        if (secureStorage.hasApiKey()) {
            refreshAll()
            startLivePolling()
        }

        // Keep UI reactive to database cache updates immediately
        viewModelScope.launch {
            repository.cachedPendingInvoices.collect { cachedList ->
                val models = cachedList.map {
                    PendingInvoice(
                        id = it.id,
                        orderId = it.orderId,
                        baseAmount = it.baseAmount,
                        payableAmount = it.payableAmount,
                        expectedAmount = it.expectedAmount,
                        status = it.status,
                        createdAt = it.createdAt,
                        expiresAt = it.expiresAt,
                        remainingSeconds = it.remainingSeconds,
                        customerName = it.customerName,
                        customerPhone = it.customerPhone,
                        customerUsername = it.customerUsername,
                        description = it.description,
                        extraData = it.extraData
                    )
                }
                _invoicesState.value = _invoicesState.value.copy(invoices = models)
            }
        }
    }

    private val webAuthService = WebAuthService()

    fun loginWithCredentials(username: String, password: String) {
        val trimmed = username.trim()
        if (trimmed.isBlank()) {
            _setupError.value = "لطفاً نام کاربری را وارد کنید."
            return
        }
        if (password.isBlank()) {
            _setupError.value = "لطفاً رمز عبور را وارد کنید."
            return
        }

        viewModelScope.launch {
            _isConnecting.value = true
            _setupError.value = null

            when (val authResult = webAuthService.loginWithCredentials(trimmed, password)) {
                is WebAuthResult.Success -> {
                    secureStorage.saveApiKey(authResult.apiKey)
                    when (val result = repository.getAccountStatus()) {
                        is NetworkResult.Success -> {
                            _isConnecting.value = false
                            app.scheduleBackgroundWorkers()
                            _authState.value = AuthState.Connected
                            val isActive = (result.data.account?.isActive ?: 1) != 0
                            _dashboardState.value = _dashboardState.value.copy(
                                data = result.data,
                                isAccountActive = isActive,
                                isServerReachable = true
                            )
                            repository.sendHeartbeat()
                            refreshPendingInvoices()
                            startLivePolling()
                        }
                        else -> {
                            _isConnecting.value = false
                            app.scheduleBackgroundWorkers()
                            _authState.value = AuthState.Connected
                            repository.sendHeartbeat()
                            refreshPendingInvoices()
                            startLivePolling()
                        }
                    }
                }
                is WebAuthResult.Error -> {
                    _isConnecting.value = false
                    _setupError.value = authResult.message
                }
            }
        }
    }

    fun connectWithApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) {
            _setupError.value = "لطفاً کلید API را وارد کنید."
            return
        }

        viewModelScope.launch {
            _isConnecting.value = true
            _setupError.value = null

            secureStorage.saveApiKey(trimmed)

            when (val result = repository.getAccountStatus()) {
                is NetworkResult.Success -> {
                    _isConnecting.value = false
                    app.scheduleBackgroundWorkers()
                    _authState.value = AuthState.Connected
                    val isActive = (result.data.account?.isActive ?: 1) != 0
                    _dashboardState.value = _dashboardState.value.copy(
                        data = result.data,
                        isAccountActive = isActive,
                        isServerReachable = true
                    )
                    // Initial heartbeat and pending sync
                    repository.sendHeartbeat()
                    refreshPendingInvoices()
                    startLivePolling()
                }

                is NetworkResult.Error -> {
                    // If server responded with 401, key is definitely invalid
                    if (result.errorType == ErrorType.UNAUTHORIZED) {
                        _isConnecting.value = false
                        secureStorage.clearApiKey()
                        _setupError.value = "کلید API نامعتبر است."
                    } else {
                        // For other non-fatal errors (e.g. timeout or no internet), allow proceeding
                        _isConnecting.value = false
                        app.scheduleBackgroundWorkers()
                        _authState.value = AuthState.Connected
                        _dashboardState.value = _dashboardState.value.copy(
                            isServerReachable = false,
                            errorMessage = result.errorType.toPersianMessage(result.message)
                        )
                    }
                }

                is NetworkResult.Exception -> {
                    // In case of network timeout / unreachable, allow proceeding with cached key
                    _isConnecting.value = false
                    app.scheduleBackgroundWorkers()
                    _authState.value = AuthState.Connected
                    _dashboardState.value = _dashboardState.value.copy(
                        isServerReachable = false,
                        errorMessage = "عدم برقراری ارتباط با سرور. ارتباط در پس‌زمینه تلاش مجدد خواهد شد."
                    )
                }
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            launch { refreshDashboard() }
            launch { refreshPendingInvoices() }
            launch { refreshTransactions(1) }
            launch { sendHeartbeatNow() }
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _dashboardState.value = _dashboardState.value.copy(
                isLoading = true,
                errorMessage = null,
                batteryLevel = DeviceUtils.getBatteryLevel(app),
                deviceId = DeviceUtils.getInstallationId(app),
                isInternetAvailable = DeviceUtils.isNetworkAvailable(app)
            )

            when (val result = repository.getAccountStatus()) {
                is NetworkResult.Success -> {
                    val isActive = (result.data.account?.isActive ?: 1) != 0
                    _dashboardState.value = _dashboardState.value.copy(
                        isLoading = false,
                        data = result.data,
                        isAccountActive = isActive,
                        isServerReachable = true,
                        errorMessage = null
                    )
                }

                is NetworkResult.Error -> {
                    _dashboardState.value = _dashboardState.value.copy(
                        isLoading = false,
                        errorMessage = result.errorType.toPersianMessage(result.message),
                        isServerReachable = result.errorType != ErrorType.NO_INTERNET && result.errorType != ErrorType.SERVER_ERROR
                    )
                }

                is NetworkResult.Exception -> {
                    _dashboardState.value = _dashboardState.value.copy(
                        isLoading = false,
                        isServerReachable = false,
                        errorMessage = "عدم امکان ارتباط با سرور."
                    )
                }
            }
        }
    }

    fun refreshPendingInvoices() {
        viewModelScope.launch {
            _invoicesState.value = _invoicesState.value.copy(isLoading = true)
            when (val result = repository.getPendingInvoices(limit = 50)) {
                is NetworkResult.Success -> {
                    _invoicesState.value = _invoicesState.value.copy(
                        isLoading = false,
                        invoices = result.data,
                        errorMessage = null
                    )
                }
                is NetworkResult.Error -> {
                    val hasExisting = _invoicesState.value.invoices.isNotEmpty()
                    _invoicesState.value = _invoicesState.value.copy(
                        isLoading = false,
                        errorMessage = if (hasExisting) null else result.errorType.toPersianMessage(result.message)
                    )
                    if (!hasExisting) {
                        launch {
                            delay(5000)
                            clearInvoiceMessages()
                        }
                    }
                }
                is NetworkResult.Exception -> {
                    val hasExisting = _invoicesState.value.invoices.isNotEmpty()
                    _invoicesState.value = _invoicesState.value.copy(
                        isLoading = false,
                        errorMessage = if (hasExisting) null else "عدم دریافت فاکتورها."
                    )
                    if (!hasExisting) {
                        launch {
                            delay(5000)
                            clearInvoiceMessages()
                        }
                    }
                }
            }
        }
    }

    fun refreshTransactions(page: Int = 1) {
        viewModelScope.launch {
            _transactionsState.value = _transactionsState.value.copy(isLoading = true, errorMessage = null)
            when (val result = repository.getTransactionHistory(page = page, limit = 20)) {
                is NetworkResult.Success -> {
                    _transactionsState.value = _transactionsState.value.copy(
                        isLoading = false,
                        transactions = result.data.transactions,
                        currentPage = result.data.page,
                        total = result.data.total
                    )
                }
                is NetworkResult.Error -> {
                    _transactionsState.value = _transactionsState.value.copy(
                        isLoading = false,
                        errorMessage = result.errorType.toPersianMessage(result.message)
                    )
                }
                is NetworkResult.Exception -> {
                    _transactionsState.value = _transactionsState.value.copy(
                        isLoading = false,
                        errorMessage = "عدم دریافت تاریخچه تراکنش‌ها."
                    )
                }
            }
        }
    }

    fun resetRevenueStats(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = repository.resetRevenueStats()
            if (success) {
                // Update local dashboard state
                val currentData = _dashboardState.value.data
                if (currentData != null) {
                    val updatedTx = currentData.transactions?.copy(
                        verifiedCount = 0,
                        verifiedAmount = 0L
                    ) ?: com.example.data.model.TransactionSummary(verifiedCount = 0, verifiedAmount = 0L)
                    _dashboardState.value = _dashboardState.value.copy(
                        data = currentData.copy(transactions = updatedTx)
                    )
                }
            }
            onResult(success)
        }
    }

    fun clearAllArchives(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = repository.clearAllArchives()
            if (success) {
                refreshPendingInvoices()
            }
            onResult(success)
        }
    }

    fun sendHeartbeatNow() {
        viewModelScope.launch {
            repository.sendHeartbeat()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            stopLivePolling()
            app.cancelBackgroundWorkers()
            repository.disconnect()
            _authState.value = AuthState.NeedsSetup
            _dashboardState.value = DashboardUiState()
            _invoicesState.value = InvoicesUiState()
            _transactionsState.value = TransactionsUiState()
        }
    }

    fun getMaskedApiKey(): String {
        return secureStorage.getMaskedApiKey()
    }
}
