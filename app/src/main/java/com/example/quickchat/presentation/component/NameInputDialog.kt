package com.example.quickchat.presentation.component

import androidx.compose.foundation.layout.Column

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.quickchat.presentation.viewmodel.ChatViewModel

@Composable
fun NameInputDialog(
    deviceId: String,
    onDismiss: (name: String) -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { /* Prevent dismiss without name */ },
        title = { Text("Enter Your Name") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Your Name") },
                    isError = error != null
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        name.isBlank() -> error = "Name cannot be empty"
                        name.length > 50 -> error = "Name too long"
                        else -> onDismiss(name)
                    }
                }
            ) {
                Text("Continue")
            }
        }
    )
}