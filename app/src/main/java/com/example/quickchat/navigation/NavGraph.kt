package com.example.quickchat.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.quickchat.presentation.screen.ChatScreen

@Composable
fun ChatAppNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "chat"
    ) {
        composable("chat") {
            ChatScreen()
        }
    }
}