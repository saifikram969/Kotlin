package com.example.quickchat.data.model


data class GroupMember(
    val userId: String,
    val userName: String,
    val role: String = "member",
    val joinedAt: Long = System.currentTimeMillis(),
    val isMuted: Boolean,

)