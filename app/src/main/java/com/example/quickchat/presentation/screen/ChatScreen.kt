package com.example.quickchat.presentation.screen

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.quickchat.presentation.component.ChatTopBar
import com.example.quickchat.presentation.component.MessageBubble
import com.example.quickchat.presentation.viewmodel.ChatUiState
import com.example.quickchat.presentation.viewmodel.ChatViewModel
import com.example.quickchat.utils.connectivityState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = koinViewModel(),
    currentUserId: String,
    roomId: String,
    otherUserId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }

    // Presence state
    val presenceStatus by viewModel.presenceStatus.collectAsState()
    val otherUserTyping by viewModel.otherUserTyping.collectAsState()

    // Handle lifecycle events for presence
    DisposableEffect(Unit) {
        viewModel.updatePresence(currentUserId, true)
        viewModel.observePresence(otherUserId)

        onDispose {
            viewModel.updatePresence(currentUserId, false)
            viewModel.updateTypingStatus(roomId, currentUserId, false)
        }
    }

    LaunchedEffect(roomId) {
        viewModel.initializeChat(roomId, currentUserId)
        viewModel.observeTypingStatus(roomId, otherUserId)
        viewModel.markMessagesAsRead(roomId, currentUserId)
        viewModel.observePresence(otherUserId)
    }

    // Typing status tracking
    var isTyping by remember { mutableStateOf(false) }
    var typingDebounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        // ... (keep existing image picker code) ...
    }






    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()



    val showScrollToBottomButton by remember {
        derivedStateOf {
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            lastVisibleItemIndex != null && totalItemsCount > 0 && lastVisibleItemIndex < totalItemsCount - 1
        }
    }


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

    var wasOffline by remember { mutableStateOf(false) }
    val isOnline by connectivityState()

    LaunchedEffect(isOnline) {
        if (isOnline && wasOffline) {
            viewModel.onNetworkRestored(roomId)
        }
        wasOffline = !isOnline
    }

    // Handle typing status changes
    LaunchedEffect(messageText) {
        typingDebounceJob?.cancel()

        if (messageText.isNotEmpty()) {
            if (!isTyping) {
                viewModel.updateTypingStatus(roomId, currentUserId, true)
                isTyping = true
            }

            typingDebounceJob = coroutineScope.launch {
                delay(2000) // 2 second delay after last keystroke
                if (messageText.isEmpty()) {
                    viewModel.updateTypingStatus(roomId, currentUserId, false)
                    isTyping = false
                }
            }
        } else if (isTyping) {
            viewModel.updateTypingStatus(roomId, currentUserId, false)
            isTyping = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        ChatTopBar(
            chatRoomName = "Chat Room",
            participantName = otherUserId,
            isOnline = presenceStatus ?: false,
            isTyping = otherUserTyping,
            onBackClick = onBackClick,
            onMoreOptionsClick = {}
        )

        if (isUploading) {
            LinearProgressIndicator(
                progress = { uploadProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
        }

        when (uiState) {
            is ChatUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is ChatUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Failed to load chat.") }
            is ChatUiState.Success -> {
                val state = uiState as ChatUiState.Success
                val (systemMessages, regularMessages) = state.messages.partition { it.isSystemMessage }

                LaunchedEffect(state.messages) {
                    delay(100) // Let layout settle

                    // Scroll to the last index in LazyColumn — account for date headers
                    val groupedMessages = state.messages
                        .filterNot { it.isSystemMessage }
                        .groupBy {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp))
                        }

                    // Total item count = total headers + total messages
                    val totalItems = groupedMessages.size + state.messages.count { !it.isSystemMessage }

                    if (totalItems > 0) {
                        listState.scrollToItem(totalItems - 1)
                    }
                }





                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        val groupedMessages = regularMessages.groupBy {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp))
                        }
                        groupedMessages.forEach { (dateKey, messagesForDate) ->
                            item(key = "header_$dateKey") { DateHeader(dateKey) }
                            items(messagesForDate, key = { it.id }) { message ->
                                MessageBubble(message, message.senderId == state.currentUserId)
                            }
                        }
                    }

                    // Scroll to bottom FAB
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToBottomButton,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    delay(100) // Allow layout to stabilize
                                    val groupedMessages = state.messages
                                        .filterNot { it.isSystemMessage }
                                        .groupBy {
                                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp))
                                        }

                                    val totalItems = groupedMessages.size + state.messages.count { !it.isSystemMessage }

                                    if (totalItems > 0) {
                                        listState.scrollToItem(totalItems - 1)
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Scroll to bottom",
                                tint = Color.White
                            )
                        }

                    }
                }


                Column {
                    if (messageText.isNotEmpty()) {
                        Text(
                            text = "$charCount/$maxCharCount",
                            color = charCountColor,
                            modifier = Modifier.padding(horizontal = 24.dp).align(Alignment.End)
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        shape = RoundedCornerShape(32.dp),
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.size(40.dp),
                                enabled = !isUploading
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.Image, contentDescription = "Pick Image", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TextField(
                                value = messageText,
                                onValueChange = {
                                    if (it.length <= maxCharCount) {
                                        messageText = it
                                    }
                                },
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
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
                                    cursorColor = MaterialTheme.colorScheme.primary
                                ),
                                trailingIcon = {
                                    if (messageText.isNotBlank()) {
                                        IconButton(onClick = { messageText = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                }
                            )

                            IconButton(
                                onClick = {
                                    if (messageText.isNotBlank()) {
                                        viewModel.sendMessage(roomId, currentUserId, messageText, null)
                                        messageText = ""
                                        viewModel.updateTypingStatus(roomId, currentUserId, false)
                                        isTyping = false
                                        coroutineScope.launch {
                                            delay(100)

                                            val groupedMessages = state.messages
                                                .filterNot { it.isSystemMessage }
                                                .groupBy {
                                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp))
                                                }

                                            val totalItems = groupedMessages.size + state.messages.count { !it.isSystemMessage }

                                            if (totalItems > 0) {
                                                listState.animateScrollToItem(totalItems - 1)
                                            }
                                        }


                                    }
                                },
                                enabled = messageText.isNotBlank(),
                                modifier = Modifier.size(48.dp).background(
                                    if (messageText.isNotBlank()) Color.Black else Color.LightGray,
                                    shape = RoundedCornerShape(50)
                                )
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ).padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}