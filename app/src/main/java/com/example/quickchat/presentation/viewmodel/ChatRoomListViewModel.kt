package com.example.quickchat.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickchat.data.model.ChatRoom
import com.example.quickchat.data.repository.ChatRepository
import com.example.quickchat.data.repository.ChatRoomRepository
import kotlinx.coroutines.delay
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






    /* swipe tp delete logic */
// In ChatRoomListViewModel
    data class UndoAction(val roomId: String, val action: String)
    private val _showUndo = MutableStateFlow<UndoAction?>(null)
    val showUndo: StateFlow<UndoAction?> = _showUndo

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
                    // Debug log to verify rooms are being received
                    Log.d("ChatRooms", "Fetched ${rooms.size} rooms")
                }
        }
    }

    suspend fun markAsRead(roomId: String) {
        try {
            repository.updateLastReadTimestamp(
                roomId = roomId,
                userId = userId,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
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

    fun createChatRoom(otherUserId: String) {
        viewModelScope.launch {
            _roomCreationState.value = RoomCreationState.Loading
            try {
                val result = repository.createChatRoom(userId, otherUserId)
                if (result.isSuccess) {
                    _roomCreationState.value = RoomCreationState.Success(result.getOrThrow())
                    fetchChatRooms()
                } else {
                    _roomCreationState.value = RoomCreationState.Error(result.exceptionOrNull()?.message ?: "Failed to create room")
                }
            } catch (e: Exception) {
                _roomCreationState.value = RoomCreationState.Error(e.message ?: "Failed to create room")
            }
        }
    }

    fun resetRoomCreationState() {
        _roomCreationState.value = RoomCreationState.Idle
    }

    fun clearError() {
        _error.value = null
    }

    private val _muteOperations = mutableMapOf<String, Boolean>()


// In ChatRoomListViewModel
    private val _pendingMuteOperations = mutableMapOf<String, Boolean>()

// In ChatRoomListViewModel.kt
    fun toggleMuteStatus(roomId: String) {
        viewModelScope.launch {
            try {
                val currentRooms = _chatRooms.value
                val currentRoom = currentRooms.first { it.roomId == roomId }
                val newMutedState = !currentRoom.isMuted

                // 2. UI ko immediately update kare loading state dikhane ke liye
                _chatRooms.value = currentRooms.map { room ->
                    if (room.roomId == roomId) {
                        room.copy(isProcessingMute = true) // Sirf loading dikhao
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
    }    fun updateRoomsPreservingMuteState(newRooms: List<ChatRoom>) {
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
        viewModelScope.launch {
            val updatedRooms = _chatRooms.value.map { room ->
                if (room.roomId == roomId) {
                    // Only update last message if NOT muted
                    if (!room.isMuted) {
                        room.copy(
                            lastMessage = message,
                            lastTimestamp = System.currentTimeMillis(),
                            unreadCount = room.unreadCount + 1
                        )
                    } else {
                        // If muted, keep everything same but update timestamp
                        room.copy(lastTimestamp = System.currentTimeMillis())
                    }
                } else {
                    room
                }
            }
            _chatRooms.value = updatedRooms
        }
    }

}