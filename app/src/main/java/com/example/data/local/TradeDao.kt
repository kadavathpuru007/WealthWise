package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {

    @Query("""
        SELECT * FROM trades
        WHERE userId = :userId
        ORDER BY tradeDate DESC
    """)
    fun getAllTradesFlow(
        userId: String
    ): Flow<List<TradeEntity>>

    @Query("""
        SELECT COALESCE(SUM(realizedPnl), 0.0)
        FROM trades
        WHERE userId = :userId
          AND status = 'CLOSED'
    """)
    fun getTotalRealizedPnlFlow(
        userId: String
    ): Flow<Double>

    @Query("""
        SELECT COUNT(*)
        FROM trades
        WHERE userId = :userId
          AND status = 'CLOSED'
    """)
    fun getClosedTradeCountFlow(
        userId: String
    ): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: TradeEntity): Long

    @Delete
    suspend fun deleteTrade(trade: TradeEntity)

    @Query("SELECT * FROM trades WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getTradeById(id: Long, userId: String): TradeEntity?

    @Query("DELETE FROM trades WHERE id = :id AND userId = :userId")
    suspend fun deleteTradeById(id: Long, userId: String)

    @Query("DELETE FROM trades WHERE userId = :userId")
    suspend fun deleteAllTrades(userId: String)
}
