package com.example.quickchat.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.quickchat.presentation.ChatRoomListScreen.ChatRoomListScreen
import com.example.quickchat.presentation.component.NameInputDialog
import com.example.quickchat.presentation.screen.ChatScreen
import com.example.quickchat.presentation.viewmodel.ChatViewModel

object Routes {
    //const val USER_SELECTION = "user_selection"
    const val NAME_DIALOG = "name_dialog/{deviceId}"
    const val CHAT_ROOMS = "chat_rooms/{userId}"
    const val CHAT_SCREEN = "chat/{currentUserId}/{roomId}/{otherUserId}"

    fun nameDialogRoute(deviceId: String) = "name_dialog/$deviceId"
    fun chatRoomsRoute(userId: String) = "chat_rooms/$userId"
    fun chatScreenRoute(currentUserId: String, roomId: String, otherUserId: String) =
        "chat/$currentUserId/$roomId/$otherUserId"
}

@Composable
fun ChatAppNavHost(
    navController: NavHostController,
    deviceId: String,
    showNameDialog: Boolean,
    viewModel: ChatViewModel,
    startDestination: String = Routes.nameDialogRoute(deviceId)
) {
    val viewModel: ChatViewModel = viewModel()
    NavHost(
        navController = navController,
        startDestination = if (showNameDialog) {
            Routes.NAME_DIALOG
        } else {
            Routes.CHAT_ROOMS.replace("{userId}", deviceId)
        }
    ) {
        composable(Routes.NAME_DIALOG) { backStackEntry ->
            // Empty composable - dialog is shown in MainActivity
            Box(modifier = Modifier.fillMaxSize())
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

/*@Composable
fun UserSelectionScreen(onUserSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Top Text
        Text(
            text = "Testing mode",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // User 1 Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onUserSelected("user1") },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User 1",
                    tint = Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Login as User 1",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.Gray)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // User 2 Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onUserSelected("user2") },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User 2",
                    tint = Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Login as User 2",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.Gray)
                )
            }
        }
    }*/
//}