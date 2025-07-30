package com.example.quickchat.data.model

data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemMessage: Boolean = false,
    val clientGeneratedId: String = "",
    val status: MessageStatus = MessageStatus.SENDING
)

enum class MessageStatus {
    SENDING,DELIVERED, SENT, FAILED
}

