package com.example.quickchat.data.model


data class TypingIndicator(
    val userId: String,
    val isTyping: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
