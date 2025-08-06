package com.example.quickchat.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.model.MessageStatus
import com.example.quickchat.data.model.MessageType
import com.example.quickchat.data.repository.ChatRepository
import com.example.quickchat.data.repository.ChatRoomRepository
import com.example.quickchat.data.repository.PresenceRepository
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

private const val CHATROOMS_COLLECTION = "chatrooms"

class ChatViewModel(
    private val repository: ChatRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val presenceRepository: PresenceRepository
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

    private val _presenceStatus = MutableStateFlow<Boolean?>(null)
    val presenceStatus: StateFlow<Boolean?> = _presenceStatus

    private var currentRoomId: String? = null
    private var currentUserId: String? = null
    private var otherUserId: String? = null

    init {
        Log.d("VM_LIFECYCLE", "ViewModel INITIALIZED - Hash: ${hashCode()}")
    }

    suspend fun storeFcmToken(deviceId: String, token: String) {
        repository.storeFcmToken(deviceId, token)
    }

    override fun onCleared() {
        currentUserId?.let { userId ->
            updatePresence(userId, false)
        }
        super.onCleared()
    }

    fun observePresence(userId: String) {
        viewModelScope.launch {
            presenceRepository.observeUserPresence(userId).collect { isOnline ->
                _presenceStatus.value = isOnline
            }
        }
    }

    fun updatePresence(userId: String, isOnline: Boolean) {
        viewModelScope.launch {
            presenceRepository.updateUserPresence(userId, isOnline)
        }
    }

    fun onScreenEntered(roomId: String, userId: String) {
        viewModelScope.launch {
            repository.updateLastReadTimestamp(
                roomId = roomId,
                userId = userId,
                timestamp = System.currentTimeMillis()
            )

            _uiState.update { currentState ->
                if (currentState is ChatUiState.Success) {
                    currentState.copy()
                } else {
                    currentState
                }
            }
        }
    }

    fun markMessagesAsRead(roomId: String, userId: String) {
        viewModelScope.launch {
            try {
                repository.updateLastReadTimestamp(roomId, userId, System.currentTimeMillis())
                chatRoomRepository.markMessagesAsRead(roomId, userId)
                Log.d("ChatViewModel", "Messages marked as read for room $roomId")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error marking messages as read", e)
            }
        }
    }

    fun handleNotificationDeepLink(roomId: String, currentUserId: String) {
        viewModelScope.launch {
            markMessagesAsRead(roomId, currentUserId)
            initializeChat(roomId, currentUserId)
        }
    }

    fun getUnreadCount(roomId: String, userId: String): Flow<Int> {
        return chatRoomRepository.getUnreadCountFlow(roomId, userId)
    }

    fun initializeChat(roomId: String, currentUserId: String) {
        this.currentRoomId = roomId
        this.currentUserId = currentUserId

        viewModelScope.launch {
            try {
                // First get the other participant ID
                val participants = repository.getRoomParticipants(roomId)
                otherUserId = participants.firstOrNull { it != currentUserId }

                // Start observing presence
                otherUserId?.let { observePresence(it) }

                // Update our own presence
                updatePresence(currentUserId, true)

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
        thumbnailUrl: String? = null
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

    private val _otherUserTyping = MutableStateFlow<String?>(null)
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
            delay(3000)
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

data class CloudinaryUploadResponse(
    val secureUrl: String,
    val thumbnailUrl: String? = null
)