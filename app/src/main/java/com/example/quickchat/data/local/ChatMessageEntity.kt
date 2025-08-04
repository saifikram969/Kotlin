package com.example.quickchat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val text: String,
    val senderId: String,
    val timestamp: Long,
    val status: String, // "SENDING", "SENT", "FAILED"
    val isSystemMessage: Boolean,
    val clientGeneratedId: String,
    val roomId: String,
    val messageType: String, // <-- ADD THIS
    val imageUrl: String? = null // <-- ADD THIS
)