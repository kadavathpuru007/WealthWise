package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("""
        SELECT * FROM categories
        WHERE userId = :userId
        ORDER BY name ASC
    """)
    fun getCategoriesFlow(
        userId: String
    ): Flow<List<CategoryEntity>>

    @Query("""
        SELECT * FROM categories
        WHERE userId = :userId
        ORDER BY name ASC
    """)
    suspend fun getCategories(
        userId: String
    ): List<CategoryEntity>

    @Query("""
        SELECT * FROM categories
        ORDER BY name ASC
    """)
    fun getAllCategoriesFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE userId = :userId AND LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun findByName(userId: String, name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM categories WHERE userId = :userId AND LOWER(TRIM(name)) = LOWER(TRIM(:name))")
    suspend fun deleteByName(userId: String, name: String)

    @Query("DELETE FROM categories WHERE userId = :userId")
    suspend fun deleteAll(userId: String)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
