package com.example.quickchat.presentation.component

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.quickchat.data.model.GroupMember
import com.example.quickchat.data.model.User
import com.example.quickchat.presentation.viewmodel.ChatRoomListViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMemberManagementScreen(
    roomId: String,
    currentUserId: String,
    onBackClick: () -> Unit,
    viewModel: ChatRoomListViewModel = koinViewModel(),
) {
    var members by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }

    val isLoading by viewModel.isLoadingUsers.collectAsState()
    val availableUsers by viewModel.availableUsers.collectAsState()
    val presenceStatus by viewModel.presenceStatus.collectAsState()

    // Load members when screen opens
    LaunchedEffect(roomId) {
        viewModel.loadGroupMembers(roomId)
    }

    // Observe members updates
    LaunchedEffect(viewModel.groupMembers) {
        viewModel.groupMembers.collect { updatedMembers ->
            members = updatedMembers.map { member ->
                val isOnline = viewModel.presenceStatus.value[member.userId] ?: false
                member.copy(isOnline = isOnline)
            }
        }
    }

    // Observe presence for all members
    LaunchedEffect(members) {
        members.forEach { member ->
            viewModel.observePresence(member.userId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Members") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (members.any { it.userId == currentUserId && it.role == "admin" }) {
                        IconButton(onClick = {
                            viewModel.loadAvailableUsers(roomId)
                            showAddMemberDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add member")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(members) { member ->
                    GroupMemberItem(
                        member = member,
                        isAdmin = members.any { it.userId == currentUserId && it.role == "admin" },
                        currentUserId = currentUserId,
                        isOnline = member.isOnline,
                        onRemove = { viewModel.removeMember(roomId, member.userId) },
                        onMakeAdmin = { viewModel.changeMemberRole(roomId, member.userId, "admin") },
                        onRemoveAdmin = { viewModel.changeMemberRole(roomId, member.userId, "member") },
                        onLongPress = { selectedMember ->
                            // Long press handled in the item itself
                        }
                    )
                }
            }

            // Leave/Transfer ownership button
            if (members.any { it.userId == currentUserId }) {
                val isCurrentUserAdmin = members.any {
                    it.userId == currentUserId && it.role == "admin"
                }

                Button(
                    onClick = {
                        if (isCurrentUserAdmin) {
                            showTransferDialog = true
                        } else {
                            viewModel.leaveGroup(roomId)
                            onBackClick()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (isCurrentUserAdmin) "Transfer Ownership & Leave" else "Leave Group")
                }
            }
        }
    }

    if (showAddMemberDialog) {
        MemberSelectionDialog(
            isLoading = isLoading,
            availableUsers = availableUsers,
            onUserSelected = { user ->
                viewModel.addMember(roomId, user.deviceId)
                showAddMemberDialog = false
            },
            onDismiss = { showAddMemberDialog = false }
        )
    }

    if (showTransferDialog) {
        TransferOwnershipDialog(
            members = members.filter { it.userId != currentUserId },
            onTransfer = { newAdminId ->
                viewModel.transferOwnership(roomId, newAdminId)
                viewModel.leaveGroup(roomId)
                showTransferDialog = false
                onBackClick()
            },
            onDismiss = { showTransferDialog = false }
        )
    }
}
@Composable
fun GroupMemberItem(
    member: GroupMember,
    isAdmin: Boolean,
    currentUserId: String,
    isOnline: Boolean,
    onRemove: () -> Unit,
    onMakeAdmin: () -> Unit,
    onRemoveAdmin: () -> Unit,
    onLongPress: (GroupMember) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showLongPressMenu by remember { mutableStateOf(false) }

    if (showLongPressMenu) {
        GroupMemberLongPressMenu(
            member = member,
            isAdmin = isAdmin,
            currentUserId = currentUserId,
            onDismiss = { showLongPressMenu = false },
            onMakeAdmin = {
                onMakeAdmin()
                showLongPressMenu = false
            },
            onRemoveAdmin = {
                onRemoveAdmin()
                showLongPressMenu = false
            },
            onRemoveMember = {
                onRemove()
                showLongPressMenu = false
            }
        )
    }


    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = { /* Regular click behavior */ },
                onLongClick = {
                    if (isAdmin && member.userId != currentUserId) {
                        showLongPressMenu = true
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with online indicator
            Box(modifier = Modifier.size(48.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        member.userName.take(2).uppercase(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Online status indicator
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.BottomEnd)
                            .background(
                                color = Color.Green,
                                shape = CircleShape
                            )
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        member.userName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )

                    // Admin badge
                    if (member.role == "admin") {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "Admin",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = if (isOnline) "Online" else "Offline",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isOnline) Color.Green else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            if (isAdmin && member.userId != currentUserId) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (member.role == "member") {
                            DropdownMenuItem(
                                text = { Text("Make admin") },
                                onClick = {
                                    onMakeAdmin()
                                    showMenu = false
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Remove admin") },
                                onClick = {
                                    onRemoveAdmin()
                                    showMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                onRemove()
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MemberSelectionDialog(
    isLoading: Boolean = false,
    availableUsers: List<User>,
    selectedUsers: List<User> = emptyList(),
    onUserSelected: (User) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(availableUsers) {
        Log.d("MemberDialog", "Available users count: ${availableUsers.size}")
        availableUsers.forEach { user ->
            Log.d("MemberDialog", "User: ${user.deviceId} - ${user.userName}")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Members") },
        text = {
            Column {
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading users...")
                    }
                } else if (availableUsers.isEmpty()) {
                    Text("No users available to add")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(availableUsers) { user ->
                            UserSelectionItem(
                                user = user,
                                isSelected = selectedUsers.contains(user),
                                onSelected = { onUserSelected(user) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
@Composable
private fun UserSelectionItem(
    user: User,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelected)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user.userName.take(2).uppercase(),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.userName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = user.deviceId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Checkbox(
            checked = isSelected,
            onCheckedChange = { onSelected() }
        )
    }
}




@Preview(showBackground = true)
@Composable
fun PreviewGroupMemberItem() {
    GroupMemberItem(
        member = GroupMember(
            userId = "1",
            userName = "Alice",
            role = "admin",
            isOnline = true,
            isMuted = true
        ),
        isAdmin = true,
        currentUserId = "1",
        isOnline = true,
        onRemove = {},
        onMakeAdmin = {},
        onRemoveAdmin = {}
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewUserSelectionItem() {
    UserSelectionItem(
        user = User(
            deviceId = "device123",
            userName = "Bob"
        ),
        isSelected = false,
        onSelected = {}
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewMemberSelectionDialog() {
    MemberSelectionDialog(
        isLoading = false,
        availableUsers = listOf(
            User(deviceId = "device1", userName = "Charlie"),
            User(deviceId = "device2", userName = "Diana")
        ),
        selectedUsers = emptyList(),
        onUserSelected = {},
        onDismiss = {}
    )
}
