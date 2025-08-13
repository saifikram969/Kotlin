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
import com.google.firebase.firestore.DocumentChange
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
import kotlinx.coroutines.delay
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
import java.util.Date
import javax.inject.Inject
import kotlin.text.get

private const val CHATROOMS_COLLECTION = "chatrooms"
private val USERS_COLLECTION = "users"
private const val DEVICES_COLLECTION = "devices"

private const val TAG = "ChatRepository"
data class DeviceData(
    val fcmToken: String?,
    val userName: String?,
    val lastUpdated: Date?
)
class ChatRepository @Inject constructor(
    private val database: FirebaseFirestore,
    private val chatMessageDao: ChatMessageDao


) {
    init {
        database.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
    }

    // ==================== Message Status Tracking Enhancements ====================



    suspend fun getDeviceData(deviceId: String): DeviceData? {
        return try {
            val doc = database.collection("devices")
                .document(deviceId)
                .get()
                .await()

            if (doc.exists()) {
                DeviceData(
                    fcmToken = doc.getString("fcmToken"),
                    userName = doc.getString("userName"),
                    lastUpdated = doc.getDate("lastUpdated")
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device data", e)
            null
        }
    }


    suspend fun sendMessageWithStatusTracking(roomId: String, message: ChatMessage): Result<Unit> {

        // 0. Check network first
        if (!isNetworkAvailable()) {
            updateMessageStatus(message.id, MessageStatus.FAILED)
            return Result.failure(Exception("No network connection"))
        }


        return try {
            // 1. Cache the message locally with SENDING status
            cacheMessage(roomId, message.copy(status = MessageStatus.SENDING))

            // 2. Send to Firestore
            val messageRef = database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("messages")
                .document(message.id)

            messageRef.set(message.toFirestoreMap()).await()

            // 3. Immediately update status to SENT (single tick)
            updateMessageStatus(message.id, MessageStatus.SENT)

            // 4. Update chatroom's last message
            val updateData = mapOf<String, Any>(
                "lastMessage" to message.text.take(50),
                "lastTimestamp" to message.timestamp,
                "lastUpdated" to FieldValue.serverTimestamp()
            )

            database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .update(updateData)
                .await()

            // 5. Get the other participant's ID
            val participants = getRoomParticipants(roomId)
            val recipientId = participants.firstOrNull { it != message.senderId }
                ?: return Result.success(Unit)

            // 6. Setup delivery receipt listener
            setupDeliveryReceiptListener(messageRef, message.id)

            // 7. Check if recipient is online to mark as DELIVERED
            val isRecipientOnline = database.collection("presence")
                .document(recipientId)
                .get()
                .await()
                .getBoolean("isOnline") ?: false

            if (isRecipientOnline) {
                // Update status on server first
                updateMessageStatusOnServer(roomId, message.id, MessageStatus.DELIVERED)
            } else {
                // If recipient is offline, just mark as SENT for now
                updateMessageStatusOnServer(roomId, message.id, MessageStatus.SENT)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("smwt", "Error sending message with status tracking", e)
            updateMessageStatus(message.id, MessageStatus.FAILED)
            Result.failure(e)
        }
    }

    private fun setupDeliveryReceiptListener(messageRef: com.google.firebase.firestore.DocumentReference, messageId: String) {
        messageRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Delivery receipt listener error", error)
                return@addSnapshotListener
            }

            snapshot?.let { doc ->
                if (doc.exists()) {
                    val status = doc.getString("status") ?: ""
                    if (status == MessageStatus.DELIVERED.name) {
                        CoroutineScope(Dispatchers.IO).launch {
                            updateMessageStatus(messageId, MessageStatus.DELIVERED)
                        }
                    }
                }
            }
        }
    }

    suspend fun setupDeliveryStatusUpdates(roomId: String, messageId: String, recipientId: String) {
        // Listen for recipient's presence changes
        database.collection("presence")
            .document(recipientId)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let { doc ->
                    val isOnline = doc.getBoolean("isOnline") ?: false
                    if (isOnline) {
                        CoroutineScope(Dispatchers.IO).launch {
                            // If recipient comes online, check if message needs to be marked as delivered
                            val messageDoc = database.collection(CHATROOMS_COLLECTION)
                                .document(roomId)
                                .collection("messages")
                                .document(messageId)
                                .get()
                                .await()

                            val currentStatus = messageDoc.getString("status") ?: ""
                            if (currentStatus == MessageStatus.SENT.name) {
                                updateMessageStatusOnServer(roomId, messageId, MessageStatus.DELIVERED)
                            }
                        }
                    }
                }
            }
    }

    private suspend fun notifyRecipient(roomId: String, message: ChatMessage) {
        try {
            val roomDoc = database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .get()
                .await()

            val participants = roomDoc.get("participants") as? List<String> ?: emptyList()
            val otherUserId = participants.firstOrNull { it != message.senderId } ?: return

            // Mark as delivered if recipient is online
            val recipientPresence = database.collection("presence")
                .document(otherUserId)
                .get()
                .await()
                .getBoolean("isOnline") ?: false

            if (recipientPresence) {
                updateMessageStatusOnServer(roomId, message.id, MessageStatus.DELIVERED)
            } else {
                // Mark as sent but not delivered
                updateMessageStatusOnServer(roomId, message.id, MessageStatus.SENT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying recipient", e)
        }
    }

// In ChatRepository.kt

    suspend fun markMessagesAsRead(roomId: String, userId: String) {
        try {
            // First get the last read timestamp
            val lastRead = System.currentTimeMillis()

            // Update all messages that are:
            // 1. Not sent by this user
            // 2. Have status DELIVERED or SENT
            val unreadMessages = database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("messages")
                .whereNotEqualTo("senderId", userId)
                .whereIn("status", listOf(MessageStatus.DELIVERED.name, MessageStatus.SENT.name))
                .get()
                .await()

            // Batch update to SEEN status
            val batch = database.batch()
            unreadMessages.documents.forEach { doc ->
                batch.update(doc.reference, mapOf(
                    "status" to MessageStatus.SEEN.name,
                    "statusTimestamps.read" to lastRead
                ))
            }

            // Also update the room's lastRead timestamp
            batch.update(
                database.collection(CHATROOMS_COLLECTION).document(roomId),
                mapOf("lastRead_$userId" to lastRead)
            )

            batch.commit().await()

            // Update local cache
            unreadMessages.documents.forEach { doc ->
                updateMessageStatus(doc.id, MessageStatus.SEEN)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking messages as read", e)
        }
    }


    fun listenForMessageStatusUpdates(roomId: String, userId: String): Flow<Pair<String, MessageStatus>> = callbackFlow {
        val listener = database.collection(CHATROOMS_COLLECTION)
            .document(roomId)
            .collection("messages")
            .whereEqualTo("senderId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.MODIFIED) {
                        val messageId = change.document.id
                        val status = MessageStatus.valueOf(
                            change.document.getString("status") ?: MessageStatus.SENDING.name
                        )
                        trySend(messageId to status)
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    suspend fun updateMessageStatusOnServer(roomId: String, messageId: String, status: MessageStatus) {
        try {
            val updateData = mapOf<String, Any>(
                "status" to status.name,
                "lastUpdated" to FieldValue.serverTimestamp()
            )

            database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("messages")
                .document(messageId)
                .update(updateData)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating message status on server", e)
        }
    }
    suspend fun markMessagesAsSeen(roomId: String, userId: String) {
        try {
            // 1. Get all messages not sent by this user that haven't been seen
            val unseenMessages = database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("messages")
                .whereNotEqualTo("senderId", userId) // Only messages from others
                .whereNotEqualTo("status", MessageStatus.SEEN.name)
                .get()
                .await()

            // 2. Update each message to SEEN status on server
            unseenMessages.documents.forEach { doc ->
                updateMessageStatusOnServer(roomId, doc.id, MessageStatus.SEEN)
            }

            // 3. Update last read timestamp
            updateLastReadTimestamp(roomId, userId, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Error marking messages as seen", e)
        }
    }
    // ==================== Automatic Retry Enhancements ====================

    suspend fun retryFailedMessages(roomId: String) {
        try {
            val failedMessages = getFailedMessages(roomId)

            failedMessages.forEachIndexed { index, message ->
                // Exponential backoff: 1s, 2s, 4s, etc. (max 16s)
                val delayMillis = (1L shl index.coerceAtMost(4)) * 1000

                CoroutineScope(Dispatchers.IO).launch {
                    delay(delayMillis)
                    retrySingleMessage(roomId, message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrying failed messages", e)
        }
    }

    private suspend fun retrySingleMessage(roomId: String, message: ChatMessage) {
        updateMessageStatus(message.id, MessageStatus.SENDING)

        if (!isNetworkAvailable()) {
            updateMessageStatus(message.id, MessageStatus.FAILED)
            return
        }

        try {
            // Rest of your retry logic...
        } catch (e: Exception) {
            // Only mark as failed if we actually attempted to send
            if (isNetworkAvailable()) {
                updateMessageStatus(message.id, MessageStatus.FAILED)
            }
        }
    }

    suspend fun isNetworkAvailable(): Boolean {
        return try {
            // Try a lightweight Firestore operation as a connectivity check
            database.disableNetwork() // Ensure we're testing actual network
            database.enableNetwork()
            true
        } catch (e: Exception) {
            false
        }
    }



    // Add this to your ChatRepository class
    suspend fun storeFcmTokenWithName(
        deviceId: String,
        token: String,
        userName: String
    ): Boolean {
        return try {
            Log.d("FCM_DEBUG", "Storing token and name for device: $deviceId")

            // Step 1: Get existing document first
            val docRef = database.collection("devices").document(deviceId)
            val existingDoc = docRef.get().await()

            val existingName = existingDoc.getString("userName")

            // Step 2: Decide final name (avoid overwriting with blank)
            val finalName = if (userName.isNotBlank()) {
                userName
            } else {
                existingName ?: "" // Keep old name if exists
            }

            // Step 3: Prepare data
            val tokenData = hashMapOf(
                "fcmToken" to token,
                "userName" to finalName,
                "lastUpdated" to FieldValue.serverTimestamp(),
                "deviceId" to deviceId
            )

            // Step 4: Merge with existing data (no field loss)
            docRef.set(tokenData, SetOptions.merge()).await()

            Log.d("FCM_DEBUG", "Token & name stored successfully for $deviceId")
            true
        } catch (e: Exception) {
            Log.e("FCM_DEBUG", "Error storing token with name", e)
            false
        }
    }

    // In ChatRepository
    suspend fun getUserNameFromDevice(deviceId: String): String? {
        return try {
            database.collection("devices")
                .document(deviceId)
                .get()
                .await()
                .getString("userName")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user name", e)
            null
        }
    }

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
    }

    /**
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
        return sendMessageWithStatusTracking(roomId, message)
    }

    // In ChatRepository.kt
     fun listenToMessages(roomId: String): Flow<List<ChatMessage>> = callbackFlow {
        // FIRST: Always emit cached messages immediately
        val cachedMessages = try {
            chatMessageDao.getMessagesByRoom(roomId)
                .first()
                .map { it.toChatMessage() }
        } catch (e: Exception) {
            emptyList()
        }
        trySend(cachedMessages)

        // THEN: Setup Firestore listener if online
        if (isNetworkAvailable()) {
            val listener = database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("messages")
                .orderBy("timestamp")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Listen error", error)
                        return@addSnapshotListener
                    }

                    val messages = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            ChatMessage.fromFirestore(doc.data ?: emptyMap())
                        } catch (e: Exception) {
                            null
                        }
                    } ?: emptyList()

                    // Save new messages to cache
                    CoroutineScope(Dispatchers.IO).launch {
                        messages.forEach { message ->
                            chatMessageDao.insertMessage(message.toEntity(roomId))
                        }
                    }

                    trySend(messages)
                }

            awaitClose { listener.remove() }
        } else {
            awaitClose { }
        }
    }

     suspend fun getCachedMessages(roomId: String): List<ChatMessage> {
        return try {
            chatMessageDao.getMessagesByRoomOnce(roomId).map { it.toChatMessage() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached messages", e)
            emptyList()
        }
    }

    suspend fun syncMissingMessages(roomId: String) {
        try {
            val newestLocalTimestamp = chatMessageDao.getNewestTimestamp(roomId) ?: 0L

            val newMessages = database.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("messages")
                .whereGreaterThan("timestamp", newestLocalTimestamp)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    try {
                        ChatMessage.fromFirestore(doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        null
                    }
                }

            newMessages.forEach { message ->
                chatMessageDao.insertMessage(message.toEntity(roomId))
            }
            val oldestLocalTimestamp = chatMessageDao.getOldestTimestamp(roomId) ?: Long.MAX_VALUE

            if (oldestLocalTimestamp > 0) {
                val olderMessages = database.collection(CHATROOMS_COLLECTION)
                    .document(roomId)
                    .collection("messages")
                    .whereLessThan("timestamp", oldestLocalTimestamp)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { doc ->
                        try {
                            ChatMessage.fromFirestore(doc.data ?: emptyMap())
                        } catch (e: Exception) {
                            null
                        }
                    }

                olderMessages.forEach { message ->
                    chatMessageDao.insertMessage(message.toEntity(roomId))
                }            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing missing messages", e)
        }
    }


    suspend fun sendMessageWithOfflineSupport(roomId: String, message: ChatMessage): Result<Unit> {
        return try {
            chatMessageDao.insertMessage(message.copy(status = MessageStatus.SENDING).toEntity(roomId))

            if (!isNetworkAvailable()) {
                chatMessageDao.updateMessageStatus(message.id, MessageStatus.FAILED.name)
                return Result.failure(Exception("Offline - message queued"))
            }

            val result = sendMessage(roomId, message)

            if (result.isSuccess) {
                chatMessageDao.updateMessageStatus(message.id, MessageStatus.SENT.name)
            } else {
                chatMessageDao.updateMessageStatus(message.id, MessageStatus.FAILED.name)
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message with offline support", e)
            Result.failure(e)
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

    suspend fun clearMessagesForUserInRoom(userId: String, roomId: String) {
        val messagesRef = FirebaseFirestore.getInstance()
            .collection("messages")
            .document(userId)
            .collection(roomId)
        chatMessageDao.clearMessagesForUserInRoom(roomId, userId)

        val snapshot = messagesRef.get().await()
        for (doc in snapshot.documents) {
            doc.reference.delete().await()
        }
    }

    interface ChatRepository {
        suspend fun deleteChatroom(roomId: String)

    }

}