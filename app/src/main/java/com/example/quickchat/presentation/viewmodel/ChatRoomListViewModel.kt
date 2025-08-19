package com.example.quickchat.presentation.viewmodel
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickchat.data.model.ChatRoom
import com.example.quickchat.data.model.GroupMember
import com.example.quickchat.data.model.User
import com.example.quickchat.data.repository.ChatRepository
import com.example.quickchat.data.repository.ChatRoomRepository
import com.example.quickchat.data.repository.PresenceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class ChatRoomListViewModel @Inject constructor(
    private val repository: ChatRoomRepository,
    private val chatRepository: ChatRepository,
    private val presenceRepository: PresenceRepository,
    private val userId: String
) : ViewModel(), GroupMemberHandler {
    private val currentUserId = userId
    // In ChatRoomListViewModel.kt
    private val _groupMembers = MutableStateFlow<List<GroupMember>>(emptyList())
    override val groupMembers: StateFlow<List<GroupMember>> = _groupMembers.asStateFlow()

    private val _isLoadingMembers = MutableStateFlow(false)
    val isLoadingMembers: StateFlow<Boolean> = _isLoadingMembers.asStateFlow()

    private val _presenceStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    override val presenceStatus: StateFlow<Map<String, Boolean>> = _presenceStatus.asStateFlow()

    private val _chatRooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    val chatRooms: StateFlow<List<ChatRoom>> = _chatRooms
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun refreshChatRooms(forceRefresh: Boolean = false) {
        fetchChatRooms(forceRefresh)
    }

    // Loading State
    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading

    // Error State
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Room Creation State
    private val _roomCreationState = MutableStateFlow<RoomCreationState>(RoomCreationState.Idle)
    val roomCreationState: StateFlow<RoomCreationState> = _roomCreationState

//available uses
// In ChatRoomListViewModel.kt
private val _availableUsers = MutableStateFlow<List<User>>(emptyList())
    private val _isLoadingUsers = MutableStateFlow(false)
    val availableUsers: StateFlow<List<User>> = _availableUsers.asStateFlow()
    val isLoadingUsers: StateFlow<Boolean> = _isLoadingUsers.asStateFlow()



    // In ChatRoomListViewModel.kt
    fun loadAvailableUsers(roomId: String) {
        viewModelScope.launch {
            _isLoadingUsers.value = true
            try {
                val users = repository.getAvailableUsersToAdd(roomId)
                    .filter { it.deviceId != userId } // Exclude current user
                    .sortedBy { it.userName }

                Log.d("VM_DEBUG", "Loaded ${users.size} users")
                users.forEach { user ->
                    Log.d("VM_DEBUG", "User: ${user.deviceId} - ${user.userName}")
                }

                _availableUsers.value = users
            } catch (e: Exception) {
                Log.e("VM_ERROR", "Error loading users", e)
                _availableUsers.value = emptyList()
            } finally {
                _isLoadingUsers.value = false
            }
        }
    }

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

    // Modify the fetchChatRooms function
    fun fetchChatRooms(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (forceRefresh) {
                    repository.syncRoomsWithFirestore(userId)
                }

                repository.getChatRooms(userId)
                    .distinctUntilChanged()
                    .catch { e ->
                        _error.value = "Error: ${e.message}"
                        emit(emptyList())
                    }
                    .collect { rooms ->
                        _chatRooms.value = rooms
                            .sortedByDescending { it.lastTimestamp }
                            .map { room ->
                                // Preserve local UI state
                                _chatRooms.value.find { it.roomId == room.roomId }
                                    ?.let { existing ->
                                        room.copy(
                                            isMuted = existing.isMuted,
                                            isProcessingMute = existing.isProcessingMute
                                        )
                                    } ?: room
                            }
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _error.value = "Failed to load: ${e.message}"
                _isLoading.value = false
            }
        }
    }


/*    // In ChatRoomListViewModel.kt
    fun getMemberWithPresence(userId: String): GroupMember? {
        return _groupMembers.value.find { it.userId == userId }?.let { member ->
            member.copy(isOnline = _presenceStatus.value[userId] ?: false)
        }
    }

    fun getCurrentMembersWithPresence(): List<GroupMember> {
        return _groupMembers.value.map { member ->
            member.copy(isOnline = _presenceStatus.value[member.userId] ?: false)
        }
    }*/
// Implement interface methods (same as GroupInfoViewModel)
override fun loadGroupMembers(roomId: String) {
    viewModelScope.launch {
        _isLoading.value = true
        try {
            _groupMembers.value = repository.getGroupMembers(roomId)
            _groupMembers.value.forEach { member ->
                observePresence(member.userId)
            }
        } catch (e: Exception) {
            // Handle error
        } finally {
            _isLoading.value = false
        }
    }
}

    override fun getMemberWithPresence(userId: String): GroupMember? {
        return _groupMembers.value.find { it.userId == userId }?.let { member ->
            member.copy(isOnline = _presenceStatus.value[userId] ?: false)
        }
    }

    override fun getCurrentMembersWithPresence(): List<GroupMember> {
        return _groupMembers.value.map { member ->
            member.copy(isOnline = _presenceStatus.value[member.userId] ?: false)
        }
    }

    // Private helper (same as GroupInfoViewModel)
    override fun observePresence(userId: String) {
        viewModelScope.launch {
            presenceRepository.observeUserPresence(userId).collect { isOnline ->
                _presenceStatus.update { current ->
                    current + (userId to isOnline)
                }
            }
        }
    }


    override fun onCleared() {
        // Clean up any presence observers if needed
        super.onCleared()
    }


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

    // Add this to ChatRoomListViewModel.kt
    fun deleteChatroom(roomId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteRoom(roomId) // Firestore + local delete
                onSuccess() // No manual filtering — let listener update UI
            } catch (e: Exception) {
                _error.value = "Failed to delete chatroom: ${e.message}"
            }
        }
    }


    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            try {
                // Optimistically update UI
                _chatRooms.value = _chatRooms.value.filter { it.roomId != roomId }

                // Perform actual deletion
                repository.deleteRoom(roomId)

                // Show undo option
                _showUndo.value = UndoAction(roomId, "deleted")
            } catch (e: Exception) {
                // If error occurs, revert UI state
                _error.value = "Failed to delete room: ${e.message}"
                fetchChatRooms() // Refresh the list
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
        viewModelScope.launch {
            try {
                val currentRooms = _chatRooms.value
                val currentRoom = currentRooms.firstOrNull { it.roomId == roomId }

                if (currentRoom != null) {
                    // Always update last message and timestamp
                    val updatedRooms = currentRooms.map { room ->
                        if (room.roomId == roomId) {
                            if (!room.isMuted) {
                                // Only increment if not muted
                                repository.incrementUnreadCount(roomId, userId)
                                room.copy(
                                    lastMessage = message,
                                    lastTimestamp = System.currentTimeMillis(),
                                    unreadCount = room.unreadCount + 1
                                )
                            } else {
                                // Just update message info without incrementing count
                                room.copy(
                                    lastMessage = message,
                                    lastTimestamp = System.currentTimeMillis()
                                )
                            }
                        } else {
                            room
                        }
                    }

                    // Update state with new list
                    _chatRooms.value = updatedRooms
                }
            } catch (e: Exception) {
                Log.e("ChatRoomListVM", "Error handling new message", e)
            }
        }
    }    // link join with user

    // Add these to ChatRoomListViewModel.kt
    private val _shareLinkState = MutableStateFlow<ShareLinkState>(ShareLinkState.Idle)
    val shareLinkState: StateFlow<ShareLinkState> = _shareLinkState

    sealed class ShareLinkState {
        object Idle : ShareLinkState()
        object Loading : ShareLinkState()
        data class Success(val link: String) : ShareLinkState()
        data class Error(val message: String) : ShareLinkState()
    }

    fun generateShareLink(roomId: String) {
        viewModelScope.launch {
            _shareLinkState.value = ShareLinkState.Loading
            try {
                val link = repository.generateInviteLink(roomId, userId)
                _shareLinkState.value = ShareLinkState.Success(link)
            } catch (e: Exception) {
                _shareLinkState.value = ShareLinkState.Error(e.message ?: "Failed to generate link")
            }
        }
    }

    fun resetShareLinkState() {
        _shareLinkState.value = ShareLinkState.Idle
    }

    //creation room
    // In ChatRoomListViewModel.kt
    fun createGroupChat(title: String, members: List<String>) {
        if (userId.isEmpty()) {
            _roomCreationState.value = RoomCreationState.Error("User not authenticated")
            return
        }

        viewModelScope.launch {
            _roomCreationState.value = RoomCreationState.Loading
            try {
                val result = repository.createGroupChat(
                    title = title.trim(),
                    creatorId = userId,
                    members = members.filter { it.isNotEmpty() && it != userId }
                )

                result.fold(
                    onSuccess = { roomId ->
                        _roomCreationState.value = RoomCreationState.Success(roomId)
                        fetchChatRooms(true) // Force refresh
                    },
                    onFailure = { e ->
                        _roomCreationState.value = RoomCreationState.Error(
                            e.message ?: "Failed to create group"
                        )
                    }
                )
            } catch (e: Exception) {
                _roomCreationState.value = RoomCreationState.Error(
                    "Failed to create group: ${e.message}"
                )
            }
        }
    }

    fun createChatRoom(otherUserId: String) {
        if (userId.isEmpty()) {
            _roomCreationState.value = RoomCreationState.Error("User not authenticated")
            return
        }

        viewModelScope.launch {
            _roomCreationState.value = RoomCreationState.Loading
            try {
                if (otherUserId.isBlank()) {
                    _roomCreationState.value = RoomCreationState.Error("Invalid user selected")
                    return@launch
                }

                if (otherUserId == userId) {
                    _roomCreationState.value = RoomCreationState.Error("Cannot chat with yourself")
                    return@launch
                }

                val result = repository.createChatRoom(userId, otherUserId)

                result.fold(
                    onSuccess = { roomId ->
                        _roomCreationState.value = RoomCreationState.Success(roomId)
                        fetchChatRooms(true)
                    },
                    onFailure = { e ->
                        _roomCreationState.value = RoomCreationState.Error(
                            e.message ?: "Failed to create chat"
                        )
                    }
                )
            } catch (e: Exception) {
                _roomCreationState.value = RoomCreationState.Error(
                    "Failed to create chat: ${e.message}"
                )
            }
        }
    }



    // In ChatRoomListViewModel.kt

    // Member management functions
    fun getGroupMembers(roomId: String): List<GroupMember> {
        return runBlocking {
            try {
                repository.getGroupMembers(roomId)
            } catch (e: Exception) {
                _error.value = "Failed to get group members: ${e.message}"
                emptyList()
            }
        }
    }

    fun getAvailableUsersToAdd(roomId: String): List<User> {
        return runBlocking {
            try {
                repository.getAvailableUsersToAdd(roomId)
            } catch (e: Exception) {
                _error.value = "Failed to get available users: ${e.message}"
                emptyList()
            }
        }
    }

    fun addMember(roomId: String, userId: String) {
        viewModelScope.launch {
            try {
                repository.addMemberToGroup(roomId, userId)
                loadGroupMembers(roomId)
                // Refresh members list
            } catch (e: Exception) {
                _error.value = "Failed to add member: ${e.message}"
            }
        }
    }

    fun removeMember(roomId: String, userId: String) {
        viewModelScope.launch {
            try {
                repository.removeMemberFromGroup(roomId, userId)
                loadGroupMembers(roomId)
                // Refresh members list
            } catch (e: Exception) {
                _error.value = "Failed to remove member: ${e.message}"
            }
        }
    }

    fun changeMemberRole(roomId: String, userId: String, newRole: String) {
        viewModelScope.launch {
            try {
                repository.changeMemberRole(roomId, userId, newRole)
                loadGroupMembers(roomId)
                // Refresh members list
            } catch (e: Exception) {
                _error.value = "Failed to change role: ${e.message}"
            }
        }
    }

    fun leaveGroup(roomId: String) {
        viewModelScope.launch {
            try {
                repository.leaveGroup(roomId, currentUserId)
            } catch (e: Exception) {
                _error.value = "Failed to leave group: ${e.message}"
            }
        }
    }

    fun transferOwnership(roomId: String, newAdminId: String) {
        viewModelScope.launch {
            try {
                repository.transferOwnership(roomId, currentUserId, newAdminId)
            } catch (e: Exception) {
                _error.value = "Failed to transfer ownership: ${e.message}"
            }
        }
    }


}