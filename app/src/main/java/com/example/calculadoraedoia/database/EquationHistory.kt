package com.example.calculadoraedoia.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equation_history")
data class EquationHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val equation: String,
    val x0: String?,
    val y0: String?,
    val yPrime0: String?,  // Condición inicial para y'
    val solution: String,
    val steps: String,
    val timestamp: Long = System.currentTimeMillis()
)
