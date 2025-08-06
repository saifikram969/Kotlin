package com.example.quickchat.utils

import android.content.Context
import android.provider.Settings

object DeviceIdHelper {
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "device_${System.currentTimeMillis()}"
    }
}