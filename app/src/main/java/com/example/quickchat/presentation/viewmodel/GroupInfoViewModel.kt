package com.example.quickchat.presentation.viewmodel


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickchat.data.model.GroupMember
import com.example.quickchat.data.repository.ChatRoomRepository
import com.example.quickchat.data.repository.PresenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GroupInfoViewModel : ViewModel(), KoinComponent, GroupMemberHandler {
    private val repository: ChatRoomRepository by inject()
    private val presenceRepository: PresenceRepository by inject()

    private val _groupMembers = MutableStateFlow<List<GroupMember>>(emptyList())
    override val groupMembers: StateFlow<List<GroupMember>> = _groupMembers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _presenceStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    override val presenceStatus: StateFlow<Map<String, Boolean>> = _presenceStatus.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    // Implement interface method
    override fun loadGroupMembers(roomId: String) {
        Log.d("VM_DEBUG", "Loading members for room: $roomId")
        if (roomId.isBlank()) {
            Log.e("VM_ERROR", "Empty roomId provided!")
            _error.value = "Invalid room ID"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val members = repository.getGroupMembers(roomId)
                Log.d("VM_DEBUG", "Retrieved ${members.size} members")

                _groupMembers.value = members

                // Observe presence for each member
                members.forEach { member ->
                    Log.d("VM_DEBUG", "Starting presence observation for ${member.userId}")
                    observePresence(member.userId)
                }
            } catch (e: Exception) {
                Log.e("VM_ERROR", "Error loading members", e)
                _error.value = "Failed to load members: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Add missing interface methods
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

    // Keep this private (not part of the interface)
    override fun observePresence(userId: String) { // Remove 'private' and add 'override'
        viewModelScope.launch {
            presenceRepository.observeUserPresence(userId).collect { isOnline ->
                _presenceStatus.update { current ->
                    current + (userId to isOnline)
                }
            }
        }
    }
}