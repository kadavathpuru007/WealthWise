package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goal_allocations",
    indices = [
        Index("userId"),
        Index("goalId")
    ]
)
data class GoalAllocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val goalId: Long,
    val userId: String,
    val amount: Double,
    val timestamp: Long,
    val note: String = ""
)
