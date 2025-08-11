package com.example.quickchat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow


@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    fun getMessagesByRoom(roomId: String): Flow<List<ChatMessageEntity>>
    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    suspend fun getMessagesByRoomOnce(roomId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("SELECT * FROM chat_messages WHERE status = 'FAILED' AND roomId = :roomId")
    suspend fun getFailedMessages(roomId: String): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId")
    suspend fun clearRoomMessages(roomId: String)

    @Query("SELECT MIN(timestamp) FROM chat_messages WHERE roomId = :roomId")
    suspend fun getOldestTimestamp(roomId: String): Long?

    @Query("SELECT MAX(timestamp) FROM chat_messages WHERE roomId = :roomId")
    suspend fun getNewestTimestamp(roomId: String): Long?

//offline query
    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId AND status = 'SENDING'")
    suspend fun getPendingMessages(roomId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId AND timestamp > :sinceTimestamp ORDER BY timestamp ASC")
    fun getMessagesSince(roomId: String, sinceTimestamp: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessage(roomId: String): ChatMessageEntity?

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId AND senderId = :userId")
    suspend fun clearMessagesForUserInRoom(roomId: String, userId: String)


}