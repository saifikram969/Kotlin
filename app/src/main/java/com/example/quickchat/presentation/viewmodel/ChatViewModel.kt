package com.example.quickchat.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.model.MessageStatus
import com.example.quickchat.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState

    private var systemMessageShown = false

    fun sendMessage(roomId: String, senderId: String, text: String) {
        if (text.length > 300) return

        val id = UUID.randomUUID().toString()
        val newMessage = ChatMessage(
            id = id,
            text = text,
            senderId = senderId,
            status = MessageStatus.SENDING,
            clientGeneratedId = "${senderId}_${System.currentTimeMillis()}"
        )

        updateMessages(newMessage)

        viewModelScope.launch {
            val result = repository.sendMessage(roomId, newMessage)
            if (result.isSuccess) {
                updateMessageStatus(id, MessageStatus.SENT)
            } else {
                updateMessageStatus(id, MessageStatus.FAILED)
                Log.e("ChatViewModel", "Failed to send message", result.exceptionOrNull())
            }
        }
    }

    fun retryMessage(roomId: String, message: ChatMessage) {
        val retryMessage = message.copy(
            status = MessageStatus.SENDING,
            clientGeneratedId = "${message.senderId}_${System.currentTimeMillis()}"
        )

        updateMessages(retryMessage)

        viewModelScope.launch {
            val result = repository.sendMessage(roomId, retryMessage)
            if (result.isSuccess) {
                updateMessageStatus(message.id, MessageStatus.SENT)
            } else {
                updateMessageStatus(message.id, MessageStatus.FAILED)
            }
        }
    }

    fun initializeChat(roomId: String, currentUserId: String) {
        viewModelScope.launch {
            repository.listenToMessages(roomId).collectLatest { messagesFromDb ->

                val currentMessages = (_uiState.value as? ChatUiState.Success)?.messages ?: emptyList()

                val systemMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = "$currentUserId joined the chat",
                    senderId = "system",
                    isSystemMessage = true,
                    status = MessageStatus.SENT
                )

                _uiState.value = ChatUiState.Success(
                    messages = if (!systemMessageShown) {
                        systemMessageShown = true
                        listOf(systemMessage) + messagesFromDb
                    } else messagesFromDb,
                    currentUserId = currentUserId,
                    roomId = roomId
                )
            }
        }
    }

    private fun updateMessages(newMessage: ChatMessage) {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            _uiState.value = currentState.copy(
                messages = currentState.messages + newMessage
            )
        }
    }

    private fun updateMessageStatus(messageId: String, status: MessageStatus) {
        val currentState = _uiState.value
        if (currentState is ChatUiState.Success) {
            val updatedMessages = currentState.messages.map {
                if (it.id == messageId) it.copy(status = status) else it
            }
            _uiState.value = currentState.copy(messages = updatedMessages)
        }
    }
}

sealed class ChatUiState {
    object Loading : ChatUiState()
    data class Success(
        val messages: List<ChatMessage>,
        val currentUserId: String,
        val roomId: String
    ) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}
