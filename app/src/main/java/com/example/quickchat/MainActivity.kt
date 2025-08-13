package com.example.quickchat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.example.quickchat.navigation.ChatAppNavHost
import com.example.quickchat.navigation.Routes
import com.example.quickchat.presentation.component.NameInputDialog
import com.example.quickchat.presentation.viewmodel.ChatViewModel
import com.example.quickchat.ui.theme.QuickChatTheme
import com.example.quickchat.utils.DeviceIdHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logCurrentToken()
        requestNotificationPermission()
        createNotificationChannel()
        handleIntent(intent)

        setContent {
            QuickChatApp()
        }
    }

    @Composable
    private fun QuickChatApp() {
        QuickChatTheme {
            val navController = rememberNavController()
            val currentUserId = remember { getCurrentUserId() }
            val viewModel: ChatViewModel = koinViewModel()
            val context = LocalContext.current
            val deviceId = remember { DeviceIdHelper.getDeviceId(context) }
            val showNameDialog by viewModel.showNameDialog.collectAsState()

            // Check if name dialog should be shown
            LaunchedEffect(Unit) {
                viewModel.checkAndRequestName(deviceId)

                try {

                    val token = FirebaseMessaging.getInstance().token.await()
                    Log.d(TAG, "FCM Token: $token")

                    viewModel.initializeUserWithToken(deviceId, token)


                } catch (e: Exception) {
                    Log.e(TAG, "Initialization failed", e)
                    // Fallback - check name anyway
                    viewModel.checkAndRequestName(deviceId)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                ChatAppNavHost(
                    navController = navController,
                    deviceId = deviceId,
                    showNameDialog = showNameDialog,
                    viewModel = viewModel
                )

                if (showNameDialog) {
                    NameInputDialog(
                        deviceId = deviceId,
                        onDismiss = { name ->
                            if (name.isNotBlank()) {
                                viewModel.storeUserName(deviceId, name)
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun HandleDeepLinks(
        navController: androidx.navigation.NavController,
        currentUserId: String,
        viewModel: ChatViewModel
    ) {
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.intent?.extras?.let { extras ->
                when (extras.getString("deep_link_action")) {
                    "open_chat" -> {
                        val roomId = extras.getString("room_id") ?: return@let
                        val senderId = extras.getString("sender_id") ?: return@let

                        if (currentUserId.isNotEmpty()) {
                            viewModel.markMessagesAsRead(roomId, currentUserId)
                            navController.navigate(
                                Routes.chatScreenRoute(
                                    currentUserId = currentUserId,
                                    roomId = roomId,
                                    otherUserId = senderId
                                )
                            ) {
                                popUpTo(Routes.chatRoomsRoute(currentUserId))
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        // Handled in HandleDeepLinks composable
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "chat_messages",
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new chat messages"
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun getCurrentUserId(): String {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return sharedPref.getString("current_user_id", "") ?: ""
    }

    private fun logCurrentToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "FCM token: ${task.result}")
            } else {
                Log.e(TAG, "Token failed", task.exception)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
        } else {
            Log.w(TAG, "Notification permission denied")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}