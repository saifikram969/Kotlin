package com.example.quickchat.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    chatRoomName: String,
    participantName: String,
    onBackClick: () -> Unit,
    onMoreOptionsClick: () -> Unit
) {
    var showJoinedText by remember { mutableStateOf(true) }

    // Auto-hide after 5 seconds (you can change this duration)
    LaunchedEffect(Unit) {
        delay(5000)
        showJoinedText = false
    }

    TopAppBar(
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = chatRoomName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedVisibility(
                    visible = showJoinedText,
                    enter = slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(600)
                    ),
                    exit = slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(600)
                    )
                ) {
                    Text(
                        text = "$participantName joined the chat room",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        color = Color(0xFF81C784), // Light green color

                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onMoreOptionsClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options"
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewChatTopBar() {
    MaterialTheme {
        Scaffold(
            topBar = {
                ChatTopBar(
                    chatRoomName = "QuickChat Room",
                    participantName = "Akbar",
                    onBackClick = {},
                    onMoreOptionsClick = {}
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                Text("Chat content goes here", modifier = Modifier.padding(16.dp))
            }
        }
    }
}
