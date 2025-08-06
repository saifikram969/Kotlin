package com.example.quickchat.data.remote

import android.app.NotificationChannel
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
import com.example.quickchat.utils.DeviceIdHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class FcmService : FirebaseMessagingService() {

    private val repository: ChatRepository by inject()
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token received")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = DeviceIdHelper.getDeviceId(this@FcmService)
                if (repository.storeFcmToken(deviceId, token)) {
                    Log.d(TAG, "Token stored successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token storage failed", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("sender_id", "Sender ID: ${remoteMessage.senderId}") // यहाँ sender ID print करें
        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Data: ${remoteMessage.data}")

        // Always handle data payload first
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Then check for notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            showNotification(
                title = it.title ?: "New message",
                message = it.body ?: "",
                data = remoteMessage.data
            )
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        when (data["type"]) {
            "chat_message" -> showChatNotification(data)
            else -> showGenericNotification(
                title = data["title"] ?: "New message",
                message = data["message"] ?: "You have a new notification",
                data = data
            )
        }
    }

    private fun showChatNotification(data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("deep_link_action", "open_chat")
            data["roomId"]?.let { putExtra("room_id", it) }
            data["senderId"]?.let { putExtra("sender_id", it) }
        }

        showNotification(
            title = data["title"] ?: "New chat message",
            message = data["message"] ?: "You have a new message",
            data = data,
            pendingIntent = PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
    }
    private fun showNotification(
        title: String,
        message: String,
        data: Map<String, String>,
        pendingIntent: PendingIntent? = null
    ) {
        createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.notifications_24)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(1000, 1000)) // Add vibration
            .setDefaults(NotificationCompat.DEFAULT_SOUND) // Add sound

        pendingIntent?.let {
            notificationBuilder.setContentIntent(it)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
    private fun showGenericNotification(
        title: String,
        message: String,
        data: Map<String, String>
    ) {
        showNotification(
            title = title,
            message = message,
            data = data,
            pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new chat messages"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "FcmService"
        private const val CHANNEL_ID = "chat_messages"
    }
}
