package com.example.quickchat.data.repository

import android.util.Log
import com.example.quickchat.data.local.ChatRoomDao
import com.example.quickchat.data.model.ChatRoom
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val TAG = "FirestoreChatRoomRepo"
private const val CHATROOMS_COLLECTION = "chatrooms"

class FirestoreChatRoomRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val chatRoomDao: ChatRoomDao
) : ChatRoomRepository {

    override fun getChatRooms(userId: String): Flow<List<ChatRoom>> {
        return callbackFlow {
            // First try to get from local cache immediately
            launch {
                chatRoomDao.getChatRooms(userId).collect { localRooms ->
                    val filteredLocal = localRooms.filter { !it.isArchived }
                    if (filteredLocal.isNotEmpty()) {
                        trySend(filteredLocal)
                    }
                }
            }
            // Then get from remote and update
            getRemoteChatRooms(userId).collect { remoteRooms ->
                val filteredRemote = remoteRooms.filter { !it.isArchived }
                trySend(filteredRemote)
            }
        }
    }

     suspend fun addFcmTokenToRoom(roomId: String, userId: String, token: String) {
        try {
            val updateMap = mapOf(
                "fcmTokens.$userId" to token,
                "lastUpdated" to FieldValue.serverTimestamp()
            )

            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update(updateMap)
                .await()

            Log.d(TAG, "Successfully added FCM token for user $userId in room $roomId")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding FCM token to room", e)
            throw e
        }
    }

    /**
     * Removes a user's FCM token from the chatroom
     */
     suspend fun removeFcmTokenFromRoom(roomId: String, userId: String) {
        try {
            val updateMap = mapOf(
                "fcmTokens.$userId" to FieldValue.delete(),
                "lastUpdated" to FieldValue.serverTimestamp()
            )

            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update(updateMap)
                .await()

            Log.d(TAG, "Successfully removed FCM token for user $userId in room $roomId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing FCM token from room", e)
            throw e
        }
    }
    /**
     * Gets all FCM tokens for participants in a room (except the current user)
     */
    suspend fun getOtherParticipantsFcmTokens(roomId: String, currentUserId: String): List<String> {
        return try {
            val document = firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .get()
                .await()

            val fcmTokens = document.get("fcmTokens") as? Map<String, String> ?: emptyMap()

            fcmTokens.filterKeys { it != currentUserId }.values.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FCM tokens for room $roomId", e)
            emptyList()
        }
    }

    private fun getRemoteChatRooms(userId: String): Flow<List<ChatRoom>> = callbackFlow {
        val listener = firestore.collection(CHATROOMS_COLLECTION)
            .whereArrayContains("participants", userId)
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.e(TAG, "Error listening to chat rooms", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val rooms = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: emptyMap()
                            val participants = data["participants"] as? List<String> ?: emptyList()

                            // Ensure the current user is actually in participants
                            if (participants.contains(userId)) {
                                createChatRoomFromDocument(doc, userId)
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing chat room document", e)
                            null
                        }
                    }

                    try {
                        chatRoomDao.insertAll(rooms)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating local cache", e)
                    }

                    trySend(rooms)
                }
            }

        awaitClose { (listener as ListenerRegistration).remove() }
    }

    private suspend fun createChatRoomFromDocument(
        doc: DocumentSnapshot,
        userId: String
    ): ChatRoom {
        val data = doc.data ?: emptyMap()
        val participants = data["participants"] as? List<String> ?: emptyList()
        val otherUserId = participants.firstOrNull { it != userId } ?: ""
        val lastRead = data["lastRead_$userId"] as? Long ?: 0L
        val unreadCount = (data["unreadCount_$userId"] as? Number)?.toInt() ?: 0
        val isMuted = data["isMuted"] as? Boolean ?: false
        val fcmTokens = data["fcmTokens"] as? Map<String, String> ?: emptyMap()
        return ChatRoom(
            roomId = doc.id,
            name = data["name"] as? String ?: "Chat with $otherUserId",
            lastMessage = data["lastMessage"] as? String,
            lastTimestamp = data["lastTimestamp"] as? Long ?: 0L,
            unreadCount = unreadCount,
            userId = userId,
            participants = participants,
            lastRead = lastRead,
            isMuted = isMuted,
            fcmTokens = fcmTokens
        )
    }

    override suspend fun updateLastReadTimestamp(
        roomId: String,
        userId: String,
        timestamp: Long
    ) {
        try {
            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update(
                    mapOf(
                        "lastRead_$userId" to timestamp,
                        "lastUpdated" to FieldValue.serverTimestamp()
                    )
                )
                .await()

            chatRoomDao.getRoomById(roomId)?.let { room ->
                chatRoomDao.insertAll(listOf(room.copy(lastRead = timestamp)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last read timestamp", e)
            throw e
        }
    }

    override suspend fun createChatRoom(user1: String, user2: String): Result<String> {
        return try {
            val participants = listOf(user1, user2).sorted()
            val roomId = participants.joinToString("_")
            val timestamp = System.currentTimeMillis()

            val roomData = mapOf<String, Any>(
                "name" to "Chat between ${participants[0]} and ${participants[1]}",
                "lastMessage" to "",
                "lastTimestamp" to timestamp,
                "participants" to participants,
                "lastRead_$user1" to timestamp,
                "lastRead_$user2" to 0L,
                "unreadCount_$user1" to 0,
                "unreadCount_$user2" to 0,
                "createdAt" to FieldValue.serverTimestamp(),
                "fcmTokens" to mapOf<String, String>() // Initialize empty FCM tokens map

            )

            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .set(roomData)
                .await()

            val newRoom = ChatRoom(
                roomId = roomId,
                name = roomData["name"] as String,
                lastMessage = roomData["lastMessage"] as String,
                lastTimestamp = roomData["lastTimestamp"] as Long,
                unreadCount = 0,
                userId = user1,
                participants = participants,
                lastRead = roomData["lastRead_$user1"] as Long,
                fcmTokens = emptyMap() // Initialize empty FCM tokens map

            )

            chatRoomDao.insertAll(listOf(newRoom))

            Result.success(roomId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating chat room", e)
            Result.failure(e)
        }
    }

    override suspend fun doesRoomExist(roomId: String): Boolean {
        return try {
            chatRoomDao.getRoomById(roomId) != null ||
                    firestore.collection(CHATROOMS_COLLECTION)
                        .document(roomId)
                        .get()
                        .await()
                        .exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if room exists", e)
            false
        }
    }

    override suspend fun archiveRoom(roomId: String, archive: Boolean) {
        try {
            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update(mapOf(
                    "isArchived" to archive,
                    "lastUpdated" to FieldValue.serverTimestamp()
                ))
                .await()

            chatRoomDao.getRoomById(roomId)?.let { room ->
                chatRoomDao.insertAll(listOf(room.copy(isArchived = archive)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error archiving room", e)
            throw e
        }
    }

    override suspend fun deleteRoom(roomId: String) {
        try {
            // Mark as deleted in Firestore
            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update("isDeleted", true)
                .await()

            // Delete from local DB
            chatRoomDao.deleteRoom(roomId)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting room", e)
            throw e
        }
    }
    override suspend fun restoreRoom(roomId: String) {
        try {
            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update(mapOf(
                    "isDeleted" to false,
                    "lastUpdated" to FieldValue.serverTimestamp()
                ))
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring room", e)
            throw e
        }
    }

    override suspend fun incrementUnreadCount(roomId: String, userId: String) {
        try {
            Log.d("UNREAD_DEBUG", "⏩ Starting incrementUnreadCount for room $roomId, user $userId")

            // 1. Update Firestore
            Log.d("UNREAD_DEBUG", " Updating Firestore...")
            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update("unreadCount_$userId", FieldValue.increment(1))
                .await()
            Log.d("UNREAD_DEBUG", "✅ Firestore updated successfully")

            // 2. Update local database
            Log.d("UNREAD_DEBUG", "💾 Updating local DB...")
            chatRoomDao.getRoomById(roomId)?.let { room ->
                val newCount = (room.unreadCount ?: 0) + 1
                Log.d("UNREAD_DEBUG", " New count will be: $newCount")
                chatRoomDao.insertAll(listOf(room.copy(unreadCount = newCount)))
                Log.d("UNREAD_DEBUG", " Local DB updated successfully")
            } ?: run {
                Log.e("UNREAD_DEBUG", " Room not found in local DB!")
            }
        } catch (e: Exception) {
            Log.e("UNREAD_DEBUG", " Error in incrementUnreadCount: ${e.message}", e)
            throw e
        }
    }
    override suspend fun markMessagesAsRead(roomId: String, userId: String) {
        try {
            Log.d("UNREAD_DEBUG", "⏩ Starting markMessagesAsRead for room $roomId, user $userId")
            val timestamp = System.currentTimeMillis()

            // 1. Update Firestore
            Log.d("UNREAD_DEBUG", " Updating Firestore...")
            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update(
                    mapOf(
                        "unreadCount_$userId" to 0,
                        "lastRead_$userId" to timestamp
                    )
                )
                .await()
            Log.d("UNREAD_DEBUG", " Firestore updated successfully")

            // 2. Update local database
            Log.d("UNREAD_DEBUG", " Updating local DB...")
            chatRoomDao.getRoomById(roomId)?.let { room ->
                Log.d("UNREAD_DEBUG", " Resetting unread count to 0")
                chatRoomDao.insertAll(listOf(room.copy(
                    unreadCount = 0,
                    lastRead = timestamp
                )))
                Log.d("UNREAD_DEBUG", " Local DB updated successfully")
            } ?: run {
                Log.e("UNREAD_DEBUG", " Room not found in local DB!")
            }
        } catch (e: Exception) {
            Log.e("UNREAD_DEBUG", " Error in markMessagesAsRead: ${e.message}", e)
            throw e
        }
    }    override fun getUnreadCountFlow(roomId: String, userId: String): Flow<Int> {
        return chatRoomDao.getUnreadCountFlow(roomId, userId)
    }
    // Add this to your FirestoreChatRoomRepository
    fun listenForNewMessages(userId: String, onNewMessage: (roomId: String, message: String) -> Unit): ListenerRegistration {
        return firestore.collection(CHATROOMS_COLLECTION)
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for new messages", error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.MODIFIED) {
                        val roomId = change.document.id
                        val lastMessage = change.document.getString("lastMessage") ?: ""
                        val lastTimestamp = change.document.getLong("lastTimestamp") ?: 0L

                        // Only notify if there's a new message and it's not from the current user
                        if (lastMessage.isNotEmpty() && lastTimestamp > System.currentTimeMillis() - 5000) {
                            onNewMessage(roomId, lastMessage)
                        }
                    }
                }
            }
    }
    override suspend fun toggleMuteStatus(roomId: String, mute: Boolean) {
        try {
            firestore.runTransaction { transaction ->
                val docRef = firestore.collection(CHATROOMS_COLLECTION).document(roomId)
                transaction.update(docRef, "isMuted", mute)
                transaction.update(docRef, "lastUpdated", FieldValue.serverTimestamp())
            }.await()

            chatRoomDao.getRoomById(roomId)?.let { room ->
                chatRoomDao.insertAll(listOf(room.copy(isMuted = mute)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating mute status", e)
            throw e
        }
    }

    private suspend fun getUnreadCountForRoom(
        roomId: String,
        userId: String,
        lastRead: Long
    ): Long {
        return try {
            val messagesRef = firestore.collection("$CHATROOMS_COLLECTION/$roomId/messages")
            val query = if (lastRead <= 0) {
                messagesRef.get().await()
            } else {
                messagesRef.whereGreaterThan("timestamp", lastRead).get().await()
            }
            query.size().toLong()
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating unread count for room $roomId", e)
            0L
        }
    }



}