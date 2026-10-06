package com.example.data.local

import java.util.Locale

fun isCompletedTransaction(txn: TransactionEntity): Boolean {
    val s = txn.status.uppercase(Locale.ROOT)
    val isFailed = s == "FAILED" || s == "REJECTED" || txn.eventType.equals("FAILED", ignoreCase = true)
    val isPending = s == "PENDING" || txn.eventType.equals("PENDING", ignoreCase = true)
    val isDuplicate = s == "DUPLICATE" || txn.eventType.equals("DUPLICATE", ignoreCase = true)
    val isPromotional = s == "PROMOTIONAL" || txn.eventType.equals("PROMOTIONAL", ignoreCase = true)
    val isBalanceOnly = s == "BALANCE_ONLY" || txn.eventType.equals("BALANCE_ONLY", ignoreCase = true)
    return !isFailed && !isPending && !isDuplicate && !isPromotional && !isBalanceOnly &&
            (s.isEmpty() || s == "COMPLETED" || s == "SUCCESS" || s == "PAID")
}