package com.example.data.repository

import com.example.data.local.CategoryDao
import com.example.data.local.CategoryEntity
import kotlinx.coroutines.flow.Flow

class CategoryRepository(
    private val categoryDao: CategoryDao
) {
    fun getCategoriesFlow(userId: String): Flow<List<CategoryEntity>> =
        if (userId.isNotBlank()) categoryDao.getCategoriesFlow(userId) else kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getCategories(userId: String): List<CategoryEntity> =
        if (userId.isNotBlank()) categoryDao.getCategories(userId) else emptyList()

    suspend fun getCategoryById(id: Long): CategoryEntity? =
        categoryDao.getCategoryById(id)

    suspend fun findByName(userId: String, name: String): CategoryEntity? =
        categoryDao.findByName(userId, name)

    suspend fun insertCategory(category: CategoryEntity): Long =
        categoryDao.insertCategory(category)

    suspend fun insertAll(categories: List<CategoryEntity>) =
        categoryDao.insertAll(categories)

    suspend fun updateCategory(category: CategoryEntity) =
        categoryDao.updateCategory(category)

    suspend fun deleteCategory(category: CategoryEntity) =
        categoryDao.deleteCategory(category)

    suspend fun deleteById(id: Long) =
        categoryDao.deleteById(id)

    suspend fun deleteAll(userId: String) =
        categoryDao.deleteAll(userId)
}
