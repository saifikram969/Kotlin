package com.example.quickchat.data.remote

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.quickchat.MainActivity
import com.example.quickchat.R
import com.example.quickchat.data.repository.ChatRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.*

class FcmService : FirebaseMessagingService() {

    private val chatRepository: ChatRepository by inject()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "New token: $token")
        // You can implement token refresh logic here if needed
    }
    private fun logCurrentToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("FCM_DEBUG", "Manual token: ${task.result}")
            } else {
                Log.e("FCM_DEBUG", "Token failed", task.exception)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.data.let { data ->
            when (data["type"]) {
                "chat_message" -> {
                    val roomId = data["roomId"] ?: return
                    val senderId = data["senderId"] ?: return
                    val senderName = data["senderName"] ?: "Someone"
                    val messagePreview = data["messagePreview"] ?: "New message"

                    if (isAppInForeground()) {
                        // If app is in foreground, just update the last read timestamp
                        CoroutineScope(Dispatchers.IO).launch {
                            // Get current user ID from shared preferences or other storage
                            val currentUserId = getCurrentUserId()
                            chatRepository.updateLastReadTimestamp(
                                roomId = roomId,
                                userId = currentUserId,
                                timestamp = System.currentTimeMillis()
                            )
                        }
                    } else {
                        // If app is in background, show notification
                        showNotification(
                            roomId = roomId,
                            senderName = senderName,
                            messagePreview = messagePreview,
                            senderId = senderId
                        )
                    }
                }
                // Add other notification types if needed
            }
        }
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val processes = activityManager.runningAppProcesses ?: return false
            for (process in processes) {
                if (process.processName == packageName) {
                    return process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val appProcessInfo = ActivityManager.RunningAppProcessInfo()
            @Suppress("DEPRECATION")
            ActivityManager.getMyMemoryState(appProcessInfo)
            return appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        return false
    }

    private fun getCurrentUserId(): String {
        // Implement your logic to get current user ID
        // This could be from SharedPreferences, Firebase Auth, etc.
        // For example:
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPref.getString("current_user_id", "") ?: ""
    }

    private fun handleChatMessageNotification(roomId: String, senderId: String, messagePreview: String) {
        CoroutineScope(Dispatchers.IO).launch {
            // Mark as read if app is in foreground
            if (isAppInForeground()) {
                chatRepository.updateLastReadTimestamp(roomId, senderId, System.currentTimeMillis())
                return@launch
            }

            // Create notification
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create deep link intent
            val intent = Intent(this@FcmService, MainActivity::class.java).apply {
                putExtra("roomId", roomId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                this@FcmService,
                Random().nextInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this@FcmService, "chat_messages")
                .setContentTitle("New message from $senderId")
                .setContentText(messagePreview)
                .setSmallIcon(R.drawable.notifications_24)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify(roomId.hashCode(), notification)
        }
    }
}

private fun FcmService.showNotification(
    roomId: String,
    senderName: String,
    messagePreview: String,
    senderId: String
) {
    // Create deep link intent
    val intent = Intent(this, MainActivity::class.java).apply {
        putExtra("deep_link_room_id", roomId)
        putExtra("deep_link_sender_id", senderId)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    val pendingIntent = PendingIntent.getActivity(
        this,
        roomId.hashCode(), // Unique request code
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, "chat_messages")
        .setContentTitle("$senderName: $messagePreview")
        .setContentText(messagePreview)
        .setSmallIcon(R.drawable.notifications_24)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()

    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        .notify(roomId.hashCode(), notification)
}