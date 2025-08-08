package com.example.quickchat.data.model

import com.google.firebase.firestore.Exclude
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

@Serializable
data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val fcmToken: String = "",
    val imageUrl: String?,
    val messageType: MessageType = MessageType.TEXT,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemMessage: Boolean = false,
    val clientGeneratedId: String = "",
    val isRead: Boolean = false,
    val status: MessageStatus = MessageStatus.SENDING,
    val retryCount: Int? = 0,
    val lastUpdated: Long = System.currentTimeMillis()
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
            "status" to status.name,
            "retryCount" to retryCount,
            "lastUpdated" to lastUpdated
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
                status = MessageStatus.valueOf(map["status"] as? String ?: "SENDING"),
                retryCount = map["retryCount"] as? Int ?: 0,
                lastUpdated = map["lastUpdated"] as? Long ?: System.currentTimeMillis()

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
@Serializable
enum class MessageStatus {
    SENDING, SENT, DELIVERED, SEEN, FAILED
}