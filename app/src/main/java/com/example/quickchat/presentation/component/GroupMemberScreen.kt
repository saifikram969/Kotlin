/*
package com.example.quickchat.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.quickchat.presentation.viewmodel.ChatRoomListViewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.quickchat.data.model.GroupMember
import com.example.quickchat.data.model.User


// GroupMembersScreen.kt
@Composable
fun GroupMembersScreen(
    viewModel: ChatRoomListViewModel,
    roomId: String,
    onBack: () -> Unit
) {
    val members by remember { mutableStateOf(viewModel.getGroupMembers(roomId)) }
    val availableUsers by remember { mutableStateOf(viewModel.getAvailableUsersToAdd(roomId)) }
    var showAddMemberDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Group Members",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showAddMemberDialog = true }) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Member")
            }
        }

        // Members List
        LazyColumn {
            items(members) { member ->
                MemberItem(
                    member = member,
                    onRoleChange = { newRole ->
                        viewModel.changeMemberRole(roomId, member.userId, newRole)
                    },
                    onRemove = {
                        viewModel.removeMember(roomId, member.userId)
                    }
                )
            }
        }
    }

    if (showAddMemberDialog) {
        AddMemberDialog(
            availableUsers = availableUsers,
            onDismiss = { showAddMemberDialog = false },
            onAddMember = { userId ->
                viewModel.addMember(roomId, userId)
                showAddMemberDialog = false
            }
        )
    }
}

@Composable
fun MemberItem(
    member: GroupMember,
    onRoleChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            // User avatar/icon would go here
            Column(modifier = Modifier.weight(1f)) {
                Text(text = member.name)
                Text(text = member.role, style = MaterialTheme.typography.caption)
            }

            // Role selector
            var expanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(text = member.role)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Change Role")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(onClick = {
                        onRoleChange("admin")
                        expanded = false
                    }) {
                        Text("Make Admin")
                    }
                    DropdownMenuItem(onClick = {
                        onRoleChange("member")
                        expanded = false
                    }) {
                        Text("Make Member")
                    }
                }
            }

            // Remove button
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    }
}

@Composable
fun AddMemberDialog(
    availableUsers: List<User>,
    onDismiss: () -> Unit,
    onAddMember: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Member") },
        text = {
            LazyColumn {
                items(availableUsers) { user ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .clickable { onAddMember(user.userId) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // User avatar/icon would go here
                            Text(text = user.name, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}*/
