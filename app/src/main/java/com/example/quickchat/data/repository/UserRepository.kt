package com.example.quickchat.data.repository


import com.example.quickchat.data.local.UserDao
import com.example.quickchat.data.local.AppUserEntity
import kotlinx.coroutines.flow.Flow

class UserRepository(private val userDao: UserDao) {

    suspend fun getUser(deviceId: String): AppUserEntity? {
        return userDao.getUser(deviceId)
    }

    suspend fun markNameSet(deviceId: String, isSet: Boolean) {
        userDao.markNameSet(deviceId, isSet)
    }
    suspend fun saveUser(user: AppUserEntity) {
        userDao.saveUser(user)
    }

    suspend fun updateUserName(deviceId: String, name: String) {
        userDao.updateUserName(deviceId, name)
        userDao.markNameSet(deviceId, true)

    }
    suspend fun hasProvidedName(deviceId: String): Boolean {
        return userDao.getUser(deviceId)?.isNameSet ?: false
    }

    fun observeUser(deviceId: String): Flow<AppUserEntity?> {

        return userDao.observeUser(deviceId)
    }

    suspend fun getAllUsers(): List<AppUserEntity> {

        return userDao.getAllUsers()
    }

    suspend fun createUserIfNotExists(deviceId: String) {
        if (userDao.getUser(deviceId) == null) {
            userDao.saveUser(AppUserEntity(deviceId = deviceId))
        }
    }

    suspend fun markNameDialogShown(deviceId: String, hasShown: Boolean) {
        userDao.markNameDialogShown(deviceId, hasShown)
    }

    suspend fun hasShownNameDialog(deviceId: String): Boolean {
        return userDao.hasShownNameDialog(deviceId)
    }

        suspend fun createOrUpdateUser(deviceId: String, name: String? = null) {
            val existingUser = userDao.getUser(deviceId)
            if (existingUser == null) {
                userDao.saveUser(
                    AppUserEntity(
                        deviceId = deviceId,
                        name = name ?: "",
                        isNameSet = name != null,
                        hasShownNameDialog = name != null
                    )
                )
            } else if (name != null) {
                userDao.updateUserName(deviceId, name)
                userDao.markNameSet(deviceId, true)
                userDao.markNameDialogShown(deviceId, true)
            }
        }

    suspend fun syncWithFirestore(deviceId: String, firestoreName: String?) {
        val localUser = userDao.getUser(deviceId)
        when {
            localUser == null -> {
                userDao.saveUser(
                    AppUserEntity(
                        deviceId = deviceId,
                        name = firestoreName ?: "",
                        isNameSet = firestoreName != null,
                        hasShownNameDialog = firestoreName != null
                    )
                )
            }
            firestoreName != null && localUser.name != firestoreName -> {
                userDao.updateUserName(deviceId, firestoreName)
                userDao.markNameSet(deviceId, true)
                userDao.markNameDialogShown(deviceId, true)
            }
        }
    }
    }


