package com.example.quickchat.data.repository

import android.util.Log
import com.example.quickchat.data.local.ChatRoomDao
import com.example.quickchat.data.model.ChatRoom
import com.example.quickchat.data.model.GroupMember
import com.example.quickchat.data.model.User
import com.google.firebase.firestore.DocumentChange
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "FirestoreChatRoomRepo"
private const val CHATROOMS_COLLECTION = "chatrooms"

class FirestoreChatRoomRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val chatRoomDao: ChatRoomDao
) : ChatRoomRepository {

    // In FirestoreChatRoomRepository.kt
    override fun getChatRooms(userId: String): Flow<List<ChatRoom>> {
        return chatRoomDao.getChatRooms(userId)
            .combine(getRemoteChatRooms(userId)) { local, remote ->
                remote.onEach { remoteRoom ->
                    // Ensure local unread counts are preserved
                    local.find { it.roomId == remoteRoom.roomId }?.let { localRoom ->
                        remoteRoom.unreadCount = localRoom.unreadCount
                    }
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
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore error", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val rooms = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            val room = createChatRoomFromDocument(doc, userId)
                            // Update local DB in the coroutine
                            chatRoomDao.insertAll(listOf(room))
                            room
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing room document", e)
                            null
                        }
                    } ?: emptyList()

                    trySend(rooms)
                }
            }

        awaitClose { listener.remove() }
    }

    fun createChatRoomFromDocument(
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
        val type = data["type"] as? String ?: "dm"

        // For groups, use the title field if available, otherwise use a default name
        val name = when (type) {
            "group" -> data["title"] as? String ?: "Group Chat"
            else -> data["name"] as? String ?: "Chat with $otherUserId"
        }

        return ChatRoom(
            roomId = doc.id,
            name = name,
            lastMessage = data["lastMessage"] as? String,
            lastTimestamp = data["lastTimestamp"] as? Long ?: 0L,
            unreadCount = unreadCount,
            userId = userId,
            participants = participants,
            lastRead = lastRead,
            isMuted = isMuted,
            fcmTokens = fcmTokens,
            type = type,
            createdBy = data["createdBy"] as? String ?: "",
            createdAt = (data["createdAt"] as? Long) ?: System.currentTimeMillis(),
            admins = data["admins"] as? List<String> ?: emptyList()
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


    override suspend fun syncRoomsWithFirestore(userId: String) {
        try {
            // Force a fresh fetch from Firestore
            val remoteRooms = firestore.collection(CHATROOMS_COLLECTION)
                .whereArrayContains("participants", userId)
                .get()
                .await()
                .documents
                .mapNotNull { doc -> createChatRoomFromDocument(doc, userId) }

            // Update local database
            chatRoomDao.insertAll(remoteRooms)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            throw e
        }
    }


    override suspend fun createChatRoom(user1: String, user2: String): Result<String> {
        if (user1.isEmpty() || user2.isEmpty()) {
            return Result.failure(IllegalArgumentException("User IDs cannot be empty"))
        }

        return try {
            val participants = listOf(user1, user2).sorted()
            val roomId = participants.joinToString("_")
            val timestamp = System.currentTimeMillis()

            val roomData = mapOf<String, Any>(
                "type" to "dm",
                "createdBy" to user1,
                "createdAt" to FieldValue.serverTimestamp(),
                "name" to "Chat between ${participants[0]} and ${participants[1]}",
                "lastMessage" to "",
                "lastTimestamp" to timestamp,
                "participants" to participants,
                "lastRead_$user1" to timestamp,
                "lastRead_$user2" to 0L,
                "unreadCount_$user1" to 0,
                "unreadCount_$user2" to 0,
                "fcmTokens" to mapOf<String, String>()
            )

            // Initialize typing status for both users
            val batch = firestore.batch()
            val roomRef = firestore.collection(CHATROOMS_COLLECTION).document(roomId)

            batch.set(roomRef, roomData)

            // Add typing status for both users
            participants.forEach { userId ->
                val typingStatusRef = roomRef
                    .collection("typingStatus")
                    .document(userId)

                batch.set(typingStatusRef, mapOf(
                    "isTyping" to false,
                    "userId" to userId,
                    "lastUpdated" to FieldValue.serverTimestamp()
                ))
            }

            batch.commit().await()

            val newRoom = ChatRoom(
                roomId = roomId,
                name = roomData["name"] as String,
                lastMessage = roomData["lastMessage"] as String,
                lastTimestamp = roomData["lastTimestamp"] as Long,
                unreadCount = 0,
                userId = user1,
                participants = participants,
                lastRead = roomData["lastRead_$user1"] as Long,
                fcmTokens = emptyMap(),
                type = "dm",
                createdBy = user1,
                createdAt = timestamp
            )

            chatRoomDao.insertAll(listOf(newRoom))

            Result.success(roomId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating chat room", e)
            Result.failure(e)
        }
    }

    override suspend fun getRoomDetails(roomId: String, currentUserId: String): ChatRoom? {
        return try {
            val doc = firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .get()
                .await()

            if (doc.exists()) {
                createChatRoomFromDocument(doc, currentUserId)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting room details", e)
            null
        }
    }

    override suspend fun createGroupChat(
        title: String,
        creatorId: String,
        members: List<String>
    ): Result<String> {
        if (creatorId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Creator ID cannot be empty"))
        }

        return try {
            val roomId = "group_${UUID.randomUUID()}"
            val allParticipants = (members + creatorId).distinct()
            val timestamp = System.currentTimeMillis()

            // Initialize typing status for all members
            val batch = firestore.batch()
            val roomRef = firestore.collection(CHATROOMS_COLLECTION).document(roomId)

            val roomData = mapOf<String, Any>(
                "title" to title,
                "name" to title,
                "type" to "group",
                "createdBy" to creatorId,
                "createdAt" to FieldValue.serverTimestamp(),
                "participants" to allParticipants,
                "admins" to listOf(creatorId),
                "fcmTokens" to mapOf<String, String>(),
                "lastMessage" to "",
                "lastTimestamp" to timestamp
            )

            batch.set(roomRef, roomData)

            // Initialize typing status for all participants
            allParticipants.forEach { userId ->
                val typingStatusRef = roomRef
                    .collection("typingStatus")
                    .document(userId)

                batch.set(typingStatusRef, mapOf(
                    "isTyping" to false,
                    "userId" to userId,
                    "lastUpdated" to FieldValue.serverTimestamp()
                ))
            }

            // Add members to subcollection
            allParticipants.forEach { userId ->
                val memberRef = roomRef.collection("members").document(userId)
                batch.set(memberRef, mapOf(
                    "role" to if (userId == creatorId) "admin" else "member",
                    "joinedAt" to FieldValue.serverTimestamp(),
                    "isMuted" to false
                ))
            }

            batch.commit().await()

            // Create local room object
            val newRoom = ChatRoom(
                roomId = roomId,
                name = title,
                lastMessage = "",
                lastTimestamp = timestamp,
                unreadCount = 0,
                userId = creatorId,
                participants = allParticipants,
                lastRead = timestamp,
                type = "group",
                createdBy = creatorId,
                createdAt = timestamp,
                admins = listOf(creatorId),
                fcmTokens = emptyMap()
            )

            chatRoomDao.insertAll(listOf(newRoom))

            Result.success(roomId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating group chat", e)
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
                .update(
                    mapOf(
                        "isArchived" to archive,
                        "lastUpdated" to FieldValue.serverTimestamp()
                    )
                )
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
                .update(
                    mapOf(
                        "isDeleted" to true,
                        "lastUpdated" to FieldValue.serverTimestamp()
                    )
                )
                .await()

            // Delete from local DB
            chatRoomDao.deleteRoom(roomId)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting room $roomId", e)
            throw e
        }
    }


    override suspend fun restoreRoom(roomId: String) {
        try {
            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update(
                    mapOf(
                        "isDeleted" to false,
                        "lastUpdated" to FieldValue.serverTimestamp()
                    )
                )
                .await()

        } catch (e: Exception) {
            Log.e(TAG, "Error restoring room $roomId", e)
            throw e
        }
    }

    override suspend fun incrementUnreadCount(roomId: String, userId: String) {
        try {
            // 1. Update Firestore atomically
            firestore.runTransaction { transaction ->
                val docRef = firestore.collection(CHATROOMS_COLLECTION).document(roomId)
                val currentCount =
                    (transaction.get(docRef).get("unreadCount_$userId") as? Long) ?: 0L
                transaction.update(docRef, "unreadCount_$userId", currentCount + 1)
                transaction.update(docRef, "lastUpdated", FieldValue.serverTimestamp())
            }.await()

            // 2. Update local DB
            chatRoomDao.getRoomById(roomId)?.let { room ->
                chatRoomDao.updateUnreadCount(roomId, userId, room.unreadCount + 1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing unread count", e)
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
                chatRoomDao.insertAll(
                    listOf(
                        room.copy(
                            unreadCount = 0,
                            lastRead = timestamp
                        )
                    )
                )
                Log.d("UNREAD_DEBUG", " Local DB updated successfully")
            } ?: run {
                Log.e("UNREAD_DEBUG", " Room not found in local DB!")
            }
        } catch (e: Exception) {
            Log.e("UNREAD_DEBUG", " Error in markMessagesAsRead: ${e.message}", e)
            throw e
        }
    }

    override fun getUnreadCountFlow(roomId: String, userId: String): Flow<Int> {
        return chatRoomDao.getUnreadCountFlow(roomId, userId)
    }

    fun listenForNewMessages(
        userId: String,
        onNewMessage: (roomId: String, message: String) -> Unit
    ): ListenerRegistration {
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

    override suspend fun generateInviteLink(roomId: String, creatorId: String): String {
        val room = firestore.collection(CHATROOMS_COLLECTION)
            .document(roomId)
            .get()
            .await()

        val admins = room.get("admins") as? List<String> ?: emptyList()
        if (!admins.contains(creatorId)) {
            throw Exception("Only admins can generate invites")
        }


        val token = UUID.randomUUID().toString()
        val expiresAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7) // 1 week expiry
        firestore.collection("chatroom_invites")
            .document(roomId)
            .set(
                mapOf(
                    "token" to token,
                    "expiresAt" to expiresAt,
                    "creatorId" to creatorId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

        return "https://yourapp.com/join/$roomId?token=$token"
    }

    override suspend fun joinChatroomViaLink(
        roomId: String,
        token: String,
        userId: String
    ): Boolean {
        return try {
            val invite = firestore.collection("chatroom_invites")
                .document(roomId)
                .get()
                .await()

            if (invite.getString("token") != token ||
                invite.getLong("expiresAt")!! < System.currentTimeMillis()
            ) {
                return false
            }

            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update("participants", FieldValue.arrayUnion(userId))
                .await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error joining chatroom via link", e)
            false
        }
    }

    override suspend fun revokeInviteLink(roomId: String) {
        firestore.collection("chatroom_invites")
            .document(roomId)
            .delete()
            .await()
    }


    override fun getGroupMembers(roomId: String): Flow<List<GroupMember>> = callbackFlow {
        Log.d("REPO_DEBUG", "Setting up real-time listener for room: $roomId")

        val membersRef = firestore.collection(CHATROOMS_COLLECTION)
            .document(roomId)
            .collection("members")

        val listener = membersRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("REPO_ERROR", "Error listening to members", error)
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                Log.d("REPO_DEBUG", "Snapshot received with ${snapshot.documents.size} member documents")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val members = snapshot.documents.mapNotNull { doc ->
                            try {
                                val data = doc.data ?: emptyMap()
                                Log.d("REPO_DEBUG", "Member doc data: $data")

                                // Get user details from devices collection
                                val userDoc = firestore.collection("devices")
                                    .document(doc.id)
                                    .get()
                                    .await()

                                val userName = userDoc.getString("userName") ?: "Unknown User"

                                GroupMember(
                                    userId = doc.id,
                                    userName = userName,
                                    role = data["role"] as? String ?: "member",
                                    joinedAt = data["joinedAt"] as? Long ?: System.currentTimeMillis(),
                                    isOnline = false,
                                    isMuted = data["isMuted"] as? Boolean ?: false
                                ).also {
                                    Log.d("REPO_DEBUG", "Mapped member: ${it.userName} (${it.userId})")
                                }
                            } catch (e: Exception) {
                                Log.e("REPO_ERROR", "Error mapping member doc ${doc.id}", e)
                                null
                            }
                        }

                        Log.d("REPO_DEBUG", "Sending ${members.size} members to UI")
                        trySend(members).isSuccess
                    } catch (e: Exception) {
                        Log.e("REPO_ERROR", "Error processing members", e)
                    }
                }
            } else if (snapshot != null && snapshot.isEmpty) {
                Log.d("REPO_DEBUG", "No members found in room")
                trySend(emptyList()).isSuccess
            }
        }

        awaitClose {
            Log.d("REPO_DEBUG", "Removing listener for room: $roomId")
            listener.remove()
        }
    }


    override suspend fun getAvailableUsersToAdd(roomId: String): List<User> {
        return try {
            val documents = firestore.collection("devices")
                .get()
                .await()
                .documents

            Log.d("REPO_DEBUG", "Total documents fetched: ${documents.size}")

            documents.mapNotNull { doc ->
                try {
                    val userName = doc.getString("userName") ?: ""
                    val deviceId = doc.id

                    Log.d("REPO_DEBUG", "Processing doc: $deviceId")
                    Log.d("REPO_DEBUG", "Raw userName field: '${doc.getString("userName")}'")
                    Log.d("REPO_DEBUG", "All fields: ${doc.data}")

                    if (userName.isBlank()) {
                        Log.w("REPO_DEBUG", "Empty userName for device: $deviceId")
                        return@mapNotNull null
                    }

                    User(
                        deviceId = deviceId,
                        userName = userName,
                        fcmToken = doc.getString("fcmToken"),
                        lastUpdated = doc.getDate("lastUpdated")
                    ).also {
                        Log.d("REPO_DEBUG", "Successfully mapped user: ${it.userName}")
                    }
                } catch (e: Exception) {
                    Log.e("REPO_ERROR", "Error mapping doc ${doc.id}: ${e.message}", e)
                    null
                }
            }.also { users ->
                Log.d("REPO_DEBUG", "Final users list size: ${users.size}")
                users.forEach { user ->
                    Log.d("REPO_DEBUG", "Final user: ${user.deviceId} -> '${user.userName}'")
                }
            }
        } catch (e: Exception) {
            Log.e("REPO_ERROR", "Error fetching users: ${e.message}", e)
            emptyList()
        }
    }


    override suspend fun addMemberToGroup(roomId: String, userId: String) {
        try {
            Log.d("REPO_DEBUG", "Adding user $userId to room $roomId")

            // Get user details
            val userDoc = firestore.collection("devices")
                .document(userId)
                .get()
                .await()

            val userName = userDoc.getString("userName") ?: "Unknown User"

            // Add member with userName stored directly
            val memberData = hashMapOf(
                "userId" to userId,
                "userName" to userName,
                "role" to "member",
                "joinedAt" to System.currentTimeMillis(),
                "isMuted" to false
            )

            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("members")
                .document(userId)
                .set(memberData)
                .await()

            Log.d("REPO_DEBUG", "User $userName added successfully to room $roomId")

        } catch (e: Exception) {
            Log.e("REPO_ERROR", "Error adding member to group", e)
            throw e
        }
    }

    override suspend fun removeMemberFromGroup(roomId: String, userId: String) {
        try {
            // Remove from participants list
            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update("participants", FieldValue.arrayRemove(userId))
                .await()

            // Remove from members subcollection
            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("members")
                .document(userId)
                .delete()
                .await()

            Log.d(TAG, "Removed user $userId from room $roomId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing member from group", e)
            throw e
        }
    }


    override suspend fun changeMemberRole(roomId: String, userId: String, newRole: String) {
        try {
            // Get member details from the members subcollection
            val memberDoc = firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("members")
                .document(userId)
                .get()
                .await()

            if (memberDoc.exists()) {
                val name = memberDoc.getString("name") ?: ""
                val joinedAt = memberDoc.getLong("joinedAt") ?: System.currentTimeMillis()

                // Update the role directly in the subcollection
                firestore.collection(CHATROOMS_COLLECTION)
                    .document(roomId)
                    .collection("members")
                    .document(userId)
                    .update("role", newRole)
                    .await()

                Log.d(TAG, "Changed role of user $userId to $newRole in room $roomId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error changing member role", e)
            throw e
        }
    }


    override suspend fun leaveGroup(roomId: String, userId: String) {
        try {
            removeMemberFromGroup(roomId, userId)
            val roomDoc = firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .get()
                .await()
            val participants = roomDoc.get("participants") as? List<String> ?: emptyList()
            if (participants.size == 1 && participants[0] == userId) {
                deleteRoom(roomId)
            }
        } catch (e: Exception) {
            Log.e(com.example.quickchat.data.repository.TAG, "Error leaving group", e)
            throw e
        }


    }


    override suspend fun transferOwnership(
        roomId: String,
        currentAdminId: String,
        newAdminId: String
    ) {
        try {
            changeMemberRole(roomId, currentAdminId, "member")
            changeMemberRole(roomId, newAdminId, "admin")

            firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update(
                    mapOf(
                        "createdBy" to newAdminId,
                        "admins" to FieldValue.arrayUnion(newAdminId),
                        "lastUpdated" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error transferring ownership", e)
            throw e
        }


    }

    companion object {
        private const val TAG = "FirestoreChatRoomRepo"
    }

}
