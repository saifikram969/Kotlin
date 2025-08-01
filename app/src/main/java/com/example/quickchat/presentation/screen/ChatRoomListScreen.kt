package com.example.quickchat.presentation.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.quickchat.R
import com.example.quickchat.data.model.ChatRoom
import com.example.quickchat.presentation.component.ChatRoomItem
import com.example.quickchat.presentation.viewmodel.ChatRoomListViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatRoomListScreen(
    userId: String,
    onChatRoomClick: (String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: ChatRoomListViewModel = koinViewModel(parameters = { parametersOf(userId) })
)  {
    val rooms by viewModel.chatRooms.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val creationState by viewModel.roomCreationState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }


    // Fetch rooms when userId changes


    // Handle room creation states
    LaunchedEffect(creationState) {
        when (creationState) {
            is ChatRoomListViewModel.RoomCreationState.Success -> {
                val roomId = (creationState as ChatRoomListViewModel.RoomCreationState.Success).roomId
                viewModel.resetRoomCreationState()
                // Auto-navigate to newly created room if desired
                // onChatRoomClick(roomId)
            }
            is ChatRoomListViewModel.RoomCreationState.Error -> {
                val errorMessage = (creationState as ChatRoomListViewModel.RoomCreationState.Error).message
                // Consider showing a Snackbar instead of Toast for better Material3 integration
                // Use the local snackbarHostState instance
                snackbarHostState.showSnackbar(errorMessage)            }
            else -> {}
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.chat_rooms)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isRefreshing = true
                            viewModel.fetchChatRooms()
                            isRefreshing = false
                        },
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
                isLoading && rooms.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null -> {
                    ErrorState(
                        error = error,
                        onRetry = { viewModel.fetchChatRooms(userId) }
                    )
                }
                rooms.isEmpty() -> {
                    EmptyState(onRefresh = { viewModel.fetchChatRooms(userId) })
                }
                else -> {
                    ChatRoomList(
                        rooms = rooms,
                        userId = userId,
                        onRoomClick = { roomId ->
                            viewModel.onRoomClicked(roomId, userId, onChatRoomClick)
                        },
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRoomList(
    rooms: List<ChatRoom>,
    userId: String,
    onRoomClick: (String) -> Unit,
    isLoading: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rooms, key = { it.roomId }) { room ->
                ChatRoomItem(
                    room = room,
                    onClick = { onRoomClick(room.roomId) }
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