package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * WealthWise Loan Repayment Entity.
 *
 * Tracks individual repayments and EMI debits linked to a [LoanEntity].
 * Represents loan progress/relationship records and links back to financial transactions
 * via [transactionRef] without duplicating ledger cash outflows.
 */
@Entity(
    tableName = "loan_repayments",
    indices = [
        Index(value = ["loanId"]),
        Index(value = ["userId"]),
        Index(value = ["paymentDate"])
    ]
)
data class LoanRepaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val loanId: Long,

    val userId: String,

    val paidAmount: Double,

    val paymentDate: Long,

    val transactionRef: String? = null,

    val isAutoDetected: Boolean = true
)
