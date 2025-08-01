package com.example.quickchat.data.repository

import com.example.quickchat.data.local.ChatRoomDao
import com.example.quickchat.data.model.ChatRoom
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirestoreChatRoomRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val chatRoomDao: ChatRoomDao
) : ChatRoomRepository {

    override fun getChatRooms(userId: String): Flow<List<ChatRoom>> {
        return combine(
            getRemoteChatRooms(userId),
            chatRoomDao.getChatRooms(userId)
        ) { remoteRooms, localRooms ->
            remoteRooms.ifEmpty { localRooms }
        }
    }

    private fun getRemoteChatRooms(userId: String): Flow<List<ChatRoom>> = flow {
        try {
            val snapshot = firestore.collection("chatrooms")
                .whereArrayContains("participants", userId)
                .get()
                .await()

            val rooms = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: emptyMap()
                    ChatRoom(
                        roomId = doc.id,
                        name = data["name"] as? String ?: "Chat Room",
                        lastMessage = data["lastMessage"] as? String,
                        lastTimestamp = data["lastTimestamp"] as? Long ?: 0L,
                        unreadCount = calculateUnreadCount(
                            userId = userId,
                            lastRead = data["lastRead_$userId"] as? Long ?: 0L,
                            lastTimestamp = data["lastTimestamp"] as? Long ?: 0L
                        ),
                        userId = userId,
                        participants = data["participants"] as? List<String> ?: emptyList(),
                        lastRead = data["lastRead_$userId"] as? Long ?: 0L
                    )
                } catch (e: Exception) {
                    null
                }
            }

            chatRoomDao.insertAll(rooms)
            emit(rooms)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun updateLastReadTimestamp(
        roomId: String,
        userId: String,
        timestamp: Long
    ) {
        try {
            // Update remote
            firestore.collection("chatrooms")
                .document(roomId)
                .update("lastRead_$userId", timestamp)
                .await()

            // Update local
            chatRoomDao.getRoomById(roomId)?.let { room ->
                val updatedRoom = room.copy(lastRead = timestamp)
                chatRoomDao.insertAll(listOf(updatedRoom))
            }
        } catch (e: Exception) {
            // Handle error
        }
    }

    override suspend fun createChatRoom(user1: String, user2: String): Result<String> {
        return try {
            val roomId = listOf(user1, user2).sorted().joinToString("-")
            val newRoom = ChatRoom(
                roomId = roomId,
                name = "Chat between $user1 and $user2",
                lastMessage = "", // Initialize with empty message
                lastTimestamp = System.currentTimeMillis(), // Use current time as default
                participants = listOf(user1, user2),
                userId = user1,
                lastRead = 0L,
                unreadCount = 0
            )

            // Create in Firestore
            firestore.collection("chatrooms")
                .document(roomId)
                .set(mapOf(
                    "name" to newRoom.name,
                    "lastMessage" to newRoom.lastMessage,
                    "lastTimestamp" to newRoom.lastTimestamp,
                    "participants" to newRoom.participants,
                    "lastRead_$user1" to newRoom.lastRead,
                    "lastRead_$user2" to newRoom.lastRead,
                    "createdAt" to System.currentTimeMillis()
                ))
                .await()

            // Create local copy
            chatRoomDao.insertAll(listOf(newRoom))

            Result.success(roomId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun doesRoomExist(roomId: String): Boolean {
        return try {
            // Check remote first
            val remoteExists = firestore.collection("chatrooms")
                .document(roomId)
                .get()
                .await()
                .exists()

            // Fall back to local check
            remoteExists || chatRoomDao.getRoomById(roomId) != null
        } catch (e: Exception) {
            // If network fails, check local only
            chatRoomDao.getRoomById(roomId) != null
        }
    }

    private fun calculateUnreadCount(
        userId: String,
        lastRead: Long,
        lastTimestamp: Long
    ): Int {
        return if (lastTimestamp > lastRead) 1 else 0
    }
}