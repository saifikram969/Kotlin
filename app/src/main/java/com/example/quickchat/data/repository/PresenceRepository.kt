package com.example.quickchat.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

interface PresenceRepository {
    fun observeUserPresence(userId: String): Flow<Boolean>
    suspend fun updateUserPresence(userId: String, isOnline: Boolean)
}

class PresenceRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : PresenceRepository {
    private val presenceCollection = firestore.collection("presence")

    override suspend fun updateUserPresence(userId: String, isOnline: Boolean) {
        try {
            presenceCollection.document(userId).set(
                mapOf(
                    "isOnline" to isOnline,
                    "lastUpdated" to FieldValue.serverTimestamp(),
                    "lastSeen" to if (!isOnline) FieldValue.serverTimestamp() else null
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            throw e
        }
    }

    override fun observeUserPresence(userId: String): Flow<Boolean> = callbackFlow {
        val listener = presenceCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val isOnline = snapshot?.getBoolean("isOnline") ?: false
                trySend(isOnline)
            }

        awaitClose { listener.remove() }
    }
}