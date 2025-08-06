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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    // Handle lifecycle events for presence
    DisposableEffect(Unit) {
        viewModel.updatePresence(currentUserId, true)
        viewModel.observePresence(otherUserId)

        onDispose {
            viewModel.updatePresence(currentUserId, false)
        }
    }

    LaunchedEffect(roomId) {
        viewModel.initializeChat(roomId, currentUserId)
        viewModel.observeTypingStatus(roomId, currentUserId)
        viewModel.markMessagesAsRead(roomId, currentUserId)
        viewModel.observePresence(otherUserId)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            coroutineScope.launch {
                try {
                    val fileSize = context.contentResolver.openInputStream(imageUri)?.available() ?: 0
                    val maxSize = 5 * 1024 * 1024 // 5MB

                    if (fileSize > maxSize) {
                        Toast.makeText(context, "File too large. Max 5MB allowed.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    isUploading = true
                    uploadProgress = 0f

                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                        ImageDecoder.decodeBitmap(source)
                    } else {
                        MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                    }

                    // Compress image
                    val compressedFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
                    val outStream = FileOutputStream(compressedFile)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outStream)
                    outStream.flush()
                    outStream.close()

                    // Upload full image
                    MediaManager.get().upload(compressedFile.absolutePath)
                        .option("resource_type", "image")
                        .callback(object : UploadCallback {
                            override fun onStart(requestId: String?) {}
                            override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {
                                uploadProgress = bytes.toFloat() / totalBytes.toFloat()
                            }
                            override fun onSuccess(requestId: String?, resultData: Map<*, *>) {
                                val fullImageUrl = resultData["secure_url"] as? String ?: return

                                // Generate thumbnail
                                val thumbBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                                val thumbFile = File(
                                    context.cacheDir,
                                    "thumb_${System.currentTimeMillis()}.jpg"
                                )
                                val thumbStream = FileOutputStream(thumbFile)
                                thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 50, thumbStream)
                                thumbStream.flush()
                                thumbStream.close()

                                // Upload thumbnail
                                MediaManager.get().upload(thumbFile.absolutePath)
                                    .option("resource_type", "image")
                                    .callback(object : UploadCallback {
                                        override fun onSuccess(requestId: String?, result: Map<*, *>) {
                                            val thumbUrl = result["secure_url"] as? String ?: return
                                            viewModel.sendMessage(
                                                roomId = roomId,
                                                senderId = currentUserId,
                                                text = "",
                                                imageUrl = fullImageUrl,
                                                thumbnailUrl = thumbUrl
                                            )
                                            isUploading = false
                                            uploadProgress = 0f
                                        }
                                        override fun onError(requestId: String?, error: ErrorInfo?) {
                                            Toast.makeText(context, "Thumbnail upload failed", Toast.LENGTH_SHORT).show()
                                            isUploading = false
                                        }

                                        override fun onStart(requestId: String?) {}
                                        override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                                        override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                                    }).dispatch()
                            }
                            override fun onError(requestId: String?, error: ErrorInfo?) {
                                Toast.makeText(context, "Upload failed: ${error?.description}", Toast.LENGTH_SHORT).show()
                                isUploading = false
                            }
                            override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                        }).dispatch()

                } catch (e: Exception) {
                    Toast.makeText(context, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    isUploading = false
                }
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val typingUserId by viewModel.typingUserId.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

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

    Column(modifier = Modifier.fillMaxSize()) {
        ChatTopBar(
            chatRoomName = "Chat Room",
            participantName = otherUserId,
            isOnline = presenceStatus ?: false,
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
                    delay(100) // optional: wait for layout
                    listState.animateScrollToItem(state.messages.lastIndex)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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

                AnimatedVisibility(
                    visible = typingUserId != null,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFDFFFD6)
                        ) {
                            Text(
                                text = "$typingUserId is typing...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
                                        viewModel.updateTypingStatus(roomId, currentUserId, it.isNotEmpty())
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
                                        coroutineScope.launch {
                                            delay(100)
                                            listState.animateScrollToItem(state.messages.lastIndex)
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