package com.example.data.api

import com.example.data.model.AccountStatusData
import com.example.data.model.ApiResponse
import com.example.data.model.HeartbeatRequest
import com.example.data.model.InvoiceDetail
import com.example.data.model.PendingInvoicesData
import com.example.data.model.RejectInvoiceRequest
import com.example.data.model.SimpleActionResponse
import com.example.data.model.TransactionHistoryData
import com.example.data.model.VerifyPaymentData
import com.example.data.model.VerifyPaymentRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface PayLinkApi {

    @GET("health")
    suspend fun health(): Response<ResponseBody>

    @GET("account_status.php")
    suspend fun getAccountStatus(): Response<ApiResponse<AccountStatusData>>

    @GET("get_pending_invoices.php")
    suspend fun getPendingInvoices(
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<PendingInvoicesData>>

    @GET("invoice_status.php")
    suspend fun getInvoiceStatus(
        @Query("order_id") orderId: String
    ): Response<ApiResponse<InvoiceDetail>>

    @POST("verify_payment.php")
    suspend fun verifyPayment(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: VerifyPaymentRequest
    ): Response<ApiResponse<VerifyPaymentData>>

    @POST("verify_payment.php")
    suspend fun rejectInvoice(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: RejectInvoiceRequest
    ): Response<SimpleActionResponse>

    @POST("heartbeat.php")
    suspend fun sendHeartbeat(
        @Body request: HeartbeatRequest
    ): Response<SimpleActionResponse>

    @GET("transaction_history.php")
    suspend fun getTransactionHistory(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<TransactionHistoryData>>
}
