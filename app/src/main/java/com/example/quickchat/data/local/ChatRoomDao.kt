package com.example.quickchat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.quickchat.data.model.ChatRoom
import kotlinx.coroutines.flow.Flow
@Dao
interface ChatRoomDao {
    // Fixed: Changed 'timestamp' to 'lastTimestamp'
    @Query("SELECT * FROM chat_rooms WHERE userId = :userId ORDER BY lastTimestamp DESC")
    fun getChatRooms(userId: String): Flow<List<ChatRoom>>
    @Query("SELECT * FROM chat_rooms ORDER BY lastTimestamp DESC")
    fun getAllChatRooms(): Flow<List<ChatRoom>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rooms: List<ChatRoom>)

    @Query("SELECT * FROM chat_rooms WHERE roomId = :roomId")
    suspend fun getRoomById(roomId: String): ChatRoom?

    @Query("DELETE FROM chat_rooms WHERE roomId = :roomId")
    suspend fun deleteRoom(roomId: String)

    @Query("UPDATE chat_rooms SET lastRead = :timestamp WHERE roomId = :roomId")
    suspend fun updateLastRead(roomId: String, timestamp: Long)

    @Query("DELETE FROM chat_rooms")
    suspend fun clearAll()
}