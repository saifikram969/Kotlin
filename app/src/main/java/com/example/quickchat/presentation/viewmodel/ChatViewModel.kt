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
import kotlinx.coroutines.tasks.await
import java.util.*

private const val CHATROOMS_COLLECTION = "chatrooms"

class ChatViewModel(
    private val repository: ChatRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val presenceRepository: PresenceRepository,


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

    // Typing status tracking
    private val _isTyping = MutableStateFlow(false)
    private val _otherUserTyping = MutableStateFlow<Boolean>(false)
    val otherUserTyping: StateFlow<Boolean> = _otherUserTyping

    init {
        // ... existing code ...
        setupStatusTracking()
    }

    private fun setupStatusTracking() {
        viewModelScope.launch {
            currentRoomId?.let { roomId ->
                currentUserId?.let { userId ->
                    repository.listenForMessageStatusUpdates(roomId, userId).collect { (messageId, status) ->
                        updateMessageStatus(messageId, status)
                    }
                }
            }
        }
    }

    init {
        Log.d("VM_LIFECYCLE", "ViewModel INITIALIZED - Hash: ${hashCode()}")
    }

    suspend fun storeFcmToken(deviceId: String, token: String) {
        repository.storeFcmToken(deviceId, token)
    }

    override fun onCleared() {
        currentUserId?.let { userId ->
            updatePresence(userId, false)
            currentRoomId?.let { roomId ->
                updateTypingStatus(roomId, userId, false)
            }
        }
        super.onCleared()
    }

    fun observePresence(userId: String) {
        viewModelScope.launch {
            // 1. Validate user ID first
            if (userId.isBlank()) {
                Log.e("ChatViewModel", "Cannot observe presence - empty user ID")
                _presenceStatus.value = null
                return@launch
            }

            // 2. Observe presence with proper error handling
            presenceRepository.observeUserPresence(userId)
                .onStart {
                    Log.d("ChatViewModel", "Starting presence observation for user: $userId")
                    _presenceStatus.value = null // Reset while loading
                }
                .catch { e ->
                    Log.e("ChatViewModel", "Error observing presence for $userId", e)
                    _presenceStatus.value = null
                }
                .collect { isOnline ->
                    Log.d("ChatViewModel", "Presence update for $userId: ${if (isOnline) "online" else "offline"}")
                    _presenceStatus.value = isOnline

                    // Update UI state if needed
                    _uiState.update { currentState ->
                        if (currentState is ChatUiState.Success) {
                            currentState.copy() // You can add presence info here if needed
                        } else {
                            currentState
                        }
                    }
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
                // Update last read timestamp
                repository.updateLastReadTimestamp(roomId, userId, System.currentTimeMillis())

                // Mark messages as read
                repository.markMessagesAsRead(roomId, userId)

                // Update UI
                val currentState = _uiState.value
                if (currentState is ChatUiState.Success) {
                    _uiState.value = currentState.copy(
                        messages = currentState.messages.map { message ->
                            if (message.senderId != userId && message.status != MessageStatus.SEEN) {
                                message.copy(status = MessageStatus.SEEN)
                            } else {
                                message
                            }
                        }
                    )
                }
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

    // Update the initializeChat function to properly handle message status updates
    fun initializeChat(roomId: String, currentUserId: String) {
        this.currentRoomId = roomId
        this.currentUserId = currentUserId

        viewModelScope.launch {
            try {
                // Get participants first
                val participants = repository.getRoomParticipants(roomId)
                otherUserId = participants.firstOrNull { it != currentUserId }

                // Mark messages as read immediately
                repository.markMessagesAsRead(roomId, currentUserId)

                // Observe messages with status updates
                repository.listenToMessages(roomId)
                    .collect { messages ->
                        val sortedMessages = messages.sortedBy { it.timestamp }
                        _uiState.value = ChatUiState.Success(
                            messages = sortedMessages,
                            currentUserId = currentUserId,
                            roomId = roomId,
                            typingUserId = if (_otherUserTyping.value) otherUserId else null
                        )

                        // After updating messages, mark any unread ones as seen
                        if (sortedMessages.any {
                                it.senderId != currentUserId &&
                                        it.status != MessageStatus.SEEN
                            }) {
                            repository.markMessagesAsRead(roomId, currentUserId)
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = ChatUiState.Error("Error initializing chat: ${e.message}")
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
                // Ensure typing status is set to false when sending message
                updateTypingStatus(roomId, senderId, false)
                _isTyping.value = false

                val messageType = when {
                    !imageUrl.isNullOrEmpty() -> MessageType.IMAGE
                    else -> MessageType.TEXT
                }

                val newMessage = createMessage(
                    senderId = senderId,
                    text = text ?: "",
                    messageType = messageType
                ).copy(
                    imageUrl = imageUrl,
                    status = MessageStatus.SENDING // Start with SENDING status

                )
                // Add to UI immediately with SENDING status
                updateMessages(newMessage)

                // Check network before attempting to send
                if (!repository.isNetworkAvailable()) {
                    updateMessageStatus(newMessage.id, MessageStatus.FAILED)
                    return@launch
                }


                repository.sendMessage(roomId, newMessage).onSuccess {
                    // Status will be updated by the snapshot listener
                }.onFailure { e ->
                    updateMessageStatus(newMessage.id, MessageStatus.FAILED)
                    Log.e("SEND_ERROR", "Failed to send message", e)
                }
            } catch (e: Exception) {
                Log.e("SEND_ERROR", "Unexpected error sending message", e)
            }
        }
    }

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

    fun observeTypingStatus(roomId: String, userId: String) {
        Firebase.firestore.collection(CHATROOMS_COLLECTION)
            .document(roomId)
            .collection("typingStatus")
            .document(userId)
            .addSnapshotListener { snapshot, _ ->
                val isTyping = snapshot?.getBoolean("isTyping") ?: false
                _otherUserTyping.value = isTyping

                // Update UI state with typing information
                _uiState.update { currentState ->
                    if (currentState is ChatUiState.Success) {
                        currentState.copy(typingUserId = if (isTyping) userId else null)
                    } else {
                        currentState
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

    // In ChatViewModel
    private fun updateMessageStatus(messageId: String, status: MessageStatus) {
        _uiState.update { currentState ->
            if (currentState is ChatUiState.Success) {
                val updatedMessages = currentState.messages.map {
                    if (it.id == messageId) it.copy(status = status) else it
                }
                currentState.copy(messages = updatedMessages)
            } else {
                currentState
            }
        }

        // Update in local database
        viewModelScope.launch {
            repository.updateMessageStatus(messageId, status)
        }
    }

    fun setTypingStatus(isTyping: Boolean) {
        currentRoomId?.let { roomId ->
            currentUserId?.let { userId ->
                viewModelScope.launch {
                    updateTypingStatus(roomId, userId, isTyping)
                    _isTyping.value = isTyping
                }
            }
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
    fun enterChatRoom(userId: String) {
        viewModelScope.launch {
            presenceRepository.updateUserPresence(userId, true)
        }
    }





    // In ChatViewModel
    fun onNetworkRestored(roomId: String) {
        viewModelScope.launch {
            // 1. First sync any message gaps
            repository.syncMessageGaps(roomId)

            // 2. Get all failed messages
            val failedMessages = repository.getFailedMessages(roomId)

            // 3. Retry each with exponential backoff
            failedMessages.forEachIndexed { index, message ->
                launch {
                    // Exponential backoff: 1s, 2s, 4s, etc.
                    delay((1L shl index.coerceAtMost(5)) * 1000)
                    retryMessage(roomId, message)
                }
            }
        }
    }
//for delete chatroom
// In ChatViewModel.kt
// In ChatViewModel.kt
fun deleteChatroom(roomId: String) {
    viewModelScope.launch {
        try {
            // 1. Get current state
            val currentState = _uiState.value
            val currentUserId = currentUserId ?: return@launch

            // 2. Delete from Firestore
            Firebase.firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .delete()
                .await()

            // 3. Delete all messages (batch operation)
            val messages = Firebase.firestore.collection(CHATROOMS_COLLECTION)
                .document(roomId)
                .collection("messages")
                .get()
                .await()

            val batch = Firebase.firestore.batch()
            messages.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()

            // 4. Clear local cache
            repository.clearRoomCache(roomId)

            // 5. Update UI state - set to empty if this was the current room
            if (currentState is ChatUiState.Success && currentState.roomId == roomId) {
                _uiState.value = ChatUiState.Success(
                    messages = emptyList(),
                    currentUserId = currentUserId,
                    roomId = roomId, // Keep same roomId to trigger recomposition
                    typingUserId = null
                )
            }

            // 6. Notify chat list to refresh
            _refreshChatList.value = true

        } catch (e: Exception) {
            Log.e("ChatViewModel", "Delete error", e)
            _uiState.value = ChatUiState.Error("Delete failed: ${e.message}")
        }
    }
}

    // Add this to your ViewModel's properties
    private val _refreshChatList = MutableStateFlow(false)
    val refreshChatList: StateFlow<Boolean> = _refreshChatList
    private suspend fun sendDeletionNotification(roomId: String, senderId: String, recipientId: String) {
        try {
            // Get recipient's FCM token
            val recipientToken = repository.getFcmToken(recipientId) ?: return

            // Get sender's name
            val senderName = Firebase.firestore.collection("users")
                .document(senderId)
                .get()
                .await()
                .getString("name") ?: senderId

            // Prepare notification payload
            val payload = mapOf(
                "to" to recipientToken,
                "notification" to mapOf(
                    "title" to "Chat deleted",
                    "body" to "$senderName deleted the chat",
                    "click_action" to "FLUTTER_NOTIFICATION_CLICK"
                ),
                "data" to mapOf(
                    "type" to "chat_deleted",
                    "roomId" to roomId,
                    "senderId" to senderId
                )
            )

            // Send notification (you might want to send this to your backend instead)
            Firebase.firestore.collection("notification_requests")
                .add(payload)
                .await()
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error sending deletion notification", e)
        }
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