package com.example.quickchat.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.quickchat.presentation.screen.ChatRoomListScreen
import com.example.quickchat.presentation.screen.ChatScreen
@Composable
fun ChatAppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "user_selection"
    ) {
        composable("user_selection") {
            UserSelectionScreen { userId ->
                // Navigate to chat rooms list
                navController.navigate("chat_rooms/$userId")
            }
        }

        composable("chat_rooms/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            ChatRoomListScreen(
                userId = userId,
                onChatRoomClick = { roomId ->
                    navController.navigate("chat/$userId/$roomId") {
                        launchSingleTop = true
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable("chat/{currentUserId}/{otherUserId}") { backStackEntry ->
            val currentUserId = backStackEntry.arguments?.getString("currentUserId") ?: ""
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: ""
            // Generate roomId for your existing ChatScreen
            val roomId = remember(currentUserId, otherUserId) {
                listOf(currentUserId, otherUserId).sorted().joinToString("-")
            }

            ChatScreen(
                currentUserId = currentUserId,
                otherUserId = otherUserId, // Keep original parameter
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
fun UserSelectionScreen(
    onUserSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxSize(), Arrangement.Center) {
        Button(onClick = { onUserSelected("user1") }) {
            Text("Login as User 1")
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onUserSelected("user2") }) {
            Text("Login as User 2")
        }
    }
}