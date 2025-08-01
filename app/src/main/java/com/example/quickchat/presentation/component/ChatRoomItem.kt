package com.example.quickchat.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.quickchat.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quickchat.data.model.ChatRoom
import java.text.SimpleDateFormat
import java.util.*
// ChatRoomItem.kt
@Composable
fun ChatRoomItem(
    room: ChatRoom,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = room.name.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = room.lastMessage ?: "No messages yet",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        .format(Date(room.lastTimestamp)),
                    style = MaterialTheme.typography.labelSmall
                )

                if (room.unreadCount > 0) {
                    Badge {
                        Text(room.unreadCount.toString())
                    }
                }
            }
        }
    }
}
/*

@Preview(showBackground = true)
@Composable
fun ChatRoomItemPreview() {
    val sampleRoom = ChatRoom(
        roomId = "General",
        lastMessage = "Don't forget the meeting!",
        lastTimestamp = System.currentTimeMillis(),
        unreadCount = 3
    )

    MaterialTheme {
        ChatRoomItem(room = sampleRoom, onClick = {})
    }
}
*/
