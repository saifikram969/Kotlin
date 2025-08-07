package com.example.quickchat.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
    isOnline: Boolean,
    isTyping: Boolean,
    onBackClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    modifier: Modifier
) {
    var showStatusText by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(Color(0xFF81C784)) }

    // For animated typing dots
    val dotCount = 3
    val dotAnimationDuration = 500
    val dotDelays = listOf(0, 150, 300)
    val dotAlphas = remember { List(dotCount) { mutableStateOf(0.3f) } }

    // Track previous online state to detect changes
    var previousOnlineState by remember { mutableStateOf(isOnline) }
    var previousTypingState by remember { mutableStateOf(isTyping) }

    // Animate typing dots
    LaunchedEffect(isTyping) {
        if (isTyping) {
            while (true) {
                dotDelays.forEachIndexed { index, delayMs ->
                    delay(delayMs.toLong())
                    dotAlphas[index].value = 1f
                    delay((dotAnimationDuration - delayMs).toLong())
                    dotAlphas[index].value = 0.3f
                }
                if (!isTyping) break
                delay(500) // Pause between animation cycles
            }
        }
    }

    // Handle status changes
    LaunchedEffect(isOnline, isTyping) {
        when {
            // User went online/offline
            isOnline != previousOnlineState -> {
                statusText = if (isOnline)
                    "$participantName joined the chat room • Online"
                else
                    "$participantName went offline • Offline"
                statusColor = if (isOnline) Color(0xFF81C784) else Color(0xFF9E9E9E)
                showStatusText = true
                previousOnlineState = isOnline
            }
            // User started/stopped typing
            isTyping != previousTypingState -> {
                statusText = if (isTyping)
                    "$participantName is typing"
                else
                    "" // Don't show anything when typing stops
                statusColor = Color(0xFF81C784)
                showStatusText = isTyping
                previousTypingState = isTyping
            }
        }

        // Auto-hide after 5 seconds (except for typing which hides immediately when stops)
        if (showStatusText && !isTyping) {
            delay(5000)
            showStatusText = false
        }
    }

    // Show initial joined message only once
    LaunchedEffect(Unit) {
        if (isOnline) {
            statusText = "$participantName joined the chat room • Online"
            statusColor = Color(0xFF81C784)
            showStatusText = true
            delay(5000)
            showStatusText = false
        }
    }

    TopAppBar(
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = chatRoomName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Online status indicator
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                shape = MaterialTheme.shapes.small
                            )
                    )
                }

                AnimatedVisibility(
                    visible = showStatusText,
                    enter = slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(600)
                    ),
                    exit = slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(600)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            color = statusColor,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (isTyping) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Row {
                                repeat(dotCount) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(
                                                color = statusColor.copy(alpha = dotAlphas[index].value),
                                                shape = MaterialTheme.shapes.small
                                            )
                                            .padding(end = 2.dp)
                                    )
                                }
                            }
                        }
                    }
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

/*
@Preview(showBackground = true)
@Composable
fun PreviewChatTopBarTyping() {
    MaterialTheme {
        Scaffold(
            topBar = {
                ChatTopBar(
                    chatRoomName = "QuickChat Room",
                    participantName = "Akbar",
                    isOnline = true,
                    isTyping = true,
                    onBackClick = {},
                    onMoreOptionsClick = {},
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                Text("Chat content goes here", modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewChatTopBarOffline() {
    MaterialTheme {
        Scaffold(
            topBar = {
                ChatTopBar(
                    chatRoomName = "QuickChat Room",
                    participantName = "Akbar",
                    isOnline = false,
                    isTyping = false,
                    onBackClick = {},
                    onMoreOptionsClick = {},
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                Text("Chat content goes here", modifier = Modifier.padding(16.dp))
            }
        }
    }
}*/
