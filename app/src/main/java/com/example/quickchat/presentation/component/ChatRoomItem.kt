/*
package com.example.quickchat.presentation.component

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.quickchat.data.model.ChatRoom
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatRoomItem(
    room: ChatRoom,
    currentUserId: String,
    onClick: () -> Unit,
    onMuteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val otherUserId = room.participants.firstOrNull { it != currentUserId } ?: ""
    val dateFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString by remember(room.lastTimestamp) {
        derivedStateOf { dateFormat.format(Date(room.lastTimestamp)) }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute button
            IconButton(
                onClick = {
                    Log.d("MuteButtonUI", "User clicked mute for ${room.roomId}")
                    if (!room.isProcessingMute) {
                        onMuteToggle()
                    }
                },
                enabled = !room.isProcessingMute
            ) {
                if (room.isProcessingMute) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = if (room.isMuted) Icons.Default.VolumeOff
                        else Icons.Default.VolumeUp,
                        contentDescription = if (room.isMuted) "Unmute" else "Mute",
                        tint = if (room.isMuted) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Avatar
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = otherUserId.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Chat with $otherUserId",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (room.isMuted) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Muted",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = room.lastMessage ?: "No messages yet",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (room.unreadCount > 0) {
                    Badge {
                        Text(text = room.unreadCount.toString())
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatRoomItemPreview() {
    val sampleRoom = ChatRoom(
        roomId = "user1-user2",
        name = "General Chat",
        lastMessage = "Don't forget the meeting!",
        lastTimestamp = System.currentTimeMillis(),
        unreadCount = 3,
        userId = "user1",
        participants = listOf("user1", "user2"),
        lastRead = 0L,
        isArchived = false,
        isMuted = false,
        isProcessingMute = false
    )

    MaterialTheme {
        ChatRoomItem(
            room = sampleRoom,
            currentUserId = "user1",
            onClick = {},
            onMuteToggle = {}
        )
    }
}*/
