package com.example.quickchat.data.repository

import android.os.Build
import android.util.Log
import com.example.quickchat.data.local.ChatMessageDao
import com.example.quickchat.data.local.toChatMessage
import com.example.quickchat.data.local.toEntity
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.model.ChatRoom
import com.example.quickchat.data.model.MessageStatus
import com.example.quickchat.data.model.MessageType
import com.google.firebase.firestore.BuildConfig
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import javax.inject.Inject

private const val CHATROOMS_COLLECTION = "chatrooms"
private val USERS_COLLECTION = "users"
private const val DEVICES_COLLECTION = "devices"


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

    //




    /**
     * Stores the FCM token for a user in Firestore with additional metadata
     */
    suspend fun storeFcmToken(deviceId: String, token: String): Boolean {
        return try {
            Log.d("FCM_DEBUG", "Attempting to store token for device: $deviceId")

            val tokenData = hashMapOf(
                "fcmToken" to token,
                "lastUpdated" to FieldValue.serverTimestamp()
            )

            database.collection("devices")
                .document(deviceId)
                .set(tokenData)
                .addOnSuccessListener {
                    Log.d("FCM_DEBUG", "Token successfully stored in Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e("FCM_DEBUG", "Firestore write failed", e)
                }
                .await()

            true
        } catch (e: Exception) {
            Log.e("FCM_DEBUG", "storeFcmToken error", e)
            false
        }
    }    /**
     * Retrieves the FCM token for a user from Firestore
     */
    suspend fun getFcmToken(deviceId: String): String? {
        return try {
            val document = database.collection(DEVICES_COLLECTION)
                .document(deviceId)
                .get()
                .await()

            document.getString("fcmToken")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FCM token", e)
            null
        }
    }
    /**
     * Deletes the FCM token when user logs out
     */
    suspend fun deleteFcmToken(userId: String): Boolean {
        return try {
            val updates = hashMapOf<String, Any>(
                "fcmToken" to FieldValue.delete(),
                "lastUpdated" to FieldValue.serverTimestamp()
            )

            database.collection(USERS_COLLECTION)
                .document(userId)
                .update(updates)
                .await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete FCM token", e)
            false
        }
    }

    /* FCM Token Handling */
    suspend fun registerAndVerifyFcmToken(userId: String): Boolean {
        return try {
            // Step 1: Get FCM token
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "FCM Token retrieved: $token")

            // Step 2: Save to Firestore with additional metadata
            val tokenData = hashMapOf(
                "fcmToken" to token,
                "deviceInfo" to Build.MODEL,
                "lastUpdated" to FieldValue.serverTimestamp(),
                "appVersion" to BuildConfig.VERSION_NAME
            )

            database.collection("users")
                .document(userId)
                .set(tokenData, SetOptions.merge())
                .await()

            // Step 3: Verify the token was saved
            verifyFcmToken(userId)
        } catch (e: Exception) {
            Log.e(TAG, "FCM Token registration failed", e)
            false
        }
    }

    suspend fun verifyFcmToken(userId: String): Boolean {
        return try {
            val document = database.collection("users")
                .document(userId)
                .get()
                .await()

            val token = document.getString("fcmToken")
            if (token.isNullOrEmpty()) {
                Log.w(TAG, "FCM Token is empty for user $userId")
                return false
            }

            // Additional verification - check token format
            token.matches(Regex("[a-zA-Z0-9_-]{152}")).also { isValid ->
                if (!isValid) {
                    Log.e(TAG, "Invalid FCM token format for user $userId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying FCM token", e)
            false
        }
    }

    suspend fun registerFcmToken(userId: String) {
        try {
            Log.d(TAG, "Starting FCM token registration for user: $userId")

            // Fetch FCM token
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "FCM Token retrieved: $token")

            // Save to Firestore
            database.collection("users")
                .document(userId)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Firestore: Token saved successfully!")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore: Failed to save token", e)
                }
                .await() // Wait for Firestore write

            Log.d(TAG, "FCM token process completed for $userId")
        } catch (e: Exception) {
            Log.e(TAG, "FCM Token Error: ${e.message}", e)
        }
    }

    /* Notification Sending */
    suspend fun sendNotification(
        roomId: String,
        senderId: String,
        messageText: String,
        recipientId: String
    ) {
        try {
            // Get recipient's token and sender's name
            val (recipientToken, senderName) = getNotificationDetails(senderId, recipientId)
                ?: return

            // Prepare notification payload
            val message = createNotificationPayload(
                token = recipientToken,
                title = senderName,
                message = messageText,
                roomId = roomId,
                senderId = senderId
            )

            // Send via Firestore (for testing) or your server
            sendNotificationToFirestore(message)

        } catch (e: Exception) {
            Log.e(TAG, "Notification sending failed", e)
        }
    }

    private suspend fun getNotificationDetails(
        senderId: String,
        recipientId: String
    ): Pair<String, String>? {
        val senderData = database.collection("users")
            .document(senderId)
            .get()
            .await()
            .data ?: run {
            Log.w(TAG, "Sender data not found")
            return null
        }

        val recipientData = database.collection("users")
            .document(recipientId)
            .get()
            .await()
            .data ?: run {
            Log.w(TAG, "Recipient data not found")
            return null
        }

        val recipientToken = recipientData["fcmToken"] as? String ?: run {
            Log.w(TAG, "Recipient token not found")
            return null
        }

        val senderName = senderData["name"] as? String ?: senderId
        return recipientToken to senderName
    }


    private fun createNotificationPayload(
        token: String,
        title: String,
        message: String,
        roomId: String,
        senderId: String
    ): Map<String, Any> {
        return mapOf(
            "to" to token,
            "priority" to "high",
            "data" to mapOf(
                "title" to title,
                "message" to message,
                "roomId" to roomId,
                "senderId" to senderId,
                "type" to "chat_message",
                "timestamp" to System.currentTimeMillis()
            ),
            "notification" to mapOf(
                "title" to title,
                "body" to message,
                "sound" to "default",
                "click_action" to "FLUTTER_NOTIFICATION_CLICK"
            )
        )
    }

    private suspend fun sendNotificationToFirestore(message: Map<String, Any>) {
        database.collection("notification_logs") // Separate collection for tracking
            .add(message)
            .addOnSuccessListener {
                Log.d(TAG, "Notification logged successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to log notification", e)
            }
            .await()
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
                                    lastRead = lastRead,
                                    isMuted = data["isMuted"] as? Boolean ?: false
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

    suspend fun getUnreadCountForRoom(roomId: String, userId: String): Int {
        return try {
            val lastRead = database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .get()
                .await()
                .getLong("lastRead_$userId") ?: 0L

            val query = database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("messages")
                .whereGreaterThan("timestamp", lastRead)
                .get()
                .await()

            query.size()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unread count", e)
            0
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
            // 1. Cache the message locally
            cacheMessage(roomId, message)

            // 2. Send to Firestore
            database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("messages")
                .document(message.id)
                .set(message.toFirestoreMap())
                .await()

            // 3. Update chatroom's last message
            val updateData = mapOf<String, Any>(
                "lastMessage" to message.text.take(50), // Preview
                "lastTimestamp" to message.timestamp,
                "lastUpdated" to FieldValue.serverTimestamp()
            )

            database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update(updateData)
                .await()

            // 4. Get the other participant's ID and FCM token
            val roomDoc = database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .get()
                .await()

            val participants = roomDoc.get("participants") as? List<String> ?: emptyList()
            val otherUserId = participants.firstOrNull { it != message.senderId }
                ?: return Result.success(Unit)

            // 5. Get sender's name for notification
            val senderName = database.collection("users")
                .document(message.senderId)
                .get()
                .await()
                .getString("name") ?: "Someone"

            // 6. Send notification
            suspend fun sendPushNotification(
                roomId: String,
                senderDeviceId: String,
                messageText: String,
                recipientDeviceId: String
            ) {
                try {
                    // 1. Get recipient's FCM token
                    val recipientToken = getFcmToken(recipientDeviceId) ?: run {
                        Log.d(TAG, "No FCM token for recipient device")
                        return
                    }

                    // 2. Get sender device info for notification
                    val senderDoc = database.collection(DEVICES_COLLECTION)
                        .document(senderDeviceId)
                        .get()
                        .await()

                    val senderName = "Device ${senderDeviceId.takeLast(4)}"

                    // 3. Prepare notification data
                    val data = mapOf(
                        "type" to "chat_message",
                        "roomId" to roomId,
                        "senderDeviceId" to senderDeviceId,
                        "messagePreview" to messageText.take(30),
                        "timestamp" to System.currentTimeMillis().toString()
                    )

                    // 4. Prepare notification payload
                    val message = mapOf(
                        "to" to recipientToken,
                        "data" to data,
                        "notification" to mapOf(
                            "title" to senderName,
                            "body" to messageText.take(100),
                            "sound" to "default"
                        ),
                        "android" to mapOf(
                            "priority" to "high"
                        )
                    )

                    // 5. Log the notification (for debugging)
                    database.collection("notification_logs")
                        .add(message)
                        .await()

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send notification", e)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            Result.failure(e)
        }
    }

    private suspend fun sendPushNotification(
        roomId: String,
        senderId: String,
        messageText: String,
        recipientId: String
    ) {
        try {
            // 1. Get recipient's FCM token from devices collection
            val recipientToken = database.collection("devices")
                .document(recipientId)
                .get()
                .await()
                .getString("fcmToken") ?: run {
                Log.d(TAG, "No FCM token for recipient $recipientId")
                return
            }

            Log.d(TAG, "Sending to token: $recipientToken")

            // 2. Get sender's name
            val senderName = database.collection("users")
                .document(senderId)
                .get()
                .await()
                .getString("name") ?: "Someone"

            // 3. Prepare FCM payload
            val message = mapOf(
                "to" to recipientToken,
                "priority" to "high",
                "data" to mapOf(
                    "type" to "chat_message",
                    "title" to senderName,
                    "message" to messageText,
                    "roomId" to roomId,
                    "senderId" to senderId,
                    "timestamp" to System.currentTimeMillis()
                ),
                "notification" to mapOf(
                    "title" to "New message from $senderName",
                    "body" to messageText.take(100),
                    "sound" to "default",
                    "click_action" to "FLUTTER_NOTIFICATION_CLICK"
                )
            )

            // 4. Send via FCM API
            val fcmApi = Retrofit.Builder()
                .baseUrl("https://fcm.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FcmApi::class.java)

            val response = fcmApi.sendMessage(
                "key=YOUR_SERVER_KEY", // Get from Firebase Console
                message
            )

            if (response.isSuccessful) {
                Log.d(TAG, "Notification sent successfully")
            } else {
                Log.e(TAG, "Failed to send notification: ${response.errorBody()?.string()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification", e)
        }
    }

    interface FcmApi {
        @POST("fcm/send")
        suspend fun sendMessage(
            @Header("Authorization") authorization: String,
            @Body message: Map<String, Any>
        ): Response<Unit>
    }

   /* suspend fun updateUserName(userId: String, name: String) {
        try {
            val userData = hashMapOf(
                "name" to name,
                "lastUpdated" to FieldValue.serverTimestamp()
            )

            database.collection("users")
                .document(userId)
                .set(userData, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user name", e)
            throw e
        }
    }*/

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
                                val data = doc.data ?: emptyMap()
                                ChatMessage(
                                    id = doc.id,
                                    text = data["text"] as? String ?: "",
                                    senderId = data["senderId"] as? String ?: "",
                                    imageUrl = data["imageUrl"] as? String,
                                    messageType = MessageType.valueOf(
                                        data["messageType"] as? String ?: "TEXT"
                                    ),
                                    timestamp = data["timestamp"] as? Long ?: System.currentTimeMillis(),
                                    isSystemMessage = data["isSystemMessage"] as? Boolean ?: false,
                                    clientGeneratedId = data["clientGeneratedId"] as? String ?: "",
                                    isRead = data["isRead"] as? Boolean ?: false,
                                    status = MessageStatus.valueOf(
                                        data["status"] as? String ?: "SENDING"
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing message ${doc.id}", e)
                                null
                            }
                        }
                        // Launch caching in IO dispatcher
                        CoroutineScope(Dispatchers.IO).launch {
                            messages.forEach {
                                try {
                                    cacheMessage(roomId, it)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error caching received message", e)
                                }
                            }
                        }
                        trySend(messages)
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    // Local Cache Operations
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

    // Add this to ChatRepository.kt
    class ChatRepository @Inject constructor(
        private val database: FirebaseFirestore,
        private val chatMessageDao: ChatMessageDao
    ) {
        // ... other existing code ...

        suspend fun toggleMuteStatus(roomId: String, mute: Boolean) {
            try {
                database.collection(CHATROOMS_COLLECTION)
                    .document(roomId)
                    .update("isMuted", mute)
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling mute status", e)
                throw e
            }
        }
    }
    suspend fun sendChatNotification(
        roomId: String,
        senderId: String,
        message: String,
        recipientId: String
    ): Boolean {
        return try {
            val recipientToken = getFcmToken(recipientId) ?: return false
            val senderName = getUserName(senderId) ?: senderId

            val payload = mapOf(
                "to" to recipientToken,
                "data" to mapOf(
                    "type" to "chat_message",
                    "title" to senderName,
                    "message" to message,
                    "roomId" to roomId,
                    "senderId" to senderId
                ),
                "notification" to mapOf(
                    "title" to "New message from $senderName",
                    "body" to message.take(100)
                )
            )

            // In production, send to your backend instead
            database.collection("notification_requests").add(payload).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Notification failed", e)
            false
        }
    }

    private suspend fun getUserName(userId: String): String? {
        return database.collection("users")
            .document(userId)
            .get()
            .await()
            .getString("name")
    }

    suspend fun debugCache(roomId: String) {
        try {
            val cached = chatMessageDao.getMessagesByRoom(roomId).first()
            Log.d(TAG, "Cached messages count: ${cached.size}")
            cached.forEach {
                Log.d(TAG, "Cached message: $it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error debugging cache", e)
        }
    }





    // Add these presence-related functions
    fun observeUserPresence(userId: String): Flow<Boolean> = callbackFlow {
        val presenceRef = database.collection("presence").document(userId)
        val listener = presenceRef.addSnapshotListener { snapshot, _ ->
            val isOnline = snapshot?.getBoolean("isOnline") ?: false
            trySend(isOnline)
        }
        awaitClose { listener.remove() }
    }

    suspend fun updateUserPresence(userId: String, isOnline: Boolean) {
        try {
            database.collection("presence")
                .document(userId)
                .set(
                    mapOf(
                        "isOnline" to isOnline,
                        "lastUpdated" to FieldValue.serverTimestamp(),
                        "lastSeen" to if (!isOnline) System.currentTimeMillis() else null
                    ),
                    SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating presence", e)
        }
    }




        fun observeTypingStatus(roomId: String, currentUserId: String): Flow<String?> = callbackFlow {
            val listener = database.collection("chatrooms")
                .document(roomId)
                .collection("typingStatus")
                .addSnapshotListener { snapshot, _ ->
                    snapshot?.documents?.forEach { doc ->
                        val userId = doc.id
                        val isTyping = doc.getBoolean("isTyping") ?: false
                        if (userId != currentUserId && isTyping) {
                            trySend(userId)
                            return@addSnapshotListener
                        }
                    }
                    trySend(null)
                }
            awaitClose { listener.remove() }
        }

        suspend fun updateTypingStatus(roomId: String, userId: String, isTyping: Boolean) {
            try {
                database.collection("chatrooms")
                    .document(roomId)
                    .collection("typingStatus")
                    .document(userId)
                    .set(
                        mapOf(
                            "isTyping" to isTyping,
                            "timestamp" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating typing status", e)
            }
        }

    suspend fun getRoomParticipants(roomId: String): List<String> {
        return database.collection(CHATROOMS_COLLECTION)
            .document(roomId)
            .get()
            .await()
            .get("participants") as? List<String> ?: emptyList()
    }

    }






