package com.example.quickchat.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.utils.MessageUtils
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
    val bubbleColor = if (isCurrentUser) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }


    val alignment = if (isCurrentUser) Alignment.End else Alignment.Start

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 8.dp,
                        topEnd = 8.dp,
                        bottomStart = if (isCurrentUser) 8.dp else 0.dp,
                        bottomEnd = if (isCurrentUser) 0.dp else 8.dp
                    )
                )
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = if (isCurrentUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp, start = 8.dp, end = 8.dp)
        )
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun DateSeparator(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = MessageUtils.formatDateHeader(date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}