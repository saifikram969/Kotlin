package com.example.quickchat.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date


data class User(
    val deviceId: String = "",
    val userName: String = "", // Must match Firestore field name exactly
    val fcmToken: String? = null,
    val lastUpdated: Date? = null
) {
    constructor() : this("", "", null, null) // For Firestore
}
