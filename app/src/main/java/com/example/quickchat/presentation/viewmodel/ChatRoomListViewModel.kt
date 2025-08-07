package com.example.quickchat.presentation.viewmodel
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickchat.data.model.ChatRoom
import com.example.quickchat.data.repository.ChatRepository
import com.example.quickchat.data.repository.ChatRoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatRoomListViewModel @Inject constructor(
    private val repository: ChatRoomRepository,
    private val chatRepository: ChatRepository,
    private val userId: String
) : ViewModel() {

    // Chat Rooms State
    private val _chatRooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    val chatRooms: StateFlow<List<ChatRoom>> = _chatRooms

    // Loading State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Error State
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Room Creation State
    private val _roomCreationState = MutableStateFlow<RoomCreationState>(RoomCreationState.Idle)
    val roomCreationState: StateFlow<RoomCreationState> = _roomCreationState

    sealed class RoomCreationState {
        object Idle : RoomCreationState()
        object Loading : RoomCreationState()
        data class Success(val roomId: String) : RoomCreationState()
        data class Error(val message: String) : RoomCreationState()
    }
    /* swipe to delete logic */
    data class UndoAction(val roomId: String, val action: String)
    private val _showUndo = MutableStateFlow<UndoAction?>(null)
    val showUndo: StateFlow<UndoAction?> = _showUndo

    init {
        println("ViewModel initialized with userId: $userId")
        fetchChatRooms()
    }

    fun fetchChatRooms() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.getChatRooms(userId)
                .catch { e ->
                    _error.value = "Failed to load chat rooms: ${e.message}"
                    _isLoading.value = false
                }
                .collectLatest { rooms ->
                    _chatRooms.value = rooms
                    _isLoading.value = false
                    Log.d("ChatRooms", "Fetched ${rooms.size} rooms")
                }
        }
    }

    // Removed the 'override' keyword since this isn't overriding anything
    suspend fun markAsRead(roomId: String) {
        Log.d("UNREAD_DEBUG", " ViewModel markAsRead called for room $roomId")
        try {
            repository.markMessagesAsRead(roomId, userId)

            _chatRooms.value = _chatRooms.value.map { room ->
                if (room.roomId == roomId) {
                    Log.d("UNREAD_DEBUG", " Updating UI state for room $roomId")
                    room.copy(unreadCount = 0)
                } else {
                    room
                }
            }
        } catch (e: Exception) {
            Log.e("UNREAD_DEBUG", " Error in markAsRead: ${e.message}", e)
            _error.value = "Failed to mark as read: ${e.message}"
        }
    }

    fun onRoomClicked(roomId: String, otherUserId: String, onNavigate: (String) -> Unit) {
        viewModelScope.launch {
            try {
                markAsRead(roomId)
                onNavigate("chat/$userId/$roomId/$otherUserId")
            } catch (e: Exception) {
                _error.value = "Navigation failed: ${e.message}"
            }
        }
    }

    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            try {
                _chatRooms.value = _chatRooms.value.filter { it.roomId != roomId }
                repository.deleteRoom(roomId)
                _showUndo.value = UndoAction(roomId, "deleted")
            } catch (e: Exception) {
                _error.value = "Failed to delete room: ${e.message}"
                fetchChatRooms()
            }
        }
    }

    fun archiveRoom(roomId: String) {
        viewModelScope.launch {
            try {
                _chatRooms.value = _chatRooms.value.filter { it.roomId != roomId }
                repository.archiveRoom(roomId, true)
                _showUndo.value = UndoAction(roomId, "archived")
            } catch (e: Exception) {
                _error.value = "Failed to archive room: ${e.message}"
                fetchChatRooms()
            }
        }
    }

    fun undoAction(roomId: String, action: String) {
        viewModelScope.launch {
            try {
                when (action) {
                    "archived" -> repository.archiveRoom(roomId, false)
                    "deleted" -> repository.restoreRoom(roomId)
                }
                fetchChatRooms()
            } catch (e: Exception) {
                _error.value = "Failed to undo $action: ${e.message}"
            } finally {
                clearUndo()
            }
        }
    }

    fun clearUndo() {
        _showUndo.value = null
    }

    fun createChatRoom(otherUserId: String) {
        viewModelScope.launch {
            _roomCreationState.value = RoomCreationState.Loading
            try {
                // Check if room already exists
                val potentialRoomId = listOf(userId, otherUserId).sorted().joinToString("_")
                val roomExists = repository.doesRoomExist(potentialRoomId)

                if (roomExists) {
                    _roomCreationState.value = RoomCreationState.Error("Chat already exists")
                } else {
                    val result = repository.createChatRoom(userId, otherUserId)
                    if (result.isSuccess) {
                        _roomCreationState.value = RoomCreationState.Success(result.getOrThrow())
                        fetchChatRooms() // Refresh the list
                    } else {
                        _roomCreationState.value = RoomCreationState.Error(
                            result.exceptionOrNull()?.message ?: "Failed to create room"
                        )
                    }
                }
            } catch (e: Exception) {
                _roomCreationState.value = RoomCreationState.Error(
                    e.message ?: "Failed to create room"
                )
            }
        }
    }



    fun resetRoomCreationState() {
        _roomCreationState.value = RoomCreationState.Idle
    }

    fun clearError() {
        _error.value = null
    }

    fun toggleMuteStatus(roomId: String) {
        viewModelScope.launch {
            try {
                val currentRooms = _chatRooms.value
                val currentRoom = currentRooms.first { it.roomId == roomId }
                val newMutedState = !currentRoom.isMuted

                _chatRooms.value = currentRooms.map { room ->
                    if (room.roomId == roomId) {
                        room.copy(isProcessingMute = true)
                    } else {
                        room
                    }
                }

                repository.toggleMuteStatus(roomId, newMutedState)

                _chatRooms.value = _chatRooms.value.map { room ->
                    if (room.roomId == roomId) {
                        room.copy(
                            isProcessingMute = false,
                            isMuted = newMutedState
                        )
                    } else {
                        room
                    }
                }

            } catch (e: Exception) {
                _chatRooms.value = _chatRooms.value.map { room ->
                    if (room.roomId == roomId) {
                        room.copy(isProcessingMute = false)
                    } else {
                        room
                    }
                }
                _error.value = "Failed to toggle mute status: ${e.message}"
            }
        }
    }

    fun updateRoomsPreservingMuteState(newRooms: List<ChatRoom>) {
        val currentRooms = _chatRooms.value
        _chatRooms.value = newRooms.map { newRoom ->
            currentRooms.find { it.roomId == newRoom.roomId }?.let { currentRoom ->
                newRoom.copy(
                    isMuted = currentRoom.isMuted,
                    pendingMuteState = currentRoom.pendingMuteState,
                    isProcessingMute = currentRoom.isProcessingMute
                )
            } ?: newRoom
        }
    }

    fun onNewMessageReceived(roomId: String, message: String) {
        Log.d("lao", " New message received in room $roomId")
        viewModelScope.launch {
            try {
                val currentRoom = _chatRooms.value.firstOrNull { it.roomId == roomId }

                if (currentRoom != null) {
                    if (!currentRoom.isMuted) {
                        Log.d("lalo", " Room is not muted, incrementing count")
                        repository.incrementUnreadCount(roomId, userId)

                        _chatRooms.value = _chatRooms.value.map { room ->
                            if (room.roomId == roomId) {
                                val newCount = room.unreadCount + 1
                                Log.d("lalo", " Updating UI count to $newCount")
                                room.copy(
                                    lastMessage = message,
                                    lastTimestamp = System.currentTimeMillis(),
                                    unreadCount = newCount
                                )
                            } else {
                                room
                            }
                        }
                    } else {
                        Log.d("lalo", " Room is muted, not incrementing count")
                        _chatRooms.value = _chatRooms.value.map { room ->
                            if (room.roomId == roomId) {
                                room.copy(
                                    lastMessage = message,
                                    lastTimestamp = System.currentTimeMillis()
                                )
                            } else {
                                room
                            }
                        }
                    }
                } else {
                    Log.e("lalo", " Room $roomId not found in UI state!")
                }
            } catch (e: Exception) {
                Log.e("lalo", " Error in onNewMessageReceived: ${e.message}", e)
            }
        }
    }}