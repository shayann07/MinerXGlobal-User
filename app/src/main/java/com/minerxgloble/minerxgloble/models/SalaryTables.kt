package com.minerxgloble.minerxgloble.models

/** Immutable table that drives salary logic. */
data class SalaryLevel(
    val level: Int,
    val requiredSelf: Double,
    val requiredDirect: Int,
    val requiredIndirect: Int,
    val reward: Double
)

val SALARY_LEVELS = listOf(
    SalaryLevel(1, 100.0, 10, 30, 100.0),
    SalaryLevel(2, 200.0, 20, 60, 200.0),
    SalaryLevel(3, 500.0, 35, 80, 400.0),
    SalaryLevel(4, 800.0, 50, 150, 800.0),
    SalaryLevel(5, 2000.0, 100, 300, 2000.0),
    SalaryLevel(6, 5000.0, 150, 500, 5000.0)
)