package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Reactive Room Data Access Object for loans and loan repayments.
 * All queries are strictly scoped to [userId] to ensure multi-user isolation.
 */
@Dao
interface LoanDao {

    @Query("""
        SELECT * FROM loans
        WHERE userId = :userId
          AND isClosed = 0
        ORDER BY emiDueDayOfMonth ASC
    """)
    fun getActiveLoansFlow(
        userId: String
    ): Flow<List<LoanEntity>>

    @Query("""
        SELECT * FROM loans
        WHERE userId = :userId
        ORDER BY id DESC
    """)
    fun getAllLoansFlow(
        userId: String
    ): Flow<List<LoanEntity>>

    @Query("""
        SELECT * FROM loans
        WHERE userId = :userId
          AND isClosed = 1
        ORDER BY id DESC
    """)
    fun getClosedLoansFlow(
        userId: String
    ): Flow<List<LoanEntity>>

    @Query("""
        SELECT * FROM loans
        WHERE id = :loanId AND userId = :userId
        LIMIT 1
    """)
    fun getLoanByIdFlow(
        loanId: Long,
        userId: String
    ): Flow<LoanEntity?>

    @Query("""
        SELECT * FROM loans
        WHERE id = :loanId AND userId = :userId
        LIMIT 1
    """)
    suspend fun getLoanById(
        loanId: Long,
        userId: String
    ): LoanEntity?

    @Query("""
        SELECT * FROM loan_repayments
        WHERE loanId = :loanId
          AND userId = :userId
        ORDER BY paymentDate DESC
    """)
    fun getRepaymentsForLoanFlow(
        loanId: Long,
        userId: String
    ): Flow<List<LoanRepaymentEntity>>

    @Query("""
        SELECT * FROM loan_repayments
        WHERE userId = :userId
        ORDER BY paymentDate DESC
    """)
    fun getAllRepaymentsForUserFlow(
        userId: String
    ): Flow<List<LoanRepaymentEntity>>

    @Query("""
        SELECT COALESCE(SUM(paidAmount), 0.0)
        FROM loan_repayments
        WHERE loanId = :loanId
          AND userId = :userId
    """)
    fun getTotalPaidForLoanFlow(
        loanId: Long,
        userId: String
    ): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(paidAmount), 0.0)
        FROM loan_repayments
        WHERE userId = :userId
    """)
    fun getTotalPaidForUserFlow(
        userId: String
    ): Flow<Double>

    @Query("""
        SELECT COALESCE(
            SUM(
                MAX(0.0, l.principalAmount - COALESCE(
                    (SELECT SUM(r.paidAmount) FROM loan_repayments r WHERE r.loanId = l.id AND r.userId = :userId),
                    0.0
                ))
            ),
            0.0
        )
        FROM loans l
        WHERE l.userId = :userId AND l.isClosed = 0
    """)
    fun getTotalRemainingDebtFlow(
        userId: String
    ): Flow<Double>

    @Query("""
        SELECT COUNT(*)
        FROM loan_repayments
        WHERE loanId = :loanId
          AND userId = :userId
    """)
    fun getRepaymentCountForLoanFlow(
        loanId: Long,
        userId: String
    ): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: LoanEntity): Long

    @Update
    suspend fun updateLoan(loan: LoanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepayment(repayment: LoanRepaymentEntity): Long

    @Query("SELECT * FROM loan_repayments WHERE transactionRef = :transactionRef AND userId = :userId LIMIT 1")
    suspend fun findRepaymentByTransactionRef(transactionRef: String, userId: String): LoanRepaymentEntity?

    @Query("UPDATE loans SET isClosed = 1 WHERE id = :loanId AND userId = :userId")
    suspend fun closeLoan(loanId: Long, userId: String)

    @Query("DELETE FROM loans WHERE id = :id AND userId = :userId")
    suspend fun deleteLoan(id: Long, userId: String)

    // Legacy signatures for backward compatibility
    @Query("SELECT * FROM loans WHERE userId = :userId ORDER BY id ASC")
    fun getAllLoans(userId: String): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans ORDER BY id ASC")
    fun getAllLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id LIMIT 1")
    suspend fun getLoanById(id: Int): LoanEntity?

    @Query("SELECT * FROM loans WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getLoanById(id: Int, userId: String): LoanEntity?

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoan(id: Int)

    @Query("UPDATE loans SET principalAmount = MAX(0.0, principalAmount - :amountPaid) WHERE id = :id")
    suspend fun recordEmiPayment(id: Int, amountPaid: Double)

    @Query("UPDATE loans SET principalAmount = MAX(0.0, principalAmount - :amountPaid) WHERE id = :id")
    suspend fun recordEmiPayment(id: Long, amountPaid: Double)

    @Query("DELETE FROM loans WHERE userId = :userId")
    suspend fun deleteAll(userId: String)

    @Query("DELETE FROM loans")
    suspend fun deleteAll()

    @Query("DELETE FROM loan_repayments WHERE userId = :userId")
    suspend fun deleteAllRepayments(userId: String)

    @Query("DELETE FROM loan_repayments")
    suspend fun deleteAllRepayments()
}
