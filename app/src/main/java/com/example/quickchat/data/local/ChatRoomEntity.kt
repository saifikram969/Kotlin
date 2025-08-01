package com.example.quickchat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.quickchat.data.model.ChatRoom


@Entity(tableName = "chat_rooms")
data class ChatRoomEntity(
    @PrimaryKey
    val roomId: String,
    val name: String,
    val lastMessage: String?,
    val lastTimestamp: Long,
    val unreadCount: Int,
    val userId: String,
    val lastRead: Long
) {
    fun toChatRoom(participants: List<String>): ChatRoom {
        return ChatRoom(
            roomId = roomId,
            name = name,
            lastMessage = lastMessage,
            lastTimestamp = lastTimestamp,
            unreadCount = unreadCount,
            userId = userId,
            participants = participants,
            lastRead = lastRead
        )
    }
}