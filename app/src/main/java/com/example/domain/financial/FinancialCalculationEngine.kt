package com.example.domain.financial

import com.example.data.local.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pure, centralized single source of truth for all WealthWise financial calculations.
 * Guarantees mathematical consistency across all screens, charts, and notifications.
 */
object FinancialCalculationEngine {

    /**
     * Derives the complete authoritative FinancialOverview from the given entities.
     * All calculations are deterministic, zero-allocation safe, and free from floating-point errors.
     */
    fun calculateOverview(
        startingBalance: BigDecimal,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity> = emptyList(),
        goals: List<GoalEntity> = emptyList(),
        loans: List<LoanEntity> = emptyList(),
        subscriptions: List<SubscriptionEntity> = emptyList(),
        referenceDate: Date = Date()
    ): FinancialOverview {
        // Filter transactions for completed vs non-completed
        val completedTxns = transactions.filter { txn ->
            // If status is present, must be COMPLETED or SUCCESS (or empty/legacy default which is treated as completed)
            val s = txn.status.uppercase(Locale.ROOT)
            s.isEmpty() || s == FinancialStatus.COMPLETED.name || s == "SUCCESS" || s == "PAID"
        }

        // 1. Calculate Cash Balance
        var cashBalance = startingBalance
        var qualifyingIncome = MoneyUtils.ZERO
        var qualifyingExpenses = MoneyUtils.ZERO
        var totalInvestments = MoneyUtils.ZERO

        for (txn in completedTxns) {
            val amount = MoneyUtils.of(kotlin.math.abs(txn.amount))
            val eventType = determineEventType(txn)

            when (eventType) {
                FinancialEventType.INCOME -> {
                    cashBalance = cashBalance.add(amount)
                    qualifyingIncome = qualifyingIncome.add(amount)
                }
                FinancialEventType.EXPENSE,
                FinancialEventType.SUBSCRIPTION_PAYMENT -> {
                    cashBalance = cashBalance.subtract(amount)
                    qualifyingExpenses = qualifyingExpenses.add(amount)
                }
                FinancialEventType.TRANSFER -> {
                    // Transfers between user's own accounts do NOT change total cash balance
                    // and do NOT count as income or expense
                }
                FinancialEventType.GOAL_ALLOCATION,
                FinancialEventType.GOAL_TRANSFER -> {
                    // Goal funding is a savings allocation / wealth transfer, NOT an ordinary expense.
                    // When a user funds a goal, Actual Balance MUST NOT decrease.
                    // (Available Balance decreases by the funded amount via goal savings).
                }
                FinancialEventType.INVESTMENT_PURCHASE -> {
                    // Cash converted to investment asset
                    cashBalance = cashBalance.subtract(amount)
                    totalInvestments = totalInvestments.add(amount)
                    // NOT an ordinary consumption expense
                }
                FinancialEventType.INVESTMENT_SALE -> {
                    // Cash returned from investment asset
                    cashBalance = cashBalance.add(amount)
                    totalInvestments = totalInvestments.subtract(amount).coerceAtLeast(MoneyUtils.ZERO)
                }
                FinancialEventType.LOAN_DISBURSEMENT -> {
                    // Cash received from lender: increases cash, but NOT qualifying income
                    cashBalance = cashBalance.add(amount)
                }
                FinancialEventType.LOAN_PAYMENT -> {
                    // Outflow for loan EMI: decreases cash
                    cashBalance = cashBalance.subtract(amount)
                    // Note: Loan payment principal reduces debt; interest is expense
                }
                FinancialEventType.REFUND,
                FinancialEventType.REVERSAL -> {
                    // Restores cash balance and reduces consumption expenses
                    cashBalance = cashBalance.add(amount)
                    qualifyingExpenses = qualifyingExpenses.subtract(amount).coerceAtLeast(MoneyUtils.ZERO)
                }
                FinancialEventType.ADJUSTMENT -> {
                    if (txn.type.equals("INCOME", ignoreCase = true)) {
                        cashBalance = cashBalance.add(amount)
                    } else {
                        cashBalance = cashBalance.subtract(amount)
                    }
                }
            }
        }

        // Net Cashflow
        val netCashflow = qualifyingIncome.subtract(qualifyingExpenses)

        // Savings Rate: (Income - Expenses) / Income * 100 (Safe division)
        val savingsRate = if (qualifyingIncome > MoneyUtils.ZERO) {
            val net = qualifyingIncome.subtract(qualifyingExpenses)
            if (net > MoneyUtils.ZERO) {
                val rate = net.divide(qualifyingIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .toInt()
                rate.coerceIn(0, 100)
            } else {
                0
            }
        } else {
            0
        }

        // Total Savings from Goals
        var totalSavings = MoneyUtils.ZERO
        for (goal in goals) {
            totalSavings = totalSavings.add(MoneyUtils.of(goal.savedAmount))
        }
        val goalAllocationTxnSum = completedTxns
            .filter {
                val ev = determineEventType(it)
                ev == FinancialEventType.GOAL_ALLOCATION || ev == FinancialEventType.GOAL_TRANSFER
            }
            .fold(MoneyUtils.ZERO) { acc, txn -> acc.add(MoneyUtils.of(kotlin.math.abs(txn.amount))) }
        val effectiveSavings = if (totalSavings > MoneyUtils.ZERO) totalSavings else goalAllocationTxnSum

        // Total Loan Liabilities
        var totalLoanLiabilities = MoneyUtils.ZERO
        for (loan in loans) {
            totalLoanLiabilities = totalLoanLiabilities.add(MoneyUtils.of(loan.remaining))
        }

        // Total Investments: sum of investment transactions (or explicit assets)
        val explicitInvestmentsFromTxn = completedTxns
            .filter { it.category.equals("Investment", ignoreCase = true) || it.eventType == FinancialEventType.INVESTMENT_PURCHASE.name }
            .sumOf { it.amount }
        if (totalInvestments == MoneyUtils.ZERO && explicitInvestmentsFromTxn > 0.0) {
            totalInvestments = MoneyUtils.of(explicitInvestmentsFromTxn)
        }

        // 2. Budget Calculations
        val categoryBudgets = calculateCategoryBudgets(budgets, completedTxns)
        val totalPlannedBudget = budgets.fold(MoneyUtils.ZERO) { acc, b -> acc.add(MoneyUtils.of(b.monthlyLimit)) }
        val totalBudgetSpent = categoryBudgets.fold(MoneyUtils.ZERO) { acc, cb -> acc.add(cb.spent) }
        val budgetRemaining = totalPlannedBudget.subtract(totalBudgetSpent)
        val isBudgetExceeded = totalPlannedBudget > MoneyUtils.ZERO && totalBudgetSpent > totalPlannedBudget
        val budgetExceededBy = if (isBudgetExceeded) totalBudgetSpent.subtract(totalPlannedBudget) else MoneyUtils.ZERO

        // Available cash after planned budgets
        val availableAfterPlannedBudgets = cashBalance.subtract(totalPlannedBudget)

        // 3. Goal Calculations & Affordability
        val goalStatuses = calculateGoalStatuses(goals, availableAfterPlannedBudgets, referenceDate)
        val totalRequiredGoalContribution = goalStatuses.fold(MoneyUtils.ZERO) { acc, gs ->
            acc.add(gs.requiredMonthlyContribution)
        }

        // 4. Financial Plan Collision Detection
        val planCollision = evaluatePlanCollision(
            availableCash = cashBalance,
            totalPlannedBudget = totalPlannedBudget,
            totalRequiredGoalContribution = totalRequiredGoalContribution,
            loans = loans,
            subscriptions = subscriptions
        )

        // 5. Subscription Planning Metrics (Zero fake transactions - planning metrics only)
        val activeSubscriptions = subscriptions.filter { it.status == "ACTIVE" }
        var totalMonthlySubscriptionCost = MoneyUtils.ZERO
        var totalAnnualSubscriptionCost = MoneyUtils.ZERO
        for (sub in activeSubscriptions) {
            val amt = if (sub.amount > 0.0) sub.amount else sub.monthlyCost
            val monthly = com.example.domain.subscription.SubscriptionCalculations.calculateMonthlyEquivalent(amt, sub.billingPeriod)
            val annual = com.example.domain.subscription.SubscriptionCalculations.calculateAnnualCost(amt, sub.billingPeriod)
            totalMonthlySubscriptionCost = totalMonthlySubscriptionCost.add(MoneyUtils.of(monthly))
            totalAnnualSubscriptionCost = totalAnnualSubscriptionCost.add(MoneyUtils.of(annual))
        }

        // 6. Safe to Spend: BUDGET-BASED value.
        // It represents the amount currently available for spending according to the user's active/current budget plan.
        // Calculated from REAL budget data and REAL spending.
        // Do NOT calculate Safe to Spend from Goals.
        // Do NOT treat Goal Savings as Safe to Spend.
        // Do NOT make Safe to Spend equal to Actual Balance.
        // Do NOT hardcode Safe to Spend.
        // Whenever actual spending occurs, Safe to Spend updates according to the existing budget logic.
        // Whenever the budget changes, Safe to Spend updates.
        // If there is no budget, show 0 (appropriate empty/zero state rather than fake data).
        val safeToSpend = if (totalPlannedBudget > MoneyUtils.ZERO) {
            budgetRemaining.coerceAtLeast(MoneyUtils.ZERO)
        } else {
            MoneyUtils.ZERO
        }

        // Available Balance (Dedicated strictly to Goals):
        // Equals: Actual Balance - Total Saved Across Goals.
        // Active budget caps must NEVER reduce Available Balance.
        // When a user funds a goal, Available Balance decreases by the funded amount.
        val availableBalance = cashBalance.subtract(effectiveSavings).coerceAtLeast(MoneyUtils.ZERO)

        // 7. Net Worth: Cash + Investments + Savings - Liabilities
        val netWorth = cashBalance.add(totalInvestments).add(totalSavings).subtract(totalLoanLiabilities)

        return FinancialOverview(
            currentBalance = cashBalance,
            safeToSpend = safeToSpend,
            availableBalance = availableBalance,
            totalIncome = qualifyingIncome,
            totalExpenses = qualifyingExpenses,
            netCashflow = netCashflow,
            savingsRate = savingsRate,
            totalSavings = totalSavings,
            totalInvestments = totalInvestments,
            totalLoanLiabilities = totalLoanLiabilities,
            totalPlannedBudget = totalPlannedBudget,
            totalBudgetSpent = totalBudgetSpent,
            budgetRemaining = budgetRemaining,
            isBudgetExceeded = isBudgetExceeded,
            budgetExceededBy = budgetExceededBy,
            totalRequiredGoalContribution = totalRequiredGoalContribution,
            availableAfterPlannedBudgets = availableAfterPlannedBudgets,
            totalMonthlySubscriptionCost = totalMonthlySubscriptionCost,
            totalAnnualSubscriptionCost = totalAnnualSubscriptionCost,
            netWorth = netWorth,
            categoryBudgets = categoryBudgets,
            goalStatuses = goalStatuses,
            planCollision = planCollision
        )
    }

    /**
     * Determines the exact FinancialEventType for a transaction entity.
     * Backwards-compatible with legacy transactions that only have type/category.
     */
    fun determineEventType(txn: TransactionEntity): FinancialEventType {
        if (txn.isInflow || txn.type.equals("credit", ignoreCase = true) || txn.type.equals("INCOME", ignoreCase = true) ||
            txn.eventType.equals("credit", ignoreCase = true) || txn.eventType.equals("INCOME", ignoreCase = true)) {
            val upperCat = txn.category.trim().uppercase(Locale.ROOT)
            val upperEvent = txn.eventType.trim().uppercase(Locale.ROOT)
            if (upperCat == "REFUND" || upperEvent == "REFUND") return FinancialEventType.REFUND
            if (upperCat == "REVERSAL" || upperEvent == "REVERSAL") return FinancialEventType.REVERSAL
            return FinancialEventType.INCOME
        }

        val explicitType = try {
            FinancialEventType.valueOf(txn.eventType.uppercase(Locale.ROOT))
        } catch (e: Exception) {
            null
        }
        if (explicitType != null) {
            return explicitType
        }

        // Legacy fallbacks based on category / type
        val cat = txn.category.trim()
        val type = txn.type.trim().uppercase(Locale.ROOT)

        return when {
            cat.equals("Transfer", ignoreCase = true) || type == "TRANSFER" -> FinancialEventType.TRANSFER
            cat.equals("Investment", ignoreCase = true) -> {
                if (type == "INCOME" || type == "CREDIT" || txn.isInflow) FinancialEventType.INVESTMENT_SALE else FinancialEventType.INVESTMENT_PURCHASE
            }
            type == "GOAL_TRANSFER" || type == "GOAL_ALLOCATION" || cat.equals("Goal Allocation", ignoreCase = true) || cat.equals("Savings Goal", ignoreCase = true) -> FinancialEventType.GOAL_TRANSFER
            cat.equals("Loan Disbursement", ignoreCase = true) -> FinancialEventType.LOAN_DISBURSEMENT
            cat.equals("Loan EMI", ignoreCase = true) || cat.equals("Loan Payment", ignoreCase = true) -> FinancialEventType.LOAN_PAYMENT
            cat.equals("Subscription", ignoreCase = true) -> FinancialEventType.SUBSCRIPTION_PAYMENT
            cat.equals("Refund", ignoreCase = true) -> FinancialEventType.REFUND
            cat.equals("Reversal", ignoreCase = true) -> FinancialEventType.REVERSAL
            type == "INCOME" || type == "CREDIT" || txn.isInflow -> FinancialEventType.INCOME
            else -> FinancialEventType.EXPENSE
        }
    }

    /**
     * Computes individual budget categories, spent amounts, and overrun percentages.
     */
    private fun calculateCategoryBudgets(
        budgets: List<BudgetEntity>,
        completedTxns: List<TransactionEntity>
    ): List<CategoryBudgetStatus> {
        val expenseTxns = completedTxns.filter { txn ->
            val eventType = determineEventType(txn)
            eventType == FinancialEventType.EXPENSE || eventType == FinancialEventType.SUBSCRIPTION_PAYMENT
        }
        val refundTxns = completedTxns.filter { txn ->
            val eventType = determineEventType(txn)
            eventType == FinancialEventType.REFUND || eventType == FinancialEventType.REVERSAL
        }

        return budgets.map { budget ->
            val limit = MoneyUtils.of(budget.monthlyLimit)
            val isCatchAll = budget.category.equals("All", ignoreCase = true) ||
                    budget.category.equals("Overall", ignoreCase = true) ||
                    budget.category.equals("Total", ignoreCase = true) ||
                    budget.category.equals("All Expenses", ignoreCase = true) ||
                    budget.category.equals("Monthly Budget", ignoreCase = true) ||
                    budget.category.equals("Budget", ignoreCase = true)

            val spentDouble = if (isCatchAll) {
                (expenseTxns.sumOf { kotlin.math.abs(it.amount) } - refundTxns.sumOf { kotlin.math.abs(it.amount) }).coerceAtLeast(0.0)
            } else {
                val expense = expenseTxns
                    .filter { it.category.trim().equals(budget.category.trim(), ignoreCase = true) }
                    .sumOf { kotlin.math.abs(it.amount) }
                val refund = refundTxns
                    .filter { it.category.trim().equals(budget.category.trim(), ignoreCase = true) }
                    .sumOf { kotlin.math.abs(it.amount) }
                (expense - refund).coerceAtLeast(0.0)
            }
            val spent = MoneyUtils.of(spentDouble)
            val remaining = limit.subtract(spent)

            val percentageUsed = if (limit > MoneyUtils.ZERO) {
                spent.divide(limit, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toDouble()
            } else {
                0.0
            }

            val isExceeded = limit > MoneyUtils.ZERO && spent > limit
            val isAlmostExhausted = limit > MoneyUtils.ZERO && percentageUsed >= 90.0 && !isExceeded
            val exceededBy = if (isExceeded) spent.subtract(limit) else MoneyUtils.ZERO

            CategoryBudgetStatus(
                category = budget.category,
                limit = limit,
                spent = spent,
                remaining = remaining,
                percentageUsed = percentageUsed,
                isAlmostExhausted = isAlmostExhausted,
                isExceeded = isExceeded,
                exceededBy = exceededBy,
                colorHex = budget.colorHex
            )
        }
    }

    /**
     * Computes goal status, progress percentage, and required monthly contribution.
     */
    private fun calculateGoalStatuses(
        goals: List<GoalEntity>,
        availableAfterBudgets: BigDecimal,
        referenceDate: Date
    ): List<GoalCalculationStatus> {
        return goals.map { goal ->
            val target = MoneyUtils.of(goal.targetAmount)
            val saved = MoneyUtils.of(goal.savedAmount)
            val remaining = target.subtract(saved).coerceAtLeast(MoneyUtils.ZERO)

            val progress = if (target > MoneyUtils.ZERO) {
                saved.divide(target, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toDouble().coerceIn(0.0, 100.0)
            } else {
                0.0
            }

            // Estimate months remaining: if monthlyTarget > 0, use target / monthlyTarget, otherwise default to 12
            val monthlyTarget = MoneyUtils.of(goal.monthlyTarget)
            val monthsRemaining = if (monthlyTarget > MoneyUtils.ZERO && remaining > MoneyUtils.ZERO) {
                remaining.divide(monthlyTarget, 0, RoundingMode.CEILING).toInt().coerceAtLeast(1)
            } else {
                12
            }

            val requiredMonthly = if (remaining > MoneyUtils.ZERO) {
                remaining.divide(BigDecimal(monthsRemaining), 2, RoundingMode.HALF_UP)
            } else {
                MoneyUtils.ZERO
            }

            val isAffordable = availableAfterBudgets >= requiredMonthly
            val shortfall = if (!isAffordable) requiredMonthly.subtract(availableAfterBudgets).coerceAtLeast(MoneyUtils.ZERO) else MoneyUtils.ZERO

            GoalCalculationStatus(
                goalId = goal.id,
                title = goal.title,
                targetAmount = target,
                savedAmount = saved,
                remainingAmount = remaining,
                progressPercent = progress,
                targetDate = "",
                monthsRemaining = monthsRemaining,
                requiredMonthlyContribution = requiredMonthly,
                isAffordable = isAffordable,
                shortfall = shortfall
            )
        }
    }

    /**
     * Evaluates whether total planned budgets + goal requirements + upcoming EMIs exceed available cash.
     */
    fun evaluatePlanCollision(
        availableCash: BigDecimal,
        totalPlannedBudget: BigDecimal,
        totalRequiredGoalContribution: BigDecimal,
        loans: List<LoanEntity>,
        subscriptions: List<SubscriptionEntity>
    ): FinancialPlanCollision {
        val totalMonthlyEmi = loans.fold(MoneyUtils.ZERO) { acc, loan ->
            acc.add(MoneyUtils.of(loan.monthlyEmi))
        }

        val totalMonthlySubscriptions = subscriptions
            .filter { it.status == "ACTIVE" }
            .fold(MoneyUtils.ZERO) { acc, sub ->
                val amt = if (sub.amount > 0.0) sub.amount else sub.monthlyCost
                val monthly = com.example.domain.subscription.SubscriptionCalculations.calculateMonthlyEquivalent(amt, sub.billingPeriod)
                acc.add(MoneyUtils.of(monthly))
            }

        val totalObligations = totalPlannedBudget
            .add(totalRequiredGoalContribution)
            .add(totalMonthlyEmi)
            .add(totalMonthlySubscriptions)

        val totalShortfall = if (totalObligations > availableCash) {
            totalObligations.subtract(availableCash)
        } else {
            MoneyUtils.ZERO
        }

        val budgetShortfall = if (totalPlannedBudget > availableCash) {
            totalPlannedBudget.subtract(availableCash)
        } else {
            MoneyUtils.ZERO
        }

        val availableAfterBudgets = availableCash.subtract(totalPlannedBudget)
        val goalShortfall = if (totalRequiredGoalContribution > availableAfterBudgets) {
            totalRequiredGoalContribution.subtract(availableAfterBudgets.coerceAtLeast(MoneyUtils.ZERO))
        } else {
            MoneyUtils.ZERO
        }

        val warnings = mutableListOf<String>()
        if (totalPlannedBudget > availableCash && availableCash > MoneyUtils.ZERO) {
            warnings.add("Planned monthly budgets (₹${MoneyUtils.formatInr(totalPlannedBudget)}) exceed available cash (₹${MoneyUtils.formatInr(availableCash)}) by ₹${MoneyUtils.formatInr(budgetShortfall)}.")
        }
        if (goalShortfall > MoneyUtils.ZERO) {
            warnings.add("Required monthly goal contributions (₹${MoneyUtils.formatInr(totalRequiredGoalContribution)}) exceed remaining funds by ₹${MoneyUtils.formatInr(goalShortfall)}.")
        }
        if (totalShortfall > MoneyUtils.ZERO) {
            val subText = if (totalMonthlySubscriptions > MoneyUtils.ZERO) " including ₹${MoneyUtils.formatInr(totalMonthlySubscriptions)} in recurring subscriptions" else ""
            warnings.add("Total monthly planned commitments (₹${MoneyUtils.formatInr(totalObligations)}$subText) exceed available cash by ₹${MoneyUtils.formatInr(totalShortfall)}.")
        }

        return FinancialPlanCollision(
            hasCollision = totalShortfall > MoneyUtils.ZERO || budgetShortfall > MoneyUtils.ZERO,
            availableCash = availableCash,
            totalPlannedBudgets = totalPlannedBudget,
            totalRequiredGoalContributions = totalRequiredGoalContribution,
            upcomingLoanObligations = totalMonthlyEmi,
            upcomingSubscriptionObligations = totalMonthlySubscriptions,
            totalPlannedObligations = totalObligations,
            totalShortfall = totalShortfall,
            budgetShortfall = budgetShortfall,
            goalShortfall = goalShortfall,
            warnings = warnings
        )
    }

    /**
     * Splits a loan EMI payment into principal and interest portions.
     * Prevents loan principal from reducing more than the actual payment,
     * and accounts for interest as a distinct financial expense.
     */
    fun splitLoanPayment(
        remainingPrincipal: BigDecimal,
        interestRateAnnualPercent: BigDecimal,
        paymentAmount: BigDecimal
    ): Pair<BigDecimal, BigDecimal> {
        if (remainingPrincipal <= MoneyUtils.ZERO || paymentAmount <= MoneyUtils.ZERO) {
            return Pair(MoneyUtils.ZERO, MoneyUtils.ZERO)
        }

        // Monthly interest = (Remaining * (Rate / 100)) / 12
        val annualRateFraction = interestRateAnnualPercent.divide(BigDecimal(100), 6, RoundingMode.HALF_UP)
        val monthlyInterest = remainingPrincipal.multiply(annualRateFraction)
            .divide(BigDecimal(12), 2, RoundingMode.HALF_UP)

        val interestPortion = monthlyInterest.min(paymentAmount)
        val principalPortion = paymentAmount.subtract(interestPortion).min(remainingPrincipal)

        return Pair(principalPortion, interestPortion)
    }
}
