package com.example.quickchat.data.local
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.quickchat.data.model.ChatRoom
import kotlinx.coroutines.flow.Flow
@Dao
interface ChatRoomDao {
    @Query("SELECT * FROM chat_rooms WHERE userId = :userId ORDER BY lastTimestamp DESC")
    fun getChatRooms(userId: String): Flow<List<ChatRoom>>

    @Query("SELECT * FROM chat_rooms WHERE roomId = :roomId")
    suspend fun getRoomById(roomId: String): ChatRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rooms: List<ChatRoom>)

    @Query("DELETE FROM chat_rooms WHERE roomId = :roomId")
    suspend fun deleteRoom(roomId: String)

    @Query("UPDATE chat_rooms SET lastRead = :timestamp WHERE roomId = :roomId")
    suspend fun updateLastRead(roomId: String, timestamp: Long)

    @Query("UPDATE chat_rooms SET unreadCount = :count WHERE roomId = :roomId AND userId = :userId")
    suspend fun updateUnreadCount(roomId: String, userId: String, count: Int)

    @Query("SELECT unreadCount FROM chat_rooms WHERE roomId = :roomId AND userId = :userId")
    fun getUnreadCountFlow(roomId: String, userId: String): Flow<Int>

    @Query("UPDATE chat_rooms SET isMuted = :isMuted WHERE roomId = :roomId")
    suspend fun updateMuteStatus(roomId: String, isMuted: Boolean)

    @Query("UPDATE chat_rooms SET isArchived = :isArchived WHERE roomId = :roomId")
    suspend fun updateArchiveStatus(roomId: String, isArchived: Boolean)

    @Query("UPDATE chat_rooms SET isDeleted = :isDeleted WHERE roomId = :roomId")
    suspend fun updateDeleteStatus(roomId: String, isDeleted: Boolean)

    @Query("UPDATE chat_rooms SET lastMessage = :message, lastTimestamp = :timestamp WHERE roomId = :roomId")
    suspend fun updateLastMessage(roomId: String, message: String, timestamp: Long)


    @Query("SELECT * FROM chat_rooms ORDER BY lastTimestamp DESC")
    fun getAllChatRooms(): Flow<List<ChatRoom>>

    @Query("UPDATE chat_rooms SET lastTimestamp = :timestamp WHERE roomId = :roomId")
    suspend fun updateTimestamp(roomId: String, timestamp: Long)

    @Query("DELETE FROM chat_rooms")
    suspend fun clearAll()

    @Query("UPDATE chat_rooms SET fcmTokens = :tokensJson WHERE roomId = :roomId")
    suspend fun updateFcmTokens(roomId: String, tokensJson: String)

    @Query("SELECT fcmTokens FROM chat_rooms WHERE roomId = :roomId")
    suspend fun getFcmTokens(roomId: String): String?


    @Transaction
    suspend fun markAsRead(roomId: String, userId: String, timestamp: Long) {
        updateLastRead(roomId, timestamp)
        updateUnreadCount(roomId, userId, 0)
    }

    @Transaction
    suspend fun incrementUnreadCount(roomId: String, userId: String) {
        val currentCount = getRoomById(roomId)?.unreadCount ?: 0
        updateUnreadCount(roomId, userId, currentCount + 1)
    }



}