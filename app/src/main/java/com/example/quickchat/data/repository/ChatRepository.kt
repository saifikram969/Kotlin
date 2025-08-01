package com.example.quickchat.data.repository

import android.util.Log
import com.example.quickchat.data.local.ChatMessageDao
import com.example.quickchat.data.local.toChatMessage
import com.example.quickchat.data.local.toEntity
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.model.ChatRoom
import com.example.quickchat.data.model.MessageStatus
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject

private const val CHATROOMS_COLLECTION = "chatrooms"
private const val TAG = "ChatRepository"

class ChatRepository @Inject constructor(
    private val database: FirebaseFirestore,
    private val chatMessageDao: ChatMessageDao
) {
    init {
        database.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
    }

    // Chat Room Operations
    fun getChatRoomsForUser(userId: String): Flow<List<ChatRoom>> = callbackFlow {
        val listener = database.collection(CHATROOMS_COLLECTION)
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> {
                        Log.e(TAG, "Error getting chat rooms", error)
                        trySend(emptyList())
                    }
                    snapshot == null -> {
                        trySend(emptyList())
                    }
                    else -> {
                        val rooms = snapshot.documents.mapNotNull { doc ->
                            try {
                                val data = doc.data ?: emptyMap()
                                val participants = data["participants"] as? List<String> ?: emptyList()
                                val otherUserId = participants.firstOrNull { it != userId } ?: ""
                                val lastRead = data["lastRead_$userId"] as? Long ?: 0L

                                ChatRoom(
                                    roomId = doc.id,
                                    name = data["name"] as? String ?: "Chat with $otherUserId",
                                    lastMessage = data["lastMessage"] as? String,
                                    lastTimestamp = data["lastTimestamp"] as? Long ?: 0L,
                                    unreadCount = 0, // Will be calculated separately
                                    userId = userId,
                                    participants = participants,
                                    lastRead = lastRead
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing chat room", e)
                                null
                            }
                        }
                        trySend(rooms)
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    suspend fun getUnreadCountsForUser(userId: String): Map<String, Int> {
        return try {
            val rooms = database.collection(CHATROOMS_COLLECTION)
                .whereArrayContains("participants", userId)
                .get()
                .await()
                .documents

            rooms.associate { doc ->
                val roomId = doc.id
                val lastRead = doc.getLong("lastRead_$userId") ?: 0L
                val count = getUnreadCountForRoom(roomId, lastRead)
                roomId to count
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unread counts", e)
            emptyMap()
        }
    }

    private suspend fun getUnreadCountForRoom(roomId: String, lastRead: Long): Int {
        return try {
            val query = database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("messages")
                .whereGreaterThan("timestamp", lastRead)
                .get()
                .await()
            query.size()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unread count for room $roomId", e)
            0
        }
    }

    suspend fun updateLastReadTimestamp(roomId: String, userId: String, timestamp: Long) {
        try {
            val updateData = mapOf<String, Any>(
                "lastRead_$userId" to timestamp,
                "lastUpdated" to FieldValue.serverTimestamp()
            )

            database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update(updateData)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last read timestamp", e)
            throw e
        }
    }

    suspend fun createChatRoom(userId: String, otherUserId: String): String {
        val participants = listOf(userId, otherUserId).sorted()
        val roomId = participants.joinToString("_")

        val roomData = mapOf<String, Any>(
            "participants" to participants,
            "lastMessage" to "",
            "lastTimestamp" to System.currentTimeMillis(),
            "lastRead_$userId" to System.currentTimeMillis(),
            "lastRead_$otherUserId" to 0L,
            "createdAt" to FieldValue.serverTimestamp()
        )

        try {
            database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .set(roomData)
                .await()
            return roomId
        } catch (e: Exception) {
            Log.e(TAG, "Error creating chat room", e)
            throw e
        }
    }

    // Message Operations
    suspend fun sendMessage(roomId: String, message: ChatMessage): Result<Unit> {
        return try {
            // First send the message
            database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("messages")
                .document(message.id)
                .set(message.toFirestoreMap())
                .await()

            // Then update the chatroom's last message
            val updateData = mapOf<String, Any>(
                "lastMessage" to message.text,
                "lastTimestamp" to message.timestamp,
                "lastUpdated" to FieldValue.serverTimestamp()
            )

            database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update(updateData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            Result.failure(e)
        }
    }

    fun listenToMessages(roomId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = database.collection(CHATROOMS_COLLECTION)
            .document(roomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> {
                        Log.e(TAG, "Error listening to messages", error)
                        trySend(emptyList())
                    }
                    snapshot == null || snapshot.isEmpty -> {
                        trySend(emptyList())
                    }
                    else -> {
                        val messages = snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toObject(ChatMessage::class.java)?.copy(
                                    status = MessageStatus.valueOf(doc.getString("status") ?: "SENT"),
                                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing message", e)
                                null
                            }
                        }
                        trySend(messages)
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    // Local Cache Operations
    fun getCachedMessages(roomId: String): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesByRoom(roomId).map { entities ->
            entities.map { it.toChatMessage() }
        }
    }

    suspend fun cacheMessage(roomId: String, message: ChatMessage) {
        try {
            chatMessageDao.insertMessage(message.toEntity(roomId))
        } catch (e: Exception) {
            Log.e(TAG, "Error caching message", e)
        }
    }

    suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
        try {
            chatMessageDao.updateMessageStatus(messageId, status.name)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating message status", e)
        }
    }

    suspend fun getFailedMessages(roomId: String): List<ChatMessage> {
        return try {
            chatMessageDao.getFailedMessages(roomId).map { it.toChatMessage() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting failed messages", e)
            emptyList()
        }
    }

    suspend fun clearRoomCache(roomId: String) {
        try {
            chatMessageDao.clearRoomMessages(roomId)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing room cache", e)
        }
    }

    suspend fun syncMessageGaps(roomId: String) {
        try {
            val oldestTime = chatMessageDao.getOldestTimestamp(roomId) ?: return
            val newestTime = chatMessageDao.getNewestTimestamp(roomId) ?: return
            val syncThreshold = newestTime - 1000 // 1 second buffer

            val olderMessages = database.collection("$CHATROOMS_COLLECTION/$roomId/messages")
                .whereLessThan("timestamp", oldestTime)
                .get().await()

            val newerMessages = database.collection("$CHATROOMS_COLLECTION/$roomId/messages")
                .whereGreaterThan("timestamp", syncThreshold)
                .get().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing message gaps", e)
        }
    }

    private fun ChatMessage.toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "text" to text,
            "senderId" to senderId,
            "timestamp" to timestamp,
            "status" to status.name,
            "clientGeneratedId" to clientGeneratedId,
            "isSystemMessage" to isSystemMessage
        )
    }
}