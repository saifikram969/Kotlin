// package com.example.quickchat.navigation
package com.example.quickchat.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.quickchat.presentation.ChatRoomListScreen.ChatRoomListScreen
import com.example.quickchat.presentation.screen.ChatScreen

object Routes {
    const val USER_SELECTION = "user_selection"
    const val CHAT_ROOMS = "chat_rooms/{userId}"
    const val CHAT_SCREEN = "chat/{currentUserId}/{roomId}/{otherUserId}"

    fun chatRoomsRoute(userId: String) = "chat_rooms/$userId"
    fun chatScreenRoute(currentUserId: String, roomId: String, otherUserId: String) =
        "chat/$currentUserId/$roomId/$otherUserId"
}

@Composable
fun ChatAppNavHost(
    navController: NavHostController,
    startDestination: String = Routes.USER_SELECTION
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.USER_SELECTION) {
            UserSelectionScreen { selectedUserId ->
                navController.navigate(Routes.chatRoomsRoute(selectedUserId)) {
                    // Clear back stack to prevent going back to user selection
                    popUpTo(Routes.USER_SELECTION) { inclusive = true }
                }
            }
        }

        composable(Routes.CHAT_ROOMS) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId").orEmpty()
            ChatRoomListScreen(
                userId = userId,
                onChatRoomClick = { roomId, otherUserId ->
                    navController.navigate(Routes.chatScreenRoute(userId, roomId, otherUserId)) {
                        launchSingleTop = true
                    }
                },
                onBackClick = { navController.navigateUp() }
            )
        }

        composable(Routes.CHAT_SCREEN) { backStackEntry ->
            val currentUserId = backStackEntry.arguments?.getString("currentUserId").orEmpty()
            val roomId = backStackEntry.arguments?.getString("roomId").orEmpty()
            val otherUserId = backStackEntry.arguments?.getString("otherUserId").orEmpty()

            ChatScreen(
                currentUserId = currentUserId,
                roomId = roomId,
                otherUserId = otherUserId,
                onBackClick = { navController.navigateUp() }
            )
        }
    }
}

@Composable
fun UserSelectionScreen(onUserSelected: (String) -> Unit) {
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