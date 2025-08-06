package com.example.quickchat.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date


data class User(
    val userId: String = "",
    val fcmToken: String = "",
    val name: String = "",
    @ServerTimestamp val lastUpdated: Date? = null
)
