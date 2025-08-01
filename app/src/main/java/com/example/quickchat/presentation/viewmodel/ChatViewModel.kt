package com.example.quickchat.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.model.MessageStatus
import com.example.quickchat.data.model.TypingIndicator
import com.example.quickchat.data.repository.ChatRepository
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
private const val CHATROOMS_COLLECTION = "chatrooms" // lowercase everywhere

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState

    private val _typingUserId = MutableStateFlow<String?>(null)
    val typingUserId: StateFlow<String?> = _typingUserId

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
                    val merged = (cached + remote)
                        .distinctBy { it.id }
                        .sortedBy { it.timestamp }
                    Log.d("VIEWMODEL", "📥 Total merged messages: ${merged.size}")
                    merged
                }
                    .distinctUntilChanged()
                    .catch { _uiState.value = ChatUiState.Error("Failed to load chat") }
                    .collectLatest { allMessages ->
                        _uiState.value = ChatUiState.Success(
                            messages = allMessages,
                            currentUserId = currentUserId,
                            roomId = roomId,
                            typingUserId = typingUserId.value
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error("Unexpected error occurred")
            }
        }
    }

    fun sendMessage(roomId: String, senderId: String, text: String) {
        // Add validation
        if (text.isBlank() || text.length > 300) return

        viewModelScope.launch {
            try {
                // Update UI immediately
                val newMessage = createMessage(senderId, text)
                updateMessages(newMessage)

                // Send to Firestore
                repository.sendMessage(roomId, newMessage).onSuccess {
                    updateMessageStatus(newMessage.id, MessageStatus.SENT)
                }.onFailure { e ->
                    updateMessageStatus(newMessage.id, MessageStatus.FAILED)
                    Log.e("SEND_ERROR", "Failed to send", e)
                }
            } catch (e: Exception) {
                Log.e("SEND_ERROR", "Unexpected error", e)
            }
        }
    }
    val _otherUserTyping = MutableStateFlow<String?>(null)
    val otherUserTyping: StateFlow<String?> = _otherUserTyping
    private fun createMessage(senderId: String, text: String): ChatMessage {
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            senderId = senderId,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            clientGeneratedId = "${senderId}_${System.currentTimeMillis()}"
        )
    }
    fun observeTypingStatus(roomId: String, currentUserId: String) {
        Firebase.firestore.collection(CHATROOMS_COLLECTION)
            .document(roomId)
            .collection("typingStatus")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documents?.forEach { doc ->
                    val userId = doc.id
                    val isTyping = doc.getBoolean("isTyping") ?: false
                    if (userId != currentUserId && isTyping) {
                        _otherUserTyping.value = "$userId is typing..."
                    } else if (userId != currentUserId) {
                        _otherUserTyping.value = null
                    }
                }
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

    fun setTypingTemporarily(userId: String) {
        _typingUserId.value = userId
        viewModelScope.launch {
            delay(3000) // 3 seconds
            if (_typingUserId.value == userId) {
                _typingUserId.value = null
            }
        }
    }

    fun onNetworkRestored(roomId: String) {
        viewModelScope.launch {
            repository.syncMessageGaps(roomId)
            val failedMessages = repository.getFailedMessages(roomId)
            failedMessages.forEach { retryMessage(roomId, it) }
        }
    }

    fun updateTypingStatus(roomId: String, userId: String, isTyping: Boolean) {
        val typingRef = Firebase.firestore
            .collection(CHATROOMS_COLLECTION)
            .document(roomId)
            .collection("typingStatus")
            .document(userId)

        val data = mapOf(
            "isTyping" to isTyping,
            "timestamp" to FieldValue.serverTimestamp()
        )

        typingRef.set(data)
    }




}

sealed class ChatUiState {
    object Loading : ChatUiState()

    data class Success(
        val messages: List<ChatMessage>,
        val currentUserId: String,
        val roomId: String,
        val typingUserId: String? = null
    ) : ChatUiState()

    data class Error(val message: String) : ChatUiState()
}
