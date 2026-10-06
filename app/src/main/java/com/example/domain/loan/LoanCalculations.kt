package com.example.domain.loan

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/**
 * Pure mathematical calculation engine for loans, EMIs, and repayment dates.
 */
object LoanCalculations {

    /**
     * Calculates the estimated monthly EMI for a standard reducing-balance loan.
     *
     * Formula:
     * r = annualRate / 12 / 100
     * n = tenureMonths
     * If r > 0: EMI = P * r * (1 + r)^n / ((1 + r)^n - 1)
     * If r == 0: EMI = P / n
     *
     * Returns 0.0 if inputs are non-positive or non-finite.
     */
    fun calculateMonthlyEmi(
        principal: Double,
        annualInterestRate: Double,
        tenureMonths: Int
    ): Double {
        if (principal <= 0.0 || principal.isNaN() || principal.isInfinite()) return 0.0
        if (tenureMonths <= 0) return 0.0
        if (annualInterestRate < 0.0 || annualInterestRate.isNaN() || annualInterestRate.isInfinite()) return 0.0

        val r = (annualInterestRate / 100.0) / 12.0
        val n = tenureMonths.toDouble()

        val rawEmi = if (r > 0.0) {
            val factor = (1.0 + r).pow(n)
            if (factor.isNaN() || factor.isInfinite() || factor == 1.0) {
                principal / n
            } else {
                (principal * r * factor) / (factor - 1.0)
            }
        } else {
            principal / n
        }

        if (rawEmi.isNaN() || rawEmi.isInfinite() || rawEmi <= 0.0) return 0.0
        return BigDecimal(rawEmi).setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * Calculates the next upcoming due date given the loan's [emiDueDayOfMonth].
     *
     * Correctly handles:
     * - Month boundaries
     * - February (28/29 days)
     * - 30-day months vs 31-day months (due day 31 clamps to month length)
     * - Today's due date (if unpaid, shows due today; if paid, advances to next month)
     * - Past due date in current month (advances to next month)
     */
    fun calculateNextDueDate(
        emiDueDayOfMonth: Int,
        currentDate: LocalDate = LocalDate.now(),
        isCurrentMonthPaid: Boolean = false
    ): LocalDate {
        val clampedDay = emiDueDayOfMonth.coerceIn(1, 31)

        // Determine valid due day in current month
        val currentMonthLength = currentDate.lengthOfMonth()
        val dueDayThisMonth = clampedDay.coerceAtMost(currentMonthLength)
        val dueDateThisMonth = LocalDate.of(currentDate.year, currentDate.month, dueDayThisMonth)

        return if (!isCurrentMonthPaid && !dueDateThisMonth.isBefore(currentDate)) {
            dueDateThisMonth
        } else {
            // Next month due date
            val nextMonthDate = currentDate.plusMonths(1)
            val nextMonthLength = nextMonthDate.lengthOfMonth()
            val dueDayNextMonth = clampedDay.coerceAtMost(nextMonthLength)
            LocalDate.of(nextMonthDate.year, nextMonthDate.month, dueDayNextMonth)
        }
    }

    /**
     * Calculates days remaining until [dueDate] from [currentDate].
     * Never returns negative days.
     */
    fun calculateDaysRemaining(
        currentDate: LocalDate = LocalDate.now(),
        dueDate: LocalDate
    ): Int {
        val days = ChronoUnit.DAYS.between(currentDate, dueDate)
        return days.toInt().coerceAtLeast(0)
    }

    /**
     * Converts a [LocalDate] to milliseconds epoch timestamp for Room storage.
     */
    fun toEpochMilli(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Calculates remaining debt clamped to >= 0.0.
     */
    fun calculateRemainingAmount(principalAmount: Double, totalRepaid: Double): Double {
        return (principalAmount - totalRepaid).coerceAtLeast(0.0)
    }

    /**
     * Calculates progress percentage [0f..1f].
     */
    fun calculateProgress(principalAmount: Double, totalRepaid: Double): Float {
        if (principalAmount <= 0.0) return 0f
        return (totalRepaid / principalAmount).toFloat().coerceIn(0f, 1f)
    }
}
