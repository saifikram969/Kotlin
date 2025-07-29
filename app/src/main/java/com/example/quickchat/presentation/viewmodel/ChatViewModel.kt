package com.example.quickchat.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState

    private val currentUserId = "user1" // This would normally come from auth

    init {
        loadMessages()

    }

    fun loadMessages() {
        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading
            try {
                val messages = repository.getMessages()
                _uiState.value = ChatUiState.Success(
                    messages = messages.toList(),
                    currentUserId = currentUserId
                )
                println("Messages loaded: ${messages.size}")
            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val newMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            senderId = currentUserId
        )
        repository.addMessage(newMessage)
        println("Message added to repo")

        loadMessages() // Refresh the list
    }

}


// presentation/viewmodel/ChatUiState.kt
sealed class ChatUiState {
    object Loading : ChatUiState()
    data class Success(
        val messages: List<ChatMessage>, // Now can contain both ChatMessage and String (date)
        val currentUserId: String
    ) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}
