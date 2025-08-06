package com.example.quickchat.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "chat_rooms")
data class ChatRoom(
    @PrimaryKey
    val roomId: String,
    val name: String,
    val lastMessage: String?,
    val lastTimestamp: Long,
    val unreadCount: Int,
    val userId: String,
    val participants: List<String> = emptyList(),
    val lastRead: Long = 0L,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val isMuted: Boolean = false,
    val isProcessingMute: Boolean = false,
    val pendingMuteState: Boolean? = null,
    val fcmTokens: Map<String, String> = emptyMap()

){
// Helper function to convert to Firestore map
fun toFirestoreMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>(
        "name" to name,
        "lastMessage" to (lastMessage ?: ""),
        "lastTimestamp" to lastTimestamp,
        "participants" to participants,
        "isArchived" to isArchived,
        "isMuted" to isMuted,
        "fcmTokens" to fcmTokens
    )

    // Add lastRead fields for all participants
    participants.forEach { userId ->
        map["lastRead_$userId"] = lastRead
    }

    return map
}

}