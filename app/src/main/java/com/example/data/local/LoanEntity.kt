package com.example.data.local

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * WealthWise Loan Liability Record.
 *
 * Represents a debt obligation (personal loan, home loan, education loan, vehicle loan, or consumer EMI).
 * Represents a liability record in WealthWise and is strictly isolated per authenticated [userId].
 */
@Entity(
    tableName = "loans",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "isClosed"])
    ]
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val userId: String = "",

    val loanName: String = "",

    val lenderName: String = "",

    val loanType: String = TYPE_PERSONAL,

    val principalAmount: Double = 0.0,

    val emiAmount: Double = 0.0,

    val interestRate: Double = 0.0,

    val totalTenureMonths: Int = 0,

    val emiDueDayOfMonth: Int = 1,

    val startDate: Long = System.currentTimeMillis(),

    val isClosed: Boolean = false
) {
    // Secondary constructor for legacy compatibility
    @Ignore
    constructor(
        id: Long = 0L,
        userId: String = "",
        title: String,
        lender: String,
        principal: Double,
        remaining: Double = principal,
        interestRate: Double = 0.0,
        monthlyEmi: Double = 0.0,
        nextDueDate: String = "",
        tenureMonthsLeft: Int = 0
    ) : this(
        id = id,
        userId = userId,
        loanName = title,
        lenderName = lender,
        loanType = TYPE_PERSONAL,
        principalAmount = principal,
        emiAmount = monthlyEmi,
        interestRate = interestRate,
        totalTenureMonths = tenureMonthsLeft,
        emiDueDayOfMonth = 1,
        startDate = System.currentTimeMillis(),
        isClosed = remaining <= 0.0
    )

    @Ignore
    constructor(
        id: Int,
        userId: String,
        title: String,
        lender: String,
        principal: Double,
        remaining: Double,
        interestRate: Double,
        monthlyEmi: Double,
        nextDueDate: String,
        tenureMonthsLeft: Int
    ) : this(
        id = id.toLong(),
        userId = userId,
        loanName = title,
        lenderName = lender,
        loanType = TYPE_PERSONAL,
        principalAmount = principal,
        emiAmount = monthlyEmi,
        interestRate = interestRate,
        totalTenureMonths = tenureMonthsLeft,
        emiDueDayOfMonth = 1,
        startDate = System.currentTimeMillis(),
        isClosed = remaining <= 0.0
    )

    // Legacy compatibility accessors (no backing fields, ignored by Room)
    val title: String get() = loanName
    val lender: String get() = lenderName
    val principal: Double get() = principalAmount
    val remaining: Double get() = principalAmount
    val monthlyEmi: Double get() = emiAmount
    val nextDueDate: String get() = ""
    val tenureMonthsLeft: Int get() = totalTenureMonths

    companion object {
        const val TYPE_PERSONAL = "PERSONAL"
        const val TYPE_HOME = "HOME"
        const val TYPE_EDUCATION = "EDUCATION"
        const val TYPE_VEHICLE = "VEHICLE"
        const val TYPE_CONSUMER_EMI = "CONSUMER_EMI"

        val SUPPORTED_TYPES = listOf(
            TYPE_PERSONAL,
            TYPE_HOME,
            TYPE_EDUCATION,
            TYPE_VEHICLE,
            TYPE_CONSUMER_EMI
        )

        fun isValidLoanType(type: String): Boolean {
            return SUPPORTED_TYPES.any { it.equals(type.trim(), ignoreCase = true) }
        }
    }
}
