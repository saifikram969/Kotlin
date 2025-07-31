package com.example.quickchat.data.repository

import android.util.Log
import com.example.quickchat.data.local.ChatMessageDao
import com.example.quickchat.data.local.toChatMessage
import com.example.quickchat.data.local.toEntity
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.model.MessageStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

class ChatRepository(
    private val database: FirebaseFirestore,
    private val chatMessageDao: ChatMessageDao
) {
    init {
        database.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
    }

    suspend fun sendMessage(roomId: String, message: ChatMessage): Result<Unit> {
        return try {

            val messageData = hashMapOf(
                "id" to message.id,
                "text" to message.text,
                "senderId" to message.senderId,
                "timestamp" to message.timestamp,
                "status" to message.status.name,
                "clientGeneratedId" to message.clientGeneratedId,
                "isSystemMessage" to message.isSystemMessage
            )

            database.collection("chatrooms")
                .document(roomId)
                .collection("messages")
                .document(message.id)
                .set(messageData)
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        Log.d("REPOSITORY", "📤 Message sent successfully: ${message.id}")
                    } else {
                    }
                }
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenToMessages(roomId: String): Flow<List<ChatMessage>> = callbackFlow {

        val listener = database.collection("chatrooms")
            .document(roomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> {
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
                                ).also {
                                    Log.v(
                                        "REPOSITORY",
                                        "👂📩 Received message: ${it?.id} [${it?.text?.take(10)}...] @${Date(it?.timestamp ?: 0L)}"
                                    )                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                        trySend(messages)
                    }
                }
            }

        awaitClose {
            listener.remove()
        }
    }

    fun getCachedMessages(roomId: String): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesByRoom(roomId).map { entities ->
            entities.map { it.toChatMessage() }.also { messages ->
                Log.d("REPOSITORY", "💾 Found ${messages.size} cached messages. " +
                        "Oldest: ${messages.minByOrNull { it.timestamp }?.timestamp?.let { Date(it) }} " +
                        "Newest: ${messages.maxByOrNull { it.timestamp }?.timestamp?.let { Date(it) }}")
            }
        }
    }

    suspend fun cacheMessage(roomId: String, message: ChatMessage) {
        try {
            chatMessageDao.insertMessage(message.toEntity(roomId))
        } catch (e: Exception) {
        }
    }

    suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
        try {
            chatMessageDao.updateMessageStatus(messageId, status.name)
        } catch (e: Exception) {
        }
    }

    suspend fun getFailedMessages(roomId: String): List<ChatMessage> {
        return try {
            val messages = chatMessageDao.getFailedMessages(roomId).map { it.toChatMessage() }
            messages
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun clearRoomCache(roomId: String) {
        try {
            chatMessageDao.clearRoomMessages(roomId)
        } catch (e: Exception) {
        }
    }

    suspend fun syncMessageGaps(roomId: String) {
        try {

            // Debug: Check if room exists in Firestore
            val roomExists = try {
                database.collection("chatrooms").document(roomId).get().await().exists()
            } catch (e: Exception) {
                false
            }

            val oldestTime = chatMessageDao.getOldestTimestamp(roomId)?.also {
            } ?: run {
                null
            }

            val newestTime = chatMessageDao.getNewestTimestamp(roomId)?.also {
            } ?: run {
                null
            }

            // Sync older messages
            oldestTime?.let { timestamp ->
                Log.d("SYNC", "🔍 Querying messages before ${Date(timestamp)}")
                try {
                    val olderMessages = database.collection("chatrooms/$roomId/messages")
                        .whereLessThan("timestamp", timestamp)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(20)
                        .get()
                        .await()

                    olderMessages.forEach { doc ->
                        try {
                            val message = doc.toObject(ChatMessage::class.java)
                            cacheMessage(roomId, message)
                        } catch (e: Exception) {
                        }
                    }
                } catch (e: Exception) {
                }
            }

            // Sync newer messages with 1 second buffer to avoid missing messages
            val syncThreshold = (newestTime ?: 0) - 1000

            try {
                val newerMessages = database.collection("chatrooms/$roomId/messages")
                    .whereGreaterThan("timestamp", syncThreshold)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .limit(20)
                    .get()
                    .await()

                newerMessages.forEach { doc ->
                    try {
                        val message = doc.toObject(ChatMessage::class.java)
                        if ((message.timestamp) > (newestTime ?: 0)) {
                            Log.d("SYNC", "💾 Caching newer message: ${message.id} @${Date(message.timestamp)}")
                            cacheMessage(roomId, message)
                        } else {
                        }
                    } catch (e: Exception) {
                    }
                }
            } catch (e: Exception) {
            }

        } catch (e: Exception) {
        }
    }

    suspend fun debugSync(roomId: String) {

        try {
            // Get latest message from Firestore
            val latestServerMessage = database.collection("chatrooms/$roomId/messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()
                ?.let { doc ->
                    doc.getLong("timestamp")?.let { Date(it) }
                }


            // Get local newest message
            val newestLocal = chatMessageDao.getNewestTimestamp(roomId)?.let { Date(it) }

            // Check if we need sync
            if (latestServerMessage != null && newestLocal != null) {
                val needsSync = latestServerMessage.after(newestLocal)
            }
        } catch (e: Exception) {
        }
    }
}