package com.example.data.local

/**
 * Room projection for monthly spending aggregated by category.
 */
data class CategorySpending(
    val category: String? = null,
    val totalSpent: Double = 0.0
)
