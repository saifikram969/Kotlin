package com.example.quickchat.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "app_user")
data class AppUserEntity(
    @PrimaryKey
    val deviceId: String,
    val name: String? = "",
    val isNameSet: Boolean = false,
    val hasShownNameDialog: Boolean = false,
    @ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
    val updatedAt: Long = System.currentTimeMillis()

)