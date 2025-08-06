package com.example.quickchat.data.model

data class Presence(
    val userId: String,
    val isOnline: Boolean,
    val lastSeen: Long = System.currentTimeMillis()
)
