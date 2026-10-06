package com.example.data.repository

import com.example.data.local.GoalAllocationEntity
import com.example.data.local.GoalDao
import com.example.data.local.GoalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

/**
 * GoalsRepository provides access to user savings goals and committed goal allocations.
 */
class GoalsRepository(
    private val goalDao: GoalDao? = null,
    private val inMemoryGoals: List<GoalEntity> = emptyList()
) {
    fun getGoalsFlow(userId: String): Flow<List<GoalEntity>> {
        val dao = goalDao ?: return flowOf(inMemoryGoals)
        return if (userId.isNotBlank()) dao.getGoalsFlow(userId) else dao.getAllGoals()
    }

    fun getTotalGoalAllocatedFlow(userId: String): Flow<Double> {
        val dao = goalDao ?: return flowOf(inMemoryGoals.sumOf { it.savedAmount })
        return if (userId.isNotBlank()) dao.getTotalGoalAllocatedFlow(userId) else flowOf(0.0)
    }

    suspend fun insertGoalAllocation(allocation: GoalAllocationEntity): Long {
        return goalDao?.insertGoalAllocation(allocation) ?: -1L
    }

    suspend fun updateGoalSavedAmount(goalId: Long, amount: Double) {
        goalDao?.updateGoalSavedAmount(goalId, amount)
    }

    fun getTotalAllocated(userId: String): Double? {
        if (inMemoryGoals.isNotEmpty()) {
            return inMemoryGoals.sumOf { it.savedAmount }
        }
        val dao = goalDao ?: return 0.0
        return try {
            val goals = runBlocking {
                if (userId.isNotBlank()) dao.getAllGoals(userId).firstOrNull() ?: emptyList()
                else dao.getAllGoals().firstOrNull() ?: emptyList()
            }
            goals.sumOf { it.savedAmount }
        } catch (_: Throwable) {
            0.0
        }
    }

    suspend fun getTotalAllocatedAsync(userId: String): Double {
        val dao = goalDao ?: return inMemoryGoals.sumOf { it.savedAmount }
        val goals = if (userId.isNotBlank()) dao.getAllGoals(userId).firstOrNull() ?: emptyList()
        else dao.getAllGoals().firstOrNull() ?: emptyList()
        return goals.sumOf { it.savedAmount }
    }
}
