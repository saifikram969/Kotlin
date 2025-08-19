package com.example.quickchat.data.repository

import com.example.quickchat.data.model.ChatRoom
import com.example.quickchat.data.model.GroupMember
import com.example.quickchat.data.model.User
import kotlinx.coroutines.flow.Flow

interface ChatRoomRepository {
    fun getChatRooms(userId: String): Flow<List<ChatRoom>>
    suspend fun updateLastReadTimestamp(roomId: String, userId: String, timestamp: Long)
    suspend fun archiveRoom(roomId: String, archive: Boolean) // Add this
    suspend fun toggleMuteStatus(roomId: String, mute: Boolean)

    suspend fun createChatRoom(user1: String, user2: String): Result<String> // Add this
    suspend fun createGroupChat(title: String, creatorId: String, members: List<String>): Result<String>
    suspend fun getRoomDetails(roomId: String, currentUserId: String): ChatRoom?



    suspend fun doesRoomExist(roomId: String): Boolean // Add this for verification
    suspend fun deleteRoom(roomId: String)
    suspend fun restoreRoom(roomId: String)
    suspend fun incrementUnreadCount(roomId: String, userId: String)
    suspend fun markMessagesAsRead(roomId: String, userId: String)
    fun getUnreadCountFlow(roomId: String, userId: String): Flow<Int>
//sharelink
    suspend fun generateInviteLink(roomId: String, creatorId: String): String
    suspend fun joinChatroomViaLink(roomId: String, token: String, userId: String): Boolean
    suspend fun revokeInviteLink(roomId: String)
    suspend fun syncRoomsWithFirestore(userId: String)

    suspend fun getGroupMembers(roomId: String): List<GroupMember>
    suspend fun getAvailableUsersToAdd(roomId: String): List<User>
    suspend fun addMemberToGroup(roomId: String, userId: String)
    suspend fun removeMemberFromGroup(roomId: String, userId: String)
    suspend fun changeMemberRole(roomId: String, userId: String, newRole: String)
    suspend fun leaveGroup(roomId: String, userId: String)
    suspend fun transferOwnership(roomId: String, currentAdminId: String, newAdminId: String)




}// Create this in a new file or at the top of your repository
sealed class RepositoryResult<out T> {
    data class Success<out T>(val value: T) : RepositoryResult<T>()
    data class Failure(val exception: Throwable) : RepositoryResult<Nothing>()




}