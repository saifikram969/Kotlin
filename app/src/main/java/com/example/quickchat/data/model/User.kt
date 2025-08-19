package com.example.quickchat.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date


data class User(
    val deviceId: String = "",
    val userName: String = "",
    val fcmToken: String? = null,
    val lastUpdated: Date? = null
) {
    constructor() : this("", "", null, null) // For Firestore
}
