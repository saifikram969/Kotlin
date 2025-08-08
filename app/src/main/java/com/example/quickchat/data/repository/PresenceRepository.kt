package com.example.quickchat.data.repository

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface PresenceRepository {
    fun observeUserPresence(userId: String): Flow<Boolean>
    suspend fun updateUserPresence(userId: String, isOnline: Boolean)
    suspend fun updateTypingStatus(roomId: String, userId: String, isTyping: Boolean)
    fun observeTypingStatus(roomId: String, userId: String): Flow<Boolean>
    fun registerActivityLifecycleCallbacks(application: Application)
}

@Singleton
class PresenceRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : PresenceRepository, LifecycleObserver, Application.ActivityLifecycleCallbacks {

    private val presenceCollection = firestore.collection("presence")
    private var activitiesStarted = 0
    private var currentUserId: String? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun registerActivityLifecycleCallbacks(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        currentUserId?.let { userId ->
            coroutineScope.launch {
                updateUserPresence(userId, false)
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        // Presence will be updated when activity resumes
    }



    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        if (activitiesStarted == 0 && currentUserId != null) {
            // App came to foreground
            coroutineScope.launch {
                updateUserPresence(currentUserId!!, true)
            }
        }
        activitiesStarted++
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        activitiesStarted--
        if (activitiesStarted == 0 && currentUserId != null) {
            // App went to background
            coroutineScope.launch {
                updateUserPresence(currentUserId!!, false)
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    override suspend fun updateUserPresence(userId: String, isOnline: Boolean) {
        try {
            currentUserId = userId
            val updateData = mapOf(
                "isOnline" to isOnline,
                "lastUpdated" to FieldValue.serverTimestamp(),
                "lastSeen" to if (!isOnline) FieldValue.serverTimestamp() else null
            )
            presenceCollection.document(userId).set(updateData, SetOptions.merge()).await()
        } catch (e: Exception) {
            throw e
        }
    }

    override fun observeUserPresence(userId: String): Flow<Boolean> = callbackFlow {
        // 1. Add validation for user ID
        if (userId.isBlank()) {
            close(IllegalArgumentException("User ID cannot be empty"))
            return@callbackFlow
        }

        // 2. Add logging for debugging
        println("Observing presence for user: $userId")

        // 3. Safely create document reference
        val docRef = try {
            presenceCollection.document(userId)
        } catch (e: IllegalArgumentException) {
            close(e)
            return@callbackFlow
        }

        // 4. Add proper error handling
        val listener = docRef.addSnapshotListener { snapshot, error ->
            when {
                error != null -> {
                    close(error)
                    return@addSnapshotListener
                }
                snapshot == null || !snapshot.exists() -> {
                    trySend(false) // Default to offline if no snapshot
                }
                else -> {
                    val isOnline = snapshot.getBoolean("isOnline") ?: false
                    val sendResult = trySend(isOnline)
                    if (sendResult.isFailure) {
                        close() // Close if channel is full or cancelled
                    }
                }
            }
        }

        // 5. Handle coroutine cancellation
        awaitClose {
            listener.remove()
            println("Presence observation stopped for user: $userId")
        }
    }

    override suspend fun updateTypingStatus(roomId: String, userId: String, isTyping: Boolean) {
        try {
            firestore.collection("chatrooms")
                .document(roomId)
                .collection("typingStatus")
                .document(userId)
                .set(mapOf(
                    "isOnline" to isTyping,
                    "timestamp" to FieldValue.serverTimestamp()
                ), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            throw e
        }
    }

    override fun observeTypingStatus(roomId: String, userId: String): Flow<Boolean> = callbackFlow {
        val listener = firestore.collection("chatrooms")
            .document(roomId)
            .collection("typingStatus")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val isTyping = snapshot?.getBoolean("isOnline") ?: false
                trySend(isTyping)
            }
        awaitClose { listener.remove() }
    }



}