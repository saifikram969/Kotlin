package com.example.quickchat.presentation.component
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.quickchat.data.model.GroupMember
@Composable
fun GroupMemberLongPressMenu(
    member: GroupMember,
    isAdmin: Boolean,
    currentUserId: String,
    onDismiss: () -> Unit,
    onMakeAdmin: () -> Unit,
    onRemoveAdmin: () -> Unit,
    onRemoveMember: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(member.userName) },
        text = {
            Column {
                // Make Admin option (only show for non-admins)
                if (member.role != "admin" && isAdmin) {
                    Text(
                        text = "Make Admin",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onMakeAdmin()
                                onDismiss()
                            }
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Divider()
                }

                // Remove Admin option (only show for admins who aren't current user)
                if (member.role == "admin" && isAdmin && member.userId != currentUserId) {
                    Text(
                        text = "Remove Admin",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onRemoveAdmin()
                                onDismiss()
                            }
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Divider()
                }

                // Remove Member option (only show for non-current user)
                if (member.userId != currentUserId && isAdmin) {
                    Text(
                        text = "Remove Member",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onRemoveMember()
                                onDismiss()
                            }
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}