package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        androidx.room.Index(value = ["userId", "timestamp"]),
        androidx.room.Index(value = ["userId", "referenceId"]),
        androidx.room.Index(value = ["userId", "fingerprint"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: String = "",
    val title: String,
    val amount: Double,
    val type: String, // "INCOME" or "EXPENSE"
    val category: String, // "Food", "Shopping", "Transport", "Entertainment", "Subscription", "Bills", "Salary", "Freelance", "Investment", "Other"
    val paymentMethod: String, // "UPI", "Bank Transfer", "Credit Card", "Debit Card", "Cash"
    val date: String, // "YYYY-MM-DD"
    val timestamp: Long = System.currentTimeMillis(),
    val isParsed: Boolean = false,
    val merchant: String = "",
    val note: String = "",
    val eventType: String = type,
    val status: String = "COMPLETED",
    val referenceId: String = "",
    val sourceAccount: String = "",
    val destinationAccount: String = "",
    val originalTransactionId: Int? = null,
    val fingerprint: String = ""
) {
    val isInflow: Boolean
        get() = type.equals("credit", ignoreCase = true) ||
                type.equals("INCOME", ignoreCase = true) ||
                type.equals("LOAN_DISBURSEMENT", ignoreCase = true) ||
                eventType.equals("credit", ignoreCase = true) ||
                eventType.equals("INCOME", ignoreCase = true) ||
                eventType.equals("REFUND", ignoreCase = true) ||
                eventType.equals("REVERSAL", ignoreCase = true) ||
                eventType.equals("LOAN_DISBURSEMENT", ignoreCase = true)

    val isGoalTransfer: Boolean
        get() = type.equals("GOAL_TRANSFER", ignoreCase = true) ||
                eventType.equals("GOAL_TRANSFER", ignoreCase = true) ||
                eventType.equals("GOAL_ALLOCATION", ignoreCase = true) ||
                type.equals("GOAL_ALLOCATION", ignoreCase = true) ||
                type.equals("GOAL_RESERVE", ignoreCase = true) ||
                category.equals("Savings Goal", ignoreCase = true) ||
                category.equals("Goal Allocation", ignoreCase = true)

    val isTransfer: Boolean
        get() = type.equals("TRANSFER", ignoreCase = true) ||
                eventType.equals("TRANSFER", ignoreCase = true) ||
                isGoalTransfer

    val isOutflow: Boolean
        get() = !isInflow && !isTransfer && !isGoalTransfer && (
            type.equals("debit", ignoreCase = true) ||
            type.equals("EXPENSE", ignoreCase = true) ||
            type.equals("LOAN_PAYMENT", ignoreCase = true) ||
            type.equals("SUBSCRIPTION_PAYMENT", ignoreCase = true) ||
            eventType.equals("debit", ignoreCase = true) ||
            eventType.equals("EXPENSE", ignoreCase = true) ||
            eventType.equals("LOAN_PAYMENT", ignoreCase = true) ||
            eventType.equals("SUBSCRIPTION_PAYMENT", ignoreCase = true)
        )

    val ledgerAmount: Double
        get() = when {
            isTransfer -> 0.0
            isInflow -> kotlin.math.abs(amount)
            else -> -kotlin.math.abs(amount)
        }

    val signedAmount: Double
        get() = ledgerAmount
}

@Entity(tableName = "budgets", primaryKeys = ["userId", "category"])
data class BudgetEntity(
    val userId: String = "",
    val category: String,
    val monthlyLimit: Double,
    val colorHex: String = "#EF4444"
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String = "",
    val title: String,
    val targetAmount: Double,
    val timelineMonths: Int = 0,
    val savedAmount: Double = 0.0,
    val category: String = "Vehicle",
    val monthlyTarget: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: String = "",
    val title: String,
    val amount: Double,
    val dueDate: String,
    val category: String, // "Electricity", "Broadband", "Credit Card", "Rent", "Water"
    val isPaid: Boolean = false
) {
    val billerName: String
        get() = title
}



@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: String = "",
    val title: String,
    val message: String,
    val type: String, // "ALERT", "PARSED", "BILL", "INFO"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
