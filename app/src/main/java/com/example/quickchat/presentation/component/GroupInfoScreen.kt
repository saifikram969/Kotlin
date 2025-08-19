package com.example.quickchat.presentation.component


import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.quickchat.data.model.GroupMember
import com.example.quickchat.presentation.viewmodel.ChatRoomListViewModel
import com.example.quickchat.presentation.viewmodel.GroupInfoViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    groupId: String,
    onBackClick: () -> Unit,
    onGroupManagementClick: (String, String) -> Unit,
    viewModel: GroupInfoViewModel = koinViewModel()
) {
    val members by viewModel.groupMembers.collectAsState()
    val presenceStatus by viewModel.presenceStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(groupId) {
        if (groupId.isNotBlank()) {
            viewModel.loadGroupMembers(groupId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Info") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item {
                // Group info header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Group avatar placeholder
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "GP",
                            style = MaterialTheme.typography.headlineLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Group Chat", // You can make this dynamic if you store group title
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${members.size} members",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                // Group management button
                Button(
                    onClick = {
                        // Get current user ID from members
                        val currentUserId = members.firstOrNull { it.role == "admin" }?.userId ?: ""
                        onGroupManagementClick(groupId, currentUserId)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Manage Group Members")
                }
            }

            item {
                Text(
                    text = "Members",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (members.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No members found")
                    }
                }
            } else {
                items(members) { member ->
                    GroupMemberItem(
                        member = member.copy(
                            isOnline = presenceStatus[member.userId] ?: false
                        ),
                        isAdmin = false,
                        currentUserId = "",
                        isOnline = presenceStatus[member.userId] ?: false,
                        onRemove = {},
                        onMakeAdmin = {},
                        onRemoveAdmin = {}
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun PreviewGroupInfoScreen() {
    val fakeMembers = listOf(
        GroupMember(userId = "1", userName = "Alice", role = "admin", isOnline = true, isMuted = false),
        GroupMember(userId = "2", userName = "Bob", role = "member", isOnline = false, isMuted = true),
        GroupMember(userId = "3", userName = "Charlie", role = "member", isOnline = true, isMuted = false)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Info") },
                navigationIcon = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("GP", style = MaterialTheme.typography.headlineLarge)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Group 12345",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${fakeMembers.size} members",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Manage Group Members")
                }
            }

            item {
                Text(
                    text = "Members",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            items(fakeMembers) { member ->
                GroupMemberItem(
                    member = member,
                    isAdmin = false,
                    currentUserId = "",
                    isOnline = member.isOnline,
                    onRemove = {},
                    onMakeAdmin = {},
                    onRemoveAdmin = {}
                )
            }
        }
    }
}


