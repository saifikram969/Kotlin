package com.example.quickchat.data.model

import com.google.firebase.firestore.Exclude

data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val imageUrl: String?,
    val messageType: MessageType = MessageType.TEXT,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemMessage: Boolean = false,
    val clientGeneratedId: String = "",
    val isRead: Boolean = false,
    val status: MessageStatus = MessageStatus.SENDING
) {
    @Exclude
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "text" to text,
            "senderId" to senderId,
            "imageUrl" to imageUrl,
            "messageType" to messageType.name,
            "timestamp" to timestamp,
            "isSystemMessage" to isSystemMessage,
            "clientGeneratedId" to clientGeneratedId,
            "isRead" to isRead,
            "status" to status.name
        )
    }

    companion object {
        fun fromFirestore(map: Map<String, Any>): ChatMessage {
            return ChatMessage(
                id = map["id"] as? String ?: "",
                text = map["text"] as? String ?: "",
                senderId = map["senderId"] as? String ?: "",
                imageUrl = map["imageUrl"] as? String,
                messageType = MessageType.valueOf(map["messageType"] as? String ?: "TEXT"),
                timestamp = map["timestamp"] as? Long ?: System.currentTimeMillis(),
                isSystemMessage = map["isSystemMessage"] as? Boolean ?: false,
                clientGeneratedId = map["clientGeneratedId"] as? String ?: "",
                isRead = map["isRead"] as? Boolean ?: false,
                status = MessageStatus.valueOf(map["status"] as? String ?: "SENDING")
            )
        }
    }
}

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    FILE
}

enum class MessageStatus {
    SENDING, DELIVERED, SENT, FAILED
}