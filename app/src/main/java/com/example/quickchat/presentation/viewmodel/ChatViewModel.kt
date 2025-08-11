package com.example.quickchat.presentation.viewmodel

import android.R.id.message
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.model.MessageStatus
import com.example.quickchat.data.model.MessageType
import com.example.quickchat.data.repository.ChatRepository
import com.example.quickchat.data.repository.ChatRoomRepository
import com.example.quickchat.data.repository.PresenceRepository
import com.example.quickchat.utils.ConnectivityObserver
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
    private val connectivityObserver: ConnectivityObserver

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

    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages

    private val _uploadingMessages = mutableStateMapOf<String, ChatMessage>()
    val uploadingMessages: Map<String, ChatMessage> get() = _uploadingMessages

    private var currentRoomId: String? = null
    private var currentUserId: String? = null
    private var otherUserId: String? = null

     private val _isTyping = MutableStateFlow(false)
    private val _otherUserTyping = MutableStateFlow<Boolean>(false)
    val otherUserTyping: StateFlow<Boolean> = _otherUserTyping

    private val _tempMessages = mutableStateListOf<ChatMessage>()
    val tempMessages: List<ChatMessage> get() = _tempMessages



    init {
        setupStatusTracking()
    }

    private val _networkStatus = MutableStateFlow(true)
    val networkStatus: StateFlow<Boolean> = _networkStatus

    init {
        observeNetworkStatus()
    }

    private fun observeNetworkStatus() {
        viewModelScope.launch {
            connectivityObserver.observe().collect { isConnected ->
                _networkStatus.value = isConnected
                if (isConnected) {
                    currentRoomId?.let { roomId ->
                        repository.syncMissingMessages(roomId)
                        retryFailedMessages(roomId)
                    }
                }
            }
        }
    }

    private suspend fun retryFailedMessages(roomId: String) {
        val failedMessages = repository.getFailedMessages(roomId)
        failedMessages.forEach { message ->
            repository.sendMessageWithOfflineSupport(roomId, message.copy(
                timestamp = System.currentTimeMillis()
            ))
        }
    }


    private fun setupStatusTracking() {
        viewModelScope.launch {
            currentRoomId?.let { roomId ->
                currentUserId?.let { userId ->
                    repository.listenForMessageStatusUpdates(roomId, userId)
                        .collect { (messageId, status) ->
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
            if (userId.isBlank()) {
                Log.e("ChatViewModel", "Cannot observe presence - empty user ID")
                _presenceStatus.value = null
                return@launch
            }

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
                    Log.d(
                        "ChatViewModel",
                        "Presence update for $userId: ${if (isOnline) "online" else "offline"}"
                    )
                    _presenceStatus.value = isOnline

                    // Update UI state if needed
                    _uiState.update { currentState ->
                        if (currentState is ChatUiState.Success) {
                            currentState.copy()
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
                repository.updateLastReadTimestamp(roomId, userId, System.currentTimeMillis())

                repository.markMessagesAsRead(roomId, userId)

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

    fun initializeChat(roomId: String, currentUserId: String) {
        this.currentRoomId = roomId
        this.currentUserId = currentUserId

        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading

            val cachedMessages = repository.getCachedMessages(roomId)
            if (cachedMessages.isNotEmpty()) {
                _uiState.value = ChatUiState.Success(
                    messages = cachedMessages,
                    currentUserId = currentUserId,
                    roomId = roomId
                )
            }

            if (networkStatus.value == true) {
                try {
                    repository.syncMissingMessages(roomId)
                    val updatedMessages = repository.getCachedMessages(roomId)
                    _uiState.value = if (updatedMessages.isNotEmpty()) {
                        ChatUiState.Success(
                            messages = updatedMessages,
                            currentUserId = currentUserId,
                            roomId = roomId
                        )
                    } else {
                        ChatUiState.Error("No messages found")
                    }
                } catch (e: Exception) {
                    Log.e("ChatVM", "Sync failed", e)
                    if (cachedMessages.isNotEmpty()) {
                        _uiState.value = ChatUiState.Success(
                            messages = cachedMessages,
                            currentUserId = currentUserId,
                            roomId = roomId,
                            isOffline = true
                        )
                    } else {
                        _uiState.value = ChatUiState.Error(
                            "Offline: No cached messages available"
                        )
                    }
                }
            } else if (cachedMessages.isEmpty()) {
                _uiState.value = ChatUiState.Error(
                    "Offline: No cached messages available"
                )
            }

            if (networkStatus.value == true) {
                repository.listenToMessages(roomId).collect { messages ->
                    _uiState.value = ChatUiState.Success(
                        messages = messages.sortedBy { it.timestamp },
                        currentUserId = currentUserId,
                        roomId = roomId
                    )
                }
            }
        }
    }

    fun addUploadingMessage(message: ChatMessage) {
        _uploadingMessages[message.id] = message
    }

    fun updateUploadingMessage(
        messageId: String,
        fileUrl: String? = null,
        status: MessageStatus? = null,
        uploadProgress: Float? = null
    ) {
        _uploadingMessages[messageId]?.let { existing ->
            _uploadingMessages[messageId] = existing.copy(
                fileUrl = fileUrl ?: existing.fileUrl,
                status = status ?: existing.status,
                uploadProgress = uploadProgress ?: existing.uploadProgress
            )
        }
    }

    fun removeUploadingMessage(messageId: String) {
        _uploadingMessages.remove(messageId)
    }

    fun sendMessage(
        roomId: String,
        senderId: String,
        text: String? = null,
        imageUrl: String? = null,
        thumbnailUrl: String? = null,
        fileUrl: String? = null,
        fileType: String? = null,
        fileName: String? = null,
        fileSize: Long? = null
    ) {
        Log.d("SendMessage", " sendMessage called → roomId=$roomId, senderId=$senderId")
        Log.d("SendMessage", "Parameters → text=$text, imageUrl=$imageUrl, fileUrl=$fileUrl, fileType=$fileType, fileName=$fileName, fileSize=$fileSize")

        viewModelScope.launch {
            val messageType = when {
                !imageUrl.isNullOrBlank() -> MessageType.IMAGE
                !fileUrl.isNullOrBlank() -> when {
                    fileType?.startsWith("audio/") == true -> MessageType.AUDIO
                    fileType?.startsWith("application/pdf") == true -> MessageType.PDF
                    else -> MessageType.FILE
                }
                else -> MessageType.TEXT
            }
            Log.d("SendMessage", "📄 Determined messageType=$messageType")

            val message = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = text ?: "",
                senderId = senderId,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENDING,
                messageType = messageType,
                imageUrl = imageUrl,
                thumbnailUrl = thumbnailUrl,
                fileUrl = fileUrl,
                fileType = fileType ?: "",
                fileName = fileName ?: "",
                fileSize = fileSize
            )
            Log.d("SendMessage", " Created ChatMessage object: $message")
            updateMessages(message)

            repository.sendMessageWithOfflineSupport(roomId, message)
                .onSuccess {
                    updateMessageStatus(message.id, MessageStatus.SENT)
                }
                .onFailure { e ->
                    updateMessageStatus(message.id, MessageStatus.FAILED)
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
        _uiState.update { current ->
            if (current is ChatUiState.Success) {
                current.copy(
                    messages = (current.messages + newMessage)
                        .distinctBy { it.id }
                        .sortedBy { it.timestamp }
                )
            } else current
        }
    }
    private fun updateMessageStatus(messageId: String, status: MessageStatus) {
        _uiState.update { current ->
            if (current is ChatUiState.Success) {
                current.copy(
                    messages = current.messages.map {
                        if (it.id == messageId) it.copy(status = status) else it
                    }
                )
            } else current
        }

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

    fun onNetworkRestored(roomId: String) {
        viewModelScope.launch {
            repository.syncMessageGaps(roomId)

            val failedMessages = repository.getFailedMessages(roomId)

            failedMessages.forEachIndexed { index, message ->
                launch {
                    delay((1L shl index.coerceAtMost(5)) * 1000)
                    retryMessage(roomId, message)
                }
            }
        }
    }

    fun deleteChatroom(roomId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                // 1. Mark as deleted in Firestore
                Firebase.firestore.collection(CHATROOMS_COLLECTION)
                    .document(roomId)
                    .update(
                        mapOf(
                            "isDeleted" to true,
                            "lastUpdated" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()

                // 2. Clear local cache
                repository.clearRoomCache(roomId)

                // 3. Update UI state - changed from Empty to Loading
                if (_uiState.value is ChatUiState.Success &&
                    (_uiState.value as ChatUiState.Success).roomId == roomId
                ) {
                    _uiState.value = ChatUiState.Loading
                }
                _refreshChatList.value = true

                onComplete()

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Delete error", e)
                _uiState.value = ChatUiState.Error("Delete failed: ${e.message}")
            }
        }
    }

    // Add this to your ViewModel's properties
    private val _refreshChatList = MutableStateFlow(false)
    val refreshChatList: StateFlow<Boolean> = _refreshChatList
    private suspend fun sendDeletionNotification(
        roomId: String,
        senderId: String,
        recipientId: String
    ) {
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

    // delete user in chatroom with all chats deleted
    fun deleteChatroomForUser(
        roomId: String,
        userId: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                // 1. Remove the user from participants
                Firebase.firestore.collection(CHATROOMS_COLLECTION)
                    .document(roomId)
                    .update(
                        mapOf(
                            "participants" to FieldValue.arrayRemove(userId),
                            "lastUpdated" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()

                // Delete all messages sent by this user
                deleteUserMessages(roomId, userId)

                // Clear from local DB (both room + messages)
                repository.clearRoomCache(roomId)
                repository.clearMessagesForUserInRoom(
                    roomId,
                    userId
                )
                onComplete()

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error leaving chatroom", e)
                _uiState.value = ChatUiState.Error("Failed to leave chatroom: ${e.message}")
            }
        }
    }

    fun addTempMessage(message: ChatMessage) {
        _tempMessages.add(message.copy(isTemp = true))
        updateMessages(message.copy(isTemp = true))
    }

    fun updateTempMessage(
        messageId: String,
        fileUrl: String? = null,
        status: MessageStatus,
        isTemp: Boolean = false,
        uploadProgress: Float? = null
    ) {
        _tempMessages.replaceAll { msg ->
            if (msg.id == messageId) msg.copy(
                fileUrl = fileUrl,
                status = status,
                uploadProgress = uploadProgress
            ) else msg
        }

        _uiState.update { current ->
            if (current is ChatUiState.Success) {
                current.copy(messages = current.messages.map {
                    if (it.id == messageId) it.copy(
                        fileUrl = fileUrl,
                        status = status,
                        uploadProgress = uploadProgress
                    ) else it
                })
            } else current
        }
    }
    
    fun removeTempMessage(messageId: String) {
        _tempMessages.removeAll { it.id == messageId }
    }
    
    private suspend fun deleteUserMessages(roomId: String, userId: String) {
        val messagesSnapshot = Firebase.firestore.collection(CHATROOMS_COLLECTION)
            .document(roomId)
            .collection("messages")
            .whereEqualTo("senderId", userId)
            .get()
            .await()

        if (messagesSnapshot.isEmpty) return

        val batch = Firebase.firestore.batch()
        for (doc in messagesSnapshot.documents) {
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }
    
}

sealed class ChatUiState {
    object Loading : ChatUiState()

    data class Success(
        val messages: List<ChatMessage>,
        val currentUserId: String,
        val roomId: String,
        val typingUserId: String? = null,
        val isOffline: Boolean = false
    ) : ChatUiState()

    data class Error(val message: String) : ChatUiState()
}

data class CloudinaryUploadResponse(
    val secureUrl: String,
    val thumbnailUrl: String? = null
)