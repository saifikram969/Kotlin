package com.example.quickchat.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.model.MessageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: ChatMessage,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    if (message.isSystemMessage) {
        SystemMessage(message = message)
    } else {
        UserMessageBubble(message = message, isCurrentUser = isCurrentUser, modifier = modifier)
    }
}

@Composable
private fun UserMessageBubble(
    message: ChatMessage,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isCurrentUser) Color.Black else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isCurrentUser) Alignment.End else Alignment.Start
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
        horizontalAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
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
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}