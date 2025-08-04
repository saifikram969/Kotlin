package com.example.quickchat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.quickchat.navigation.ChatAppNavHost
import com.example.quickchat.navigation.Routes
import com.example.quickchat.presentation.viewmodel.ChatViewModel
import com.example.quickchat.ui.theme.QuickChatTheme
import com.google.firebase.messaging.FirebaseMessaging
import org.koin.androidx.compose.koinViewModel
import kotlin.math.log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logCurrentToken() // Call this temporarily

        createNotificationChannel()
        setContent {
            QuickChatTheme {
                // Create navController at the root level
                val navController = rememberNavController()

                // Surface provides proper background color
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatAppNavHost(navController = navController)
                    HandleDeepLinks(navController)
                }
            }
        }
        handleIntent(intent)
        logCurrentToken()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "chat_messages",
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new chat messages"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun handleIntent(intent: Intent) {
        val roomId = intent.getStringExtra("deep_link_room_id") ?: return
        val senderId = intent.getStringExtra("deep_link_sender_id") ?: return
    }

    @Composable
    private fun HandleDeepLinks(navController: NavController) {
        val context = LocalContext.current
        val viewModel: ChatViewModel = koinViewModel()

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.intent?.extras?.let { extras ->
                val roomId = extras.getString("deep_link_room_id") ?: return@let
                val senderId = extras.getString("deep_link_sender_id") ?: return@let

                // Mark messages as read when coming from notification
                viewModel.onScreenEntered(roomId, senderId)

                navController.navigate(Routes.chatScreenRoute(
                    currentUserId = senderId,
                    roomId = roomId,
                    otherUserId = "other_user"
                )) {
                    popUpTo(Routes.chatRoomsRoute(senderId)) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
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

}