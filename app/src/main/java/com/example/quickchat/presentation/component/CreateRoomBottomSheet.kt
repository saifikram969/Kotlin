package com.example.quickchat.presentation.component

 import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
 import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
 import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
  import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
 import androidx.compose.material3.Button
 import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.quickchat.data.model.User
import com.example.quickchat.presentation.viewmodel.ChatRoomListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomBottomSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    roomTitle: String,
    onRoomTitleChange: (String) -> Unit,
    initialMembers: List<User> = emptyList(),
    onMemberSelectionChange: (User, Boolean) -> Unit = { _, _ -> },
    onCreateClick: () -> Unit = {},
    isGroup: Boolean = false,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: ChatRoomListViewModel
) {
    val density = LocalDensity.current
    var showMemberSelection by remember { mutableStateOf(false) }

    val availableUsers by viewModel.availableUsers.collectAsState()
    val isLoadingUsers by viewModel.isLoadingUsers.collectAsState()

    LaunchedEffect(show) {
        if (show) {
            viewModel.loadAvailableUsers("temp")
        }
    }

    AnimatedVisibility(
        visible = show,
        enter = slideInVertically { with(density) { -40.dp.roundToPx() } } + fadeIn(),
        exit = slideOutVertically { with(density) { -40.dp.roundToPx() } } + fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = if (isGroup) "Create Group Chat" else "New Chat",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isGroup) {
                    OutlinedTextField(
                        value = roomTitle,
                        onValueChange = onRoomTitleChange,
                        label = { Text("Group name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (initialMembers.isNotEmpty()) {
                    Text(
                        text = "Selected Members:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        initialMembers.forEach { member ->
                            MemberChip(
                                user = member,
                                onRemove = { onMemberSelectionChange(member, false) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { showMemberSelection = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add members")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Members")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onCreateClick,
                        enabled = if (isGroup) roomTitle.isNotBlank() && initialMembers.isNotEmpty()
                        else initialMembers.isNotEmpty() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(if (isGroup) "Create Group" else "Start Chat")
                        }
                    }
                }
            }
        }
    }

    if (showMemberSelection) {
        MemberSelectionDialog(
            isLoading = isLoadingUsers,
            availableUsers = availableUsers,
            selectedUsers = initialMembers,
            onUserSelected = { user ->
                onMemberSelectionChange(user, !initialMembers.contains(user))
            },
            onDismiss = { showMemberSelection = false }
        )
    }
}

@Composable
private fun MemberChip(
    user: User,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.userName.take(2).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = user.userName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
