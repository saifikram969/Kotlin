package com.example.quickchat.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.quickchat.presentation.component.MessageBubble
import com.example.quickchat.presentation.component.SystemMessage
import com.example.quickchat.presentation.viewmodel.ChatUiState
import com.example.quickchat.presentation.viewmodel.ChatViewModel
import com.example.quickchat.utils.connectivityState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.*


@Composable
fun ChatScreen(
    viewModel: ChatViewModel = koinViewModel(),
    currentUserId: String,
    otherUserId: String
) {
    // Consistent room ID generation (alphabetical order)
    val roomId = remember(currentUserId, otherUserId) {
        listOf(currentUserId, otherUserId).sorted().joinToString("-")
    }

    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val charCount by remember { derivedStateOf { messageText.length } }
    val maxCharCount = 300
    val charCountColor by remember {
        derivedStateOf {
            when {
                charCount > maxCharCount -> Color.Red
                charCount == maxCharCount -> Color(0xFFFFA000)
                else -> Color.Gray
            }
        }
    }

    //sync message
    var wasOffline by remember { mutableStateOf(false) }
    val isOnline by connectivityState()

    LaunchedEffect(isOnline) {
        if (isOnline && wasOffline) {
            viewModel.onNetworkRestored(roomId)
        }
        wasOffline = !isOnline
    }



    // Initialize chat and listener
    LaunchedEffect(roomId) {
        viewModel.initializeChat(roomId, currentUserId)
    }

    LaunchedEffect(uiState) {
        if (uiState is ChatUiState.Success) {
            val messages = (uiState as ChatUiState.Success).messages
            if (messages.isNotEmpty()) {
                coroutineScope.launch {
                    delay(100)
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            is ChatUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is ChatUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Error loading chat",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.initializeChat(roomId, currentUserId)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            is ChatUiState.Success -> {
                val state = uiState as ChatUiState.Success
                val (systemMessages, regularMessages) = state.messages.partition { it.isSystemMessage }

                Column(modifier = Modifier.padding(top = 8.dp)) {
                    systemMessages.forEach { message -> SystemMessage(message = message) }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    val groupedMessages = regularMessages.groupBy { message ->
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(message.timestamp))
                    }

                    groupedMessages.forEach { (dateKey, messagesForDate) ->
                        item(key = "header_$dateKey") {
                            DateHeader(dateKey)
                        }

                        items(messagesForDate, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                isCurrentUser = message.senderId == state.currentUserId
                            )
                        }
                    }
                }

                Column {
                    if (messageText.isNotEmpty()) {
                        Text(
                            text = "$charCount/$maxCharCount",
                            color = charCountColor,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .align(Alignment.End)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
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
                                onValueChange = { if (it.length <= maxCharCount) messageText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                placeholder = { Text("Type a message...") },
                                singleLine = false,
                                maxLines = 5,
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                ),
                                trailingIcon = {
                                    if (messageText.isNotEmpty()) {
                                        IconButton(
                                            onClick = { messageText = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            )

                            IconButton(
                                onClick = {
                                    if (messageText.isNotBlank() && messageText.length <= maxCharCount) {
                                        viewModel.sendMessage(
                                            roomId = roomId,
                                            senderId = currentUserId,
                                            text = messageText
                                        )
                                        messageText = ""
                                        coroutineScope.launch {
                                            delay(50)
                                            listState.animateScrollToItem(index = state.messages.size)
                                        }
                                    }
                                },
                                enabled = messageText.isNotBlank() && messageText.length <= maxCharCount,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (messageText.isNotBlank() && messageText.length <= maxCharCount) {
                                            Color.Black
                                        } else {
                                            Color.LightGray
                                        },
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

@Composable
fun DateHeader(dateKey: String) {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey)
    val today = Calendar.getInstance()
    val messageDate = Calendar.getInstance().apply { time = date }

    val label = when {
        today.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR) -> "Today"

        today.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) - 1 == messageDate.get(Calendar.DAY_OF_YEAR) -> "Yesterday"

        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date!!)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}
