package com.example.keeper.data

data class TriggerLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean,
    val message: String,
    val networkType: String
)
