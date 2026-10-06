package com.example.ui.payment

import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener

/**
 * Bridge between MainActivity and the active payment Composable/ViewModel
 * to route Razorpay Checkout callback events safely.
 */
object RazorpayPaymentBridge {
    @Volatile
    var listener: PaymentResultWithDataListener? = null

    fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        listener?.onPaymentSuccess(razorpayPaymentId, paymentData)
    }

    fun onPaymentError(errorCode: Int, errorDescription: String?, paymentData: PaymentData?) {
        listener?.onPaymentError(errorCode, errorDescription, paymentData)
    }
}
