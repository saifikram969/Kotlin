package com.example.quickchat.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickchat.data.model.ChatRoom
import com.example.quickchat.data.repository.ChatRoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatRoomListViewModel @Inject constructor(
    private val repository: ChatRoomRepository,
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

    init {
        println("ViewModel initialized with userId: $userId")
        fetchChatRooms()
    }
    fun fetchChatRooms(userId: String = this.userId) {

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.getChatRooms(userId)
                .catch { e ->
                    _error.value = "Failed to load chat rooms: ${e.message}"
                    _isLoading.value = false
                }
                .collect { rooms ->
                    println("Received rooms: ${rooms.size} rooms")
                    _chatRooms.value = rooms
                    _isLoading.value = false
                    println("Fetched rooms: $rooms")
                }
        }
    }
    suspend fun markAsRead(roomId: String, userId: String) {
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

    fun onRoomClicked(roomId: String, userId: String, onNavigate: (String) -> Unit) {
        viewModelScope.launch {
            try {
                markAsRead(roomId, userId)
                onNavigate(roomId)
            } catch (e: Exception) {
                _error.value = "Navigation failed: ${e.message}"
            }
        }
    }

    // Other methods can use this.userId directly
    fun createChatRoom(otherUserId: String) {
        viewModelScope.launch {
            repository.createChatRoom(this@ChatRoomListViewModel.userId, otherUserId)
        }
    }

    fun resetRoomCreationState() {
        _roomCreationState.value = RoomCreationState.Idle
    }

    fun clearError() {
        _error.value = null
    }
}