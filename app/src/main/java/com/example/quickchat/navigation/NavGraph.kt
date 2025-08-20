package com.example.quickchat.navigation
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.quickchat.presentation.ChatRoomListScreen.ChatRoomListScreen
import com.example.quickchat.presentation.component.GroupInfoScreen
import com.example.quickchat.presentation.component.GroupMemberManagementScreen
import com.example.quickchat.presentation.component.NameInputDialog
import com.example.quickchat.presentation.screen.ChatScreen
import com.example.quickchat.presentation.viewmodel.ChatViewModel

object Routes {
    const val NAME_DIALOG = "name_dialog/{deviceId}"
    const val CHAT_ROOMS = "chat_rooms/{userId}"
    const val CHAT_SCREEN = "chat/{currentUserId}/{roomId}/{otherUserId}"
    const val GROUP_MANAGEMENT = "group_management/{roomId}/{currentUserId}"
    const val GROUP_INFO = "group_info/{groupId}/{groupName}"

    fun nameDialogRoute(deviceId: String) = "name_dialog/$deviceId"
    fun chatRoomsRoute(userId: String) = "chat_rooms/$userId"
    fun chatScreenRoute(currentUserId: String, roomId: String, otherUserId: String) =
        "chat/$currentUserId/$roomId/$otherUserId"

    fun groupManagementRoute(roomId: String, currentUserId: String) =
        "group_management/$roomId/$currentUserId"

    fun groupInfoRoute(roomId: String, groupName: String = "Group Chat") =
        "group_info/$roomId/${groupName.replace("/", "_")}"
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
                onBackClick = { navController.navigateUp() },
                navController = navController
            )
        }

        composable(Routes.GROUP_MANAGEMENT) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            val currentUserId = backStackEntry.arguments?.getString("currentUserId") ?: ""

            GroupMemberManagementScreen(
                roomId = roomId,
                currentUserId = currentUserId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Routes.GROUP_INFO) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val encodedGroupName = backStackEntry.arguments?.getString("groupName") ?: "Group Chat"
            val groupName = encodedGroupName.replace("_", "/")

            GroupInfoScreen(
                groupId = groupId,
                groupName = groupName,
                onBackClick = { navController.popBackStack() },
                onGroupManagementClick = { roomId, currentUserId ->
                    navController.navigate(Routes.groupManagementRoute(roomId, currentUserId))
                }
            )
        }

    }
}
