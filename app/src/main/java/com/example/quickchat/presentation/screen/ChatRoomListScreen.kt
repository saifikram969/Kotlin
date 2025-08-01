package com.example.quickchat.presentation.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.quickchat.R
import com.example.quickchat.data.model.ChatRoom
import com.example.quickchat.presentation.viewmodel.ChatRoomListViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatRoomListScreen(
    userId: String,
    onChatRoomClick: (roomId: String, otherUserId: String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: ChatRoomListViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val rooms by viewModel.chatRooms.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val creationState by viewModel.roomCreationState.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(creationState) {
        when (creationState) {
            is ChatRoomListViewModel.RoomCreationState.Success -> {
                val roomId = (creationState as ChatRoomListViewModel.RoomCreationState.Success).roomId
                viewModel.resetRoomCreationState()
            }
            is ChatRoomListViewModel.RoomCreationState.Error -> {
                val errorMessage = (creationState as ChatRoomListViewModel.RoomCreationState.Error).message
                snackbarHostState.showSnackbar(
                    message = errorMessage ?: "Failed to create chat room",
                    withDismissAction = true
                )
            }
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchChatRooms()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.chat_rooms)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.fetchChatRooms() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading && rooms.isEmpty() -> FullScreenLoading()
                error != null -> ErrorState(
                    error = error,
                    onRetry = { viewModel.fetchChatRooms() }
                )
                rooms.isEmpty() -> EmptyState { viewModel.fetchChatRooms() }
                else -> ChatRoomListContent(
                    rooms = rooms,
                    userId = userId,
                    onRoomClick = { room ->
                        val otherUserId = room.participants.firstOrNull { it != userId } ?: ""
                        viewModel.onRoomClicked(
                            roomId = room.roomId,
                            otherUserId = otherUserId,
                            onNavigate = { route ->
                                onChatRoomClick(room.roomId, otherUserId)
                            }
                        )
                    },
                    isLoading = isLoading
                )
            }
        }
    }
}

@Composable
private fun ChatRoomListContent(
    rooms: List<ChatRoom>,
    userId: String,
    onRoomClick: (ChatRoom) -> Unit,
    isLoading: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rooms, key = { it.roomId }) { room ->
                ChatRoomListItem(
                    room = room,
                    currentUserId = userId,
                    onClick = { onRoomClick(room) }
                )
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun ChatRoomListItem(
    room: ChatRoom,
    currentUserId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val otherUserId = room.participants.firstOrNull { it != currentUserId } ?: ""
    val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val timeString = dateFormat.format(Date(room.lastTimestamp))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = otherUserId.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Chat with $otherUserId",
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
                horizontalAlignment = Alignment.End
            ) {
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

@Composable
private fun FullScreenLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(
    error: String?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = error ?: stringResource(R.string.unknown_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.no_chat_rooms),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRefresh) {
            Text(stringResource(R.string.refresh))
        }
    }
}