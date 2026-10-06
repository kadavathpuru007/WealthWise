package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trades",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["tradeDate"])
    ]
)
data class TradeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val userId: String,

    val symbol: String,

    val tradeType: String,

    val buyPrice: Double,

    val sellPrice: Double,

    val quantity: Int,

    val realizedPnl: Double,

    val tradeDate: Long,

    val brokerName: String,

    val status: String
) {
    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_CLOSED = "CLOSED"

        const val TYPE_EQUITY = "EQUITY"
        const val TYPE_FNO = "FNO"
        const val TYPE_CRYPTO = "CRYPTO"
        const val TYPE_COMMODITY = "COMMODITY"

        val SUPPORTED_TYPES = listOf(TYPE_EQUITY, TYPE_FNO, TYPE_CRYPTO, TYPE_COMMODITY)

        /**
         * Pure, centralized single source of truth for trade realized P&L calculation.
         * For CLOSED trades: (sellPrice - buyPrice) * quantity.
         * For OPEN trades: 0.0 (no realized P&L without closed position).
         */
        fun calculateRealizedPnl(
            status: String,
            buyPrice: Double,
            sellPrice: Double,
            quantity: Int
        ): Double {
            return if (status.equals(STATUS_CLOSED, ignoreCase = true)) {
                (sellPrice - buyPrice) * quantity
            } else {
                0.0
            }
        }
    }
}
