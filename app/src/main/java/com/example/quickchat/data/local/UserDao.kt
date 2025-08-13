package com.example.quickchat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


// Add this to your existing data.local package
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: AppUserEntity)

    @Query("SELECT * FROM app_user WHERE deviceId = :deviceId")
    suspend fun getUser(deviceId: String): AppUserEntity?

    @Query("UPDATE app_user SET name = :name, isNameSet = 1 WHERE deviceId = :deviceId")
    suspend fun updateUserName(deviceId: String, name: String)

    // Optional Flow support
    @Query("SELECT * FROM app_user WHERE deviceId = :deviceId")
    fun observeUser(deviceId: String): Flow<AppUserEntity?>

    // Optional: For getting all users
    @Query("SELECT * FROM app_user")
    suspend fun getAllUsers(): List<AppUserEntity>


    @Query("UPDATE app_user SET hasShownNameDialog = 1 WHERE deviceId = :deviceId")
    suspend fun markNameDialogShown(deviceId: String)

    @Query("SELECT hasShownNameDialog FROM app_user WHERE deviceId = :deviceId")
    suspend fun hasShownNameDialog(deviceId: String): Boolean

    @Query("UPDATE app_user SET isNameSet = :isSet WHERE deviceId = :deviceId")
    suspend fun markNameSet(deviceId: String, isSet: Boolean)

    @Query("SELECT * FROM app_user WHERE deviceId = :deviceId")
    suspend fun getUserWithName(deviceId: String): AppUserEntity?

    @Query("UPDATE app_user SET name = :name, isNameSet = 1, updatedAt = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateUserName(deviceId: String, name: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE app_user SET hasShownNameDialog = :hasShown WHERE deviceId = :deviceId")
    suspend fun markNameDialogShown(deviceId: String, hasShown: Boolean)
}