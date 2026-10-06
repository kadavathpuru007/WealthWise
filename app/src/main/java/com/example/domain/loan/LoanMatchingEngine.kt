package com.example.domain.loan

import com.example.data.local.LoanEntity
import kotlin.math.abs

sealed interface LoanMatchResult {
    data class ExactMatch(val loan: LoanEntity) : LoanMatchResult
    data class Ambiguous(val candidateLoans: List<LoanEntity>) : LoanMatchResult
    data object NoMatch : LoanMatchResult
}

/**
 * Matching engine that attributes detected completed EMI / NACH debits
 * to the appropriate user loan without false associations.
 */
object LoanMatchingEngine {

    /**
     * Attempts to match a detected EMI amount and text/lender hints against the user's active loans.
     *
     * Rules:
     * 1. Lender/Loan name match prioritized.
     * 2. Candidate amount matching allowed within [toleranceRatio] (default +/- 5%).
     * 3. Multiple plausible loans are flagged as [LoanMatchResult.Ambiguous], never randomly selected.
     */
    fun matchEmiToLoan(
        detectedAmount: Double,
        detectedMerchantOrLender: String,
        detectedTitleOrText: String,
        activeLoans: List<LoanEntity>,
        toleranceRatio: Double = 0.05
    ): LoanMatchResult {
        if (activeLoans.isEmpty() || detectedAmount <= 0.0) return LoanMatchResult.NoMatch

        val textToSearch = "$detectedMerchantOrLender $detectedTitleOrText".trim()

        // 1. Lender / Loan name keyword matches
        val lenderMatches = activeLoans.filter { loan ->
            (loan.lenderName.isNotBlank() && textToSearch.contains(loan.lenderName.trim(), ignoreCase = true)) ||
            (loan.loanName.isNotBlank() && textToSearch.contains(loan.loanName.trim(), ignoreCase = true))
        }

        if (lenderMatches.isNotEmpty()) {
            val amountCandidates = lenderMatches.filter { loan ->
                loan.emiAmount > 0.0 && (abs(loan.emiAmount - detectedAmount) / loan.emiAmount) <= toleranceRatio
            }

            return when {
                amountCandidates.size == 1 -> LoanMatchResult.ExactMatch(amountCandidates.first())
                amountCandidates.size > 1 -> {
                    // Check if exactly one has exact match
                    val exactAmount = amountCandidates.filter { abs(it.emiAmount - detectedAmount) <= 1.0 }
                    if (exactAmount.size == 1) {
                        LoanMatchResult.ExactMatch(exactAmount.first())
                    } else {
                        LoanMatchResult.Ambiguous(amountCandidates)
                    }
                }
                lenderMatches.size == 1 -> {
                    // Lender is unique match, but amount outside tolerance: ambiguous for confirmation
                    LoanMatchResult.Ambiguous(lenderMatches)
                }
                else -> LoanMatchResult.Ambiguous(lenderMatches)
            }
        }

        // 2. Fallback: match by amount within tolerance
        val amountCandidates = activeLoans.filter { loan ->
            loan.emiAmount > 0.0 && (abs(loan.emiAmount - detectedAmount) / loan.emiAmount) <= toleranceRatio
        }

        return when {
            amountCandidates.isEmpty() -> LoanMatchResult.NoMatch
            amountCandidates.size == 1 -> LoanMatchResult.ExactMatch(amountCandidates.first())
            else -> LoanMatchResult.Ambiguous(amountCandidates) // Multiple loans with similar EMI: do not guess!
        }
    }
}
