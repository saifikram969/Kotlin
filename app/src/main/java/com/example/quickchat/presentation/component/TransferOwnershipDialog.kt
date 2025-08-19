package com.example.quickchat.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.quickchat.data.model.GroupMember

@Composable
fun TransferOwnershipDialog(
    members: List<GroupMember>,
    onTransfer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMemberId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer Ownership") },
        text = {
            Column {
                Text("Select a new admin for this group:")
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(members) { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMemberId = member.userId }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMemberId == member.userId,
                                onClick = { selectedMemberId = member.userId }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(member.userName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedMemberId?.let { onTransfer(it) } },
                enabled = selectedMemberId != null
            ) {
                Text("Transfer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
fun PreviewTransferOwnershipDialog() {
    val sampleMembers = listOf(
        GroupMember(userId = "1", userName = "Akbar", isMuted = true),
        GroupMember(userId = "2", userName = "Zishan", isMuted = false),
        GroupMember(userId = "3", userName = "Ali", isMuted = true)
    )

    TransferOwnershipDialog(
        members = sampleMembers,
        onTransfer = { newAdminId ->
            println("Transferred to: $newAdminId")
        },
        onDismiss = {
            println("Dialog dismissed")
        }
    )
}
