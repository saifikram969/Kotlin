package com.example.quickchat.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.*
import androidx.navigation.compose.*
import com.example.quickchat.presentation.ChatRoomListScreen.ChatRoomListScreen
import com.example.quickchat.presentation.scree.ChatScreen

@Composable
fun ChatAppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "user_selection"
    ) {
        // User selection screen
        composable("user_selection") {
            UserSelectionScreen { selectedUserId ->
                navController.navigate("chat_rooms/$selectedUserId")
            }
        }

        // Chat rooms list screen
        composable("chat_rooms/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            ChatRoomListScreen(
                userId = userId,
                onChatRoomClick = { roomId, otherUserId ->  // Now takes two parameters
                    navController.navigate("chat/$userId/$roomId/$otherUserId") {
                        launchSingleTop = true
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        // Chat screen
        composable("chat/{currentUserId}/{roomId}/{otherUserId}") { backStackEntry ->
            val currentUserId = backStackEntry.arguments?.getString("currentUserId") ?: ""
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: ""

            ChatScreen(
                currentUserId = currentUserId,
                roomId = roomId,
                otherUserId = otherUserId,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}

// Helper function moved outside NavHost for better organization
private fun generateRoomId(user1: String, user2: String): String {
    return listOf(user1, user2).sorted().joinToString("-")
}

@Composable
fun UserSelectionScreen(
    onUserSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = { onUserSelected("user1") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login as User 1")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onUserSelected("user2") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login as User 2")
        }
    }
}