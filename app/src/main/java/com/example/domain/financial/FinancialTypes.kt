package com.example.domain.financial

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Supported financial event types in the central WealthWise ledger.
 */
enum class FinancialEventType {
    INCOME,
    EXPENSE,
    TRANSFER,
    GOAL_ALLOCATION,
    GOAL_TRANSFER,
    INVESTMENT_PURCHASE,
    INVESTMENT_SALE,
    LOAN_DISBURSEMENT,
    LOAN_PAYMENT,
    SUBSCRIPTION_PAYMENT,
    REFUND,
    REVERSAL,
    ADJUSTMENT
}

/**
 * Lifecycle status of a financial transaction.
 * Only COMPLETED events affect completed financial totals unless explicitly specified.
 */
enum class FinancialStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REVERSED,
    REFUNDED
}

/**
 * Standard transaction categories used across WealthWise.
 */
enum class TransactionCategory(val displayName: String) {
    FOOD("Food"),
    SHOPPING("Shopping"),
    TRANSPORT("Transport"),
    SUBSCRIPTION("Subscription"),
    BILLS("Bills"),
    ENTERTAINMENT("Entertainment"),
    HEALTH("Health"),
    PERSONAL_TRANSFER("Personal Transfer"),
    SALARY("Salary"),
    FREELANCE("Freelance"),
    INVESTMENT("Investment"),
    REWARDS("Rewards"),
    REFUND("Refund"),
    ATM_WITHDRAWAL("ATM Withdrawal"),
    OTHER("Other");

    companion object {
        fun fromString(value: String): TransactionCategory {
            val normalized = value.trim()
            return entries.firstOrNull { 
                it.displayName.equals(normalized, ignoreCase = true) || 
                it.name.equals(normalized, ignoreCase = true) 
            } ?: OTHER
        }
    }
}

/**
 * Validation severity levels.
 */
enum class ValidationSeverity {
    VALID,
    WARNING,
    BLOCKED
}

/**
 * Result returned by the FinancialValidationEngine before committing financial events.
 */
sealed class ValidationResult {
    object Valid : ValidationResult()

    data class Warning(
        val title: String,
        val message: String,
        val availableAmount: BigDecimal,
        val plannedAmount: BigDecimal,
        val shortfall: BigDecimal,
        val affectedFeature: String,
        val actionSuggestion: String = "You can proceed, but your current plan exceeds available money."
    ) : ValidationResult()

    data class Blocked(
        val title: String,
        val message: String,
        val availableBalance: BigDecimal,
        val requiredAmount: BigDecimal,
        val shortfall: BigDecimal,
        val reason: String
    ) : ValidationResult()
}

/**
 * Exact monetary utility using BigDecimal with standard financial rounding (2 decimals).
 * Prevents floating-point rounding errors (e.g. ₹0.10 + ₹0.20 = ₹0.30).
 */
object MoneyUtils {
    private val DEFAULT_SCALE = 2
    private val DEFAULT_ROUNDING = RoundingMode.HALF_EVEN

    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING)

    fun of(value: Double): BigDecimal {
        return if (value.isNaN() || value.isInfinite()) {
            ZERO
        } else {
            BigDecimal.valueOf(value).setScale(DEFAULT_SCALE, DEFAULT_ROUNDING)
        }
    }

    fun of(value: Long): BigDecimal {
        return BigDecimal.valueOf(value).setScale(DEFAULT_SCALE, DEFAULT_ROUNDING)
    }

    fun of(value: String): BigDecimal {
        return try {
            BigDecimal(value.replace(",", "").trim()).setScale(DEFAULT_SCALE, DEFAULT_ROUNDING)
        } catch (e: Exception) {
            ZERO
        }
    }

    fun formatInr(amount: BigDecimal): String {
        val longVal = amount.setScale(0, RoundingMode.HALF_UP).toLong()
        val isNegative = longVal < 0
        val absVal = kotlin.math.abs(longVal)
        val s = absVal.toString()

        val formatted = if (s.length <= 3) {
            s
        } else {
            val lastThree = s.substring(s.length - 3)
            val rest = s.substring(0, s.length - 3)
            val sb = StringBuilder()
            var count = 0
            for (i in rest.length - 1 downTo 0) {
                sb.insert(0, rest[i])
                count++
                if (count % 2 == 0 && i != 0) {
                    sb.insert(0, ',')
                }
            }
            sb.append(',').append(lastThree).toString()
        }

        return if (isNegative) "-$formatted" else formatted
    }

    fun formatInr(amount: Double): String = formatInr(of(amount))
}

/**
 * Detailed budget calculation status per category.
 */
data class CategoryBudgetStatus(
    val category: String,
    val limit: BigDecimal,
    val spent: BigDecimal,
    val remaining: BigDecimal,
    val percentageUsed: Double,
    val isAlmostExhausted: Boolean, // >= 90%
    val isExceeded: Boolean,        // > 100%
    val exceededBy: BigDecimal,
    val colorHex: String
)

/**
 * Detailed goal calculation status per goal.
 */
data class GoalCalculationStatus(
    val goalId: Long = 0L,
    val title: String,
    val targetAmount: BigDecimal,
    val savedAmount: BigDecimal,
    val remainingAmount: BigDecimal,
    val progressPercent: Double,
    val targetDate: String,
    val monthsRemaining: Int,
    val requiredMonthlyContribution: BigDecimal,
    val isAffordable: Boolean,
    val shortfall: BigDecimal
)

/**
 * Structured warning information for multi-feature financial plan collisions.
 */
data class FinancialPlanCollision(
    val hasCollision: Boolean,
    val availableCash: BigDecimal,
    val totalPlannedBudgets: BigDecimal,
    val totalRequiredGoalContributions: BigDecimal,
    val upcomingLoanObligations: BigDecimal,
    val upcomingSubscriptionObligations: BigDecimal = MoneyUtils.ZERO,
    val totalPlannedObligations: BigDecimal,
    val totalShortfall: BigDecimal,
    val budgetShortfall: BigDecimal,
    val goalShortfall: BigDecimal,
    val warnings: List<String>
)

/**
 * Derived financial state representing the single authoritative source of truth.
 */
data class FinancialOverview(
    val currentBalance: BigDecimal = MoneyUtils.ZERO,
    val safeToSpend: BigDecimal = MoneyUtils.ZERO,
    val availableBalance: BigDecimal = MoneyUtils.ZERO,
    val totalIncome: BigDecimal = MoneyUtils.ZERO,
    val totalExpenses: BigDecimal = MoneyUtils.ZERO,
    val netCashflow: BigDecimal = MoneyUtils.ZERO,
    val savingsRate: Int = 0,
    val totalSavings: BigDecimal = MoneyUtils.ZERO,
    val totalInvestments: BigDecimal = MoneyUtils.ZERO,
    val totalLoanLiabilities: BigDecimal = MoneyUtils.ZERO,
    val totalPlannedBudget: BigDecimal = MoneyUtils.ZERO,
    val totalBudgetSpent: BigDecimal = MoneyUtils.ZERO,
    val budgetRemaining: BigDecimal = MoneyUtils.ZERO,
    val isBudgetExceeded: Boolean = false,
    val budgetExceededBy: BigDecimal = MoneyUtils.ZERO,
    val totalRequiredGoalContribution: BigDecimal = MoneyUtils.ZERO,
    val availableAfterPlannedBudgets: BigDecimal = MoneyUtils.ZERO,
    val totalMonthlySubscriptionCost: BigDecimal = MoneyUtils.ZERO,
    val totalAnnualSubscriptionCost: BigDecimal = MoneyUtils.ZERO,
    val netWorth: BigDecimal = MoneyUtils.ZERO,
    val categoryBudgets: List<CategoryBudgetStatus> = emptyList(),
    val goalStatuses: List<GoalCalculationStatus> = emptyList(),
    val planCollision: FinancialPlanCollision = FinancialPlanCollision(
        hasCollision = false,
        availableCash = MoneyUtils.ZERO,
        totalPlannedBudgets = MoneyUtils.ZERO,
        totalRequiredGoalContributions = MoneyUtils.ZERO,
        upcomingLoanObligations = MoneyUtils.ZERO,
        upcomingSubscriptionObligations = MoneyUtils.ZERO,
        totalPlannedObligations = MoneyUtils.ZERO,
        totalShortfall = MoneyUtils.ZERO,
        budgetShortfall = MoneyUtils.ZERO,
        goalShortfall = MoneyUtils.ZERO,
        warnings = emptyList()
    )
)
