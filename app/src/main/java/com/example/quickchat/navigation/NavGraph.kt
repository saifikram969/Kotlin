package com.example.quickchat.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.quickchat.presentation.screen.ChatScreen

@Composable
fun ChatAppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController, startDestination = "user_selection") {
        composable("user_selection") {
            UserSelectionScreen(navController)
        }
        composable("chat/{currentUserId}/{otherUserId}") { backStackEntry ->
            val currentUserId = backStackEntry.arguments?.getString("currentUserId") ?: ""
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: ""
            ChatScreen(currentUserId = currentUserId, otherUserId = otherUserId)
        }
    }
}

@Composable
fun UserSelectionScreen(navController: NavController) {
    Column(Modifier.fillMaxSize(), Arrangement.Center) {
        Button(onClick = {
            navController.navigate("chat/user1/user2")
        }) {
            Text("Login as User 1 (Chat with User 2)")
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            navController.navigate("chat/user2/user1")
        }) {
            Text("Login as User 2 (Chat with User 1)")
        }
    }
}