package com.example.quickchat.data.remote

import android.content.Context
import android.util.Log
import com.example.quickchat.data.repository.ChatRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await


// TokenManager.kt
class TokenManager(
    private val repository: ChatRepository,
    private val context: Context
) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    suspend fun initializeFcmToken(userId: String): Boolean {
        return try {
            // Get current FCM token
            val token = FirebaseMessaging.getInstance().token.await()

            // Store in Firestore
            repository.storeFcmToken(userId, token).also { success ->
                if (success) {
                    Log.d(TAG, "FCM token initialized successfully")
                    prefs.edit().putBoolean("fcm_token_registered", true).apply()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "FCM token initialization failed", e)
            false
        }
    }

    suspend fun refreshFcmToken(userId: String) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            repository.storeFcmToken(userId, token)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh FCM token", e)
        }
    }

    suspend fun clearFcmToken(userId: String) {
        repository.deleteFcmToken(userId)
        prefs.edit().remove("fcm_token_registered").apply()
    }

    companion object {
        private const val TAG = "TokenManager"
    }
}