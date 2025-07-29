package com.example.quickchat.data.model

data class ChatMessage(
    val id: String,
    val text: String,
    val senderId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemMessage: Boolean = false

)
