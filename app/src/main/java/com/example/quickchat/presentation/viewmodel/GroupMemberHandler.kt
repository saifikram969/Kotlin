package com.example.quickchat.presentation.viewmodel

import com.example.quickchat.data.model.GroupMember
import kotlinx.coroutines.flow.StateFlow

interface GroupMemberHandler {
    val groupMembers: StateFlow<List<GroupMember>>
    val isLoading: StateFlow<Boolean>
    val presenceStatus: StateFlow<Map<String, Boolean>>
    fun loadGroupMembers(roomId: String)
    fun getMemberWithPresence(userId: String): GroupMember?
    fun getCurrentMembersWithPresence(): List<GroupMember>
    fun observePresence(userId: String)
}