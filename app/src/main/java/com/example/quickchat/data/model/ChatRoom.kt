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
    val userId: String,  // To associate rooms with users
    val participants: List<String> = emptyList(),
    val lastRead: Long = 0L // For tracking read status


){
// Helper function to convert to Firestore map
fun toFirestoreMap(): Map<String, Any> {
    return mapOf(
        "name" to name,
        "lastMessage" to (lastMessage ?: ""),
        "lastTimestamp" to lastTimestamp,
        "participants" to participants,
        "lastRead_${userId}" to lastRead
    )
}}