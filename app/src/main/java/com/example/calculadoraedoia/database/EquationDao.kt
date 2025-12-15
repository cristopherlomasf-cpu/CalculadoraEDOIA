package com.example.calculadoraedoia.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EquationDao {
    
    @Query("SELECT * FROM equation_history ORDER BY timestamp DESC LIMIT 50")
    fun getAllHistory(): Flow<List<EquationHistory>>
    
    @Query("SELECT * FROM equation_history WHERE id = :id")
    suspend fun getHistoryById(id: Long): EquationHistory?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: EquationHistory): Long
    
    @Delete
    suspend fun delete(history: EquationHistory)
    
    @Query("DELETE FROM equation_history WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM equation_history")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM equation_history")
    suspend fun getCount(): Int
}
