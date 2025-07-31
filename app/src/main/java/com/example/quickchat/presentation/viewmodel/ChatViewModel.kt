package com.example.quickchat.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.model.MessageStatus
import com.example.quickchat.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState

    init {
        Log.d("VM_LIFECYCLE", "ViewModel INITIALIZED - Hash: ${hashCode()}")
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun initializeChat(roomId: String, currentUserId: String) {

        viewModelScope.launch {
            try {
                combine(
                    repository.getCachedMessages(roomId),

                    repository.listenToMessages(roomId)
                ) { cached, remote ->
                    // Merge both and remove duplicates (prefer remote messages)
                    val merged = (cached + remote)
                        .distinctBy { it.id }
                        .sortedBy { it.timestamp }

                    Log.d("VIEWMODEL", "📥 Total merged messages: ${merged.size}")
                    merged
                }
                    .distinctUntilChanged()
                    .catch { e ->
                        _uiState.value = ChatUiState.Error("Failed to load chat")
                    }
                    .collectLatest { allMessages ->
                        _uiState.value = ChatUiState.Success(
                            messages = allMessages,
                            currentUserId = currentUserId,
                            roomId = roomId
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error("Unexpected error occurred")
            }
        }
    }

    fun sendMessage(roomId: String, senderId: String, text: String) {
        if (text.length > 300) {
            return
        }

        val messageId = UUID.randomUUID().toString()
        val clientGenId = "${senderId}_${System.currentTimeMillis()}"
        val timestamp = System.currentTimeMillis()

        val newMessage = ChatMessage(
            id = messageId,
            text = text,
            senderId = senderId,
            status = MessageStatus.SENDING,
            clientGeneratedId = clientGenId,
            timestamp = timestamp
        )

        updateMessages(newMessage)

        viewModelScope.launch {
            repository.cacheMessage(roomId, newMessage)

            val result = runCatching {
                repository.sendMessage(roomId, newMessage)
            }

            val finalStatus = if (result.getOrNull()?.isSuccess == true) {
                MessageStatus.SENT
            } else {
                MessageStatus.FAILED
            }

            updateMessageStatus(messageId, finalStatus)
            repository.updateMessageStatus(messageId, finalStatus)
        }
    }

    fun retryMessage(roomId: String, message: ChatMessage) {
        val newClientId = "${message.senderId}_${System.currentTimeMillis()}"

        val retriedMessage = message.copy(
            status = MessageStatus.SENDING,
            clientGeneratedId = newClientId,
            timestamp = System.currentTimeMillis()
        )

        updateMessages(retriedMessage)

        viewModelScope.launch {
            repository.cacheMessage(roomId, retriedMessage)

            val result = runCatching {
                repository.sendMessage(roomId, retriedMessage)
            }

            val finalStatus = if (result.getOrNull()?.isSuccess == true) {
                MessageStatus.SENT
            } else {
                MessageStatus.FAILED
            }

            updateMessageStatus(retriedMessage.id, finalStatus)
            repository.updateMessageStatus(retriedMessage.id, finalStatus)
        }
    }

    private fun updateMessages(newMessage: ChatMessage) {
        val state = _uiState.value
        if (state is ChatUiState.Success) {
            _uiState.value = state.copy(
                messages = (state.messages + newMessage)
                    .distinctBy { it.id }
                    .sortedBy { it.timestamp }
            )
        }
    }

    private fun updateMessageStatus(messageId: String, status: MessageStatus) {
        val state = _uiState.value
        if (state is ChatUiState.Success) {
            val updatedMessages = state.messages.map {
                if (it.id == messageId) it.copy(status = status) else it
            }
            _uiState.value = state.copy(messages = updatedMessages)
        }
    }




    fun onNetworkRestored(roomId: String) {
        viewModelScope.launch {
            // Sync message gaps first
            repository.syncMessageGaps(roomId)

            // Then retry any failed messages
            val failedMessages = repository.getFailedMessages(roomId)
            failedMessages.forEach { message ->
                retryMessage(roomId, message)
            }
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
