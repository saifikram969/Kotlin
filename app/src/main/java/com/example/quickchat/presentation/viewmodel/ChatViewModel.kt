package com.example.quickchat.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.model.MessageStatus
import com.example.quickchat.data.model.MessageType
import com.example.quickchat.data.model.TypingIndicator
import com.example.quickchat.data.repository.ChatRepository
import com.google.firebase.Firebase
import android.net.Uri
import com.example.quickchat.data.model.CloudinaryUploadResponse

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
private const val CHATROOMS_COLLECTION = "chatrooms" // lowercase everywhere

class ChatViewModel(private val repository: ChatRepository,
    //private val cloudinaryRepository: CloudinaryRepository
) : ViewModel() {

    private val _uploadResult = MutableStateFlow<CloudinaryUploadResponse?>(null)
    val uploadResult: StateFlow<CloudinaryUploadResponse?> = _uploadResult

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading

    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError


    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState

    private val _typingUserId = MutableStateFlow<String?>(null)
    val typingUserId: StateFlow<String?> = _typingUserId


    init {
        Log.d("VM_LIFECYCLE", "ViewModel INITIALIZED - Hash: ${hashCode()}")
    }
    fun uploadImage(uri: Uri) {
        _uploading.value = true
        viewModelScope.launch {
            try {
              //  val result = cloudinaryRepository.uploadImage(uri)
              //  _uploadResult.value = result
            } catch (e: Exception) {
                _uploadError.value = "Failed to upload image: ${e.message}"
                Log.e("ChatViewModel", "Image upload failed", e)
            } finally {
                _uploading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun onScreenEntered(roomId: String, userId: String) {
        viewModelScope.launch {
            // Mark all messages as read when entering the chat
            repository.updateLastReadTimestamp(
                roomId = roomId,
                userId = userId,
                timestamp = System.currentTimeMillis()
            )

            // Also update local unread count
            _uiState.update { currentState ->
                if (currentState is ChatUiState.Success) {
                    currentState.copy(
                        // Reset unread count for this room
                    )
                } else {
                    currentState
                }
            }
        }
    }

    // Add this to handle notification deep links
    fun handleDeepLink(roomId: String, currentUserId: String) {
        onScreenEntered(roomId, currentUserId)
        initializeChat(roomId, currentUserId)
    }

    fun markMessagesAsRead(roomId: String, userId: String) {
        viewModelScope.launch {
            try {
                repository.updateLastReadTimestamp(roomId, userId, System.currentTimeMillis())
                Log.d("ChatViewModel", "Messages marked as read for room $roomId")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error marking messages as read", e)
            }
        }
    }

    fun initializeChat(roomId: String, currentUserId: String) {
        viewModelScope.launch {
            try {
                // Combine cached and remote messages
                repository.listenToMessages(roomId)
                    .catch { e ->
                        _uiState.value = ChatUiState.Error("Failed to load chat: ${e.message}")
                        Log.e("ChatViewModel", "Error listening to messages", e)
                    }
                    .collect { messages ->
                        val sortedMessages = messages.sortedBy { it.timestamp }
                        _uiState.value = ChatUiState.Success(
                            messages = sortedMessages,
                            currentUserId = currentUserId,
                            roomId = roomId,
                            typingUserId = typingUserId.value
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error("Unexpected error occurred: ${e.message}")
                Log.e("ChatViewModel", "Error initializing chat", e)
            }
        }
    }
    fun sendMessage(
        roomId: String,
        senderId: String,
        text: String? = null,
        imageUrl: String? = null,
        thumbnailUrl: String? =  null

    ) {
        viewModelScope.launch {
            try {
                val messageType = when {
                    !imageUrl.isNullOrEmpty() -> MessageType.IMAGE
                    else -> MessageType.TEXT
                }

                val newMessage = createMessage(
                    senderId = senderId,
                    text = text ?: "",
                    messageType = messageType
                ).copy(
                    imageUrl = imageUrl
                )

                updateMessages(newMessage)

                repository.sendMessage(roomId, newMessage).onSuccess {
                    updateMessageStatus(newMessage.id, MessageStatus.SENT)
                }.onFailure { e ->
                    updateMessageStatus(newMessage.id, MessageStatus.FAILED)
                    Log.e("SEND_ERROR", "Failed to send message", e)
                }
            } catch (e: Exception) {
                Log.e("SEND_ERROR", "Unexpected error sending message", e)
            }
        }
    }

    fun sendImageMessage(roomId: String, senderId: String, imageUrl: String) {
        viewModelScope.launch {
            try {
                val newMessage = createMessage(senderId, "", MessageType.IMAGE).copy(
                    imageUrl = imageUrl
                )
                updateMessages(newMessage)

                repository.sendMessage(roomId, newMessage).onSuccess {
                    updateMessageStatus(newMessage.id, MessageStatus.SENT)
                }.onFailure { e ->
                    updateMessageStatus(newMessage.id, MessageStatus.FAILED)
                    Log.e("SEND_ERROR", "Failed to send image", e)
                }
            } catch (e: Exception) {
                Log.e("SEND_ERROR", "Unexpected error sending image", e)
            }
        }
    }

    val _otherUserTyping = MutableStateFlow<String?>(null)
    val otherUserTyping: StateFlow<String?> = _otherUserTyping

    private fun createMessage(
        senderId: String,
        text: String,
        messageType: MessageType,
        imageUrl: String? = null
    ): ChatMessage {
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            senderId = senderId,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            clientGeneratedId = "${senderId}_${System.currentTimeMillis()}",
            messageType = messageType,
            imageUrl = imageUrl
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