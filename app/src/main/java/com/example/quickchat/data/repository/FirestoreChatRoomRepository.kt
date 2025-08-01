package com.example.quickchat.data.repository

import android.util.Log
import com.example.quickchat.data.local.ChatRoomDao
import com.example.quickchat.data.model.ChatRoom
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
        return combine(
            getRemoteChatRooms(userId),
            chatRoomDao.getChatRooms(userId).distinctUntilChanged()
        ) { remoteRooms, localRooms ->
            if (remoteRooms.isNotEmpty()) remoteRooms else localRooms
        }
    }

    private fun getRemoteChatRooms(userId: String): Flow<List<ChatRoom>> = callbackFlow {
        val listener = firestore.collection(CHATROOMS_COLLECTION)
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->

                if (error != null || snapshot == null) {
                    Log.e(TAG, "Error listening to chat rooms", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val rooms = snapshot.documents.mapNotNull { doc ->
                        try {
                            createChatRoomFromDocument(doc, userId)
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

        return ChatRoom(
            roomId = doc.id,
            name = data["name"] as? String ?: "Chat with $otherUserId",
            lastMessage = data["lastMessage"] as? String,
            lastTimestamp = data["lastTimestamp"] as? Long ?: 0L,
            unreadCount = getUnreadCountForRoom(doc.id, userId, lastRead).toInt(),
            userId = userId,
            participants = participants,
            lastRead = lastRead
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
                "createdAt" to FieldValue.serverTimestamp()
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
                lastRead = roomData["lastRead_$user1"] as Long
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
