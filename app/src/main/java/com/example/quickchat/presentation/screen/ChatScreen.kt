package com.example.quickchat.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.presentation.component.DateSeparator
import com.example.quickchat.presentation.component.MessageBubble
import com.example.quickchat.presentation.viewmodel.ChatUiState
import com.example.quickchat.presentation.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState) {
        if (uiState is ChatUiState.Success) {
            val messages = (uiState as ChatUiState.Success).messages
            if (messages.isNotEmpty()) {
                coroutineScope.launch {
                    listState.animateScrollToItem(index = messages.size - 1)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is ChatUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is ChatUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message)
                }
            }

            is ChatUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    state = listState
                ) {
                    items(state.messages) { item ->
                        when (item) {
                            is String -> DateSeparator(date = item)
                            is ChatMessage -> MessageBubble(
                                message = item,
                                isCurrentUser = item.senderId == state.currentUserId && !item.isSystemMessage
                            )
                        }
                    }
                }

                // === Updated Chat Input Bar ===
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        shape = RoundedCornerShape(32.dp),
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                placeholder = {
                                    Text("Typine a message...")
                                },
                                singleLine = false,
                                maxLines = 3,
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                )
                            )


                            IconButton(onClick = { /* TODO: Voice input */ }) {
                                Icon(Icons.Default.Add, contentDescription = "add files")
                            }

                            IconButton(
                                onClick = {
                                    viewModel.sendMessage(messageText)
                                    messageText = ""
                                    coroutineScope.launch {
                                        delay(50)
                                        listState.animateScrollToItem(index = state.messages.size - 1)
                                    }
                                },
                                enabled = messageText.isNotBlank(),
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (messageText.isNotBlank()) Color.Black else Color.LightGray,
                                        shape = RoundedCornerShape(50)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
