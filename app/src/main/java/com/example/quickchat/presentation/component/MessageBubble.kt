package com.example.quickchat.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.model.MessageStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: ChatMessage,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    if (message.isSystemMessage) {
        SystemMessage(message = message)
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
        ) {
            when {
                message.imageUrl != null -> {
                    ImageMessageComponent(
                        url = message.imageUrl,
                        isCurrentUser = isCurrentUser
                    )
                    if (!message.text.isNullOrEmpty()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                !message.text.isNullOrEmpty() -> {
                    TextMessageBubble(
                        message = message,
                        isCurrentUser = isCurrentUser
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageMessageComponent(
    url: String,
    isCurrentUser: Boolean
) {
    var showFullScreen by remember { mutableStateOf(false) }
    val bubbleColor = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isCurrentUser) 16.dp else 4.dp,
        bottomEnd = if (isCurrentUser) 4.dp else 16.dp
    )

    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(bubbleShape)
            .background(bubbleColor)
            .clickable { showFullScreen = true }
    ) {
        AsyncImage(
            model = url,
            contentDescription = "Chat image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
    }

    if (showFullScreen) {
        Dialog(
            onDismissRequest = { showFullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = "Full screen image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = { showFullScreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun TextMessageBubble(
    message: ChatMessage,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isCurrentUser) Color.Black else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleShape = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 8.dp,
        bottomStart = if (isCurrentUser) 8.dp else 0.dp,
        bottomEnd = if (isCurrentUser) 0.dp else 8.dp
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Text(
                text = message.text ?: "",
                color = textColor,
                modifier = Modifier.wrapContentWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray.copy(alpha = 0.8f)
                )

                if (isCurrentUser && message.status != MessageStatus.DELIVERED) {
                    Spacer(modifier = Modifier.width(4.dp))
                    when (message.status) {
                        MessageStatus.SENDING -> Icon(
                            Icons.Outlined.Send,
                            contentDescription = "Sending",
                            tint = Color.Gray,
                            modifier = Modifier.size(12.dp)
                        )
                        MessageStatus.SENT -> Icon(
                            Icons.Outlined.Done,
                            contentDescription = "Sent",
                            tint = Color.Gray,
                            modifier = Modifier.size(12.dp)
                        )
                        MessageStatus.FAILED -> Icon(
                            Icons.Outlined.Clear,
                            contentDescription = "Failed",
                            tint = Color.Red,
                            modifier = Modifier.size(12.dp)
                        )
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun SystemMessage(message: ChatMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}