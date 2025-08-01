package com.example.quickchat.data.repository

import com.example.quickchat.data.model.ChatRoom
import kotlinx.coroutines.flow.Flow

interface ChatRoomRepository {
    fun getChatRooms(userId: String): Flow<List<ChatRoom>>
    suspend fun updateLastReadTimestamp(roomId: String, userId: String, timestamp: Long)



    suspend fun createChatRoom(user1: String, user2: String): Result<String> // Add this
    suspend fun doesRoomExist(roomId: String): Boolean // Add this for verification

}// Create this in a new file or at the top of your repository
sealed class RepositoryResult<out T> {
    data class Success<out T>(val value: T) : RepositoryResult<T>()
    data class Failure(val exception: Throwable) : RepositoryResult<Nothing>()
}