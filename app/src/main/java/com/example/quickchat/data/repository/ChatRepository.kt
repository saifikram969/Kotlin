package com.example.quickchat.data.repository

import android.util.Log
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.model.MessageStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository(private val database: FirebaseFirestore) {

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
                .await()

            Log.d("ChatRepository", "Message sent: ${message.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Send failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun listenToMessages(roomId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = database.collection("chatrooms")
            .document(roomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRepository", "Listen failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(ChatMessage::class.java)?.copy(
                            status = MessageStatus.valueOf(doc.getString("status") ?: "SENT"),
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        Log.e("ChatRepository", "Parse error: ${doc.id}", e)
                        null
                    }
                }?.distinctBy { it.clientGeneratedId } ?: emptyList()

                trySend(messages)
            }

        // Optional: Load initial messages
        database.collection("chatrooms")
            .document(roomId)
            .collection("messages")
            .get()
            .addOnSuccessListener { snapshot ->
                val initialMessages = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(ChatMessage::class.java)?.copy(
                            status = MessageStatus.valueOf(doc.getString("status") ?: "SENT"),
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                trySend(initialMessages)
            }

        awaitClose { listener.remove() }
    }
}
