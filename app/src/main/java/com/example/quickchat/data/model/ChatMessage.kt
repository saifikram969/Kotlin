package com.example.quickchat.data.model

import com.google.firebase.firestore.Exclude
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val fcmToken: String = "",
    val imageUrl: String?,
    val fileUrl: String? = null,
    val isTemp: Boolean = false,
    val thumbnailUrl: String? = null,
    var fileName: String? = null,
    var fileType: String? = null,
    var fileSize: Long? = null,
    val uploadProgress: Float? = null,
    val messageType: MessageType = MessageType.TEXT,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemMessage: Boolean = false,
    val clientGeneratedId: String = "",
    val isRead: Boolean = false,
    val status: MessageStatus = MessageStatus.SENDING,
    val retryCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),

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
            "lastUpdated" to lastUpdated,
            "fileUrl" to fileUrl,
            "thumbnailUrl" to thumbnailUrl,
            "fileName" to fileName,
            "fileType" to fileType,
            "fileSize" to fileSize,
            "uploadProgress" to uploadProgress,
            "isTemp" to isTemp,
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
                lastUpdated = map["lastUpdated"] as? Long ?: System.currentTimeMillis(),
                thumbnailUrl = map["thumbnailUrl"] as? String,
                fileName = map["fileName"] as? String,
                fileType = map["fileType"] as? String,
                fileSize = (map["fileSize"] as? Number)?.toLong(),
                uploadProgress = (map["uploadProgress"] as? Number)?.toFloat(),
                isTemp = map["isTemp"] as? Boolean ?: false


            )
        }
    }
}

enum class MessageType {
    TEXT,
    IMAGE,
    PDF,
    AUDIO,
    VIDEO,
    FILE;

    companion object {
        fun fromString(value: String?): MessageType {
            return try {
                valueOf(value ?: "TEXT")
            } catch (e: IllegalArgumentException) {
                FILE // Default fallback for unknown types
            }
        }
    }

}
@Serializable
enum class MessageStatus {
    SENDING, SENT, DELIVERED, SEEN, FAILED;
    companion object {
        fun fromString(value: String?): MessageStatus {
            return try {
                valueOf(value ?: "SENDING")
            } catch (e: IllegalArgumentException) {
                SENDING // Default fallback
            }
        }
    }
}