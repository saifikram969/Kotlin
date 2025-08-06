package com.example.quickchat

import android.app.Application
import android.content.ContentValues.TAG
import android.util.Log
import com.cloudinary.android.MediaManager
import com.example.quickchat.data.repository.PresenceRepository
import com.example.quickchat.di.appModule
import com.google.firebase.FirebaseApp
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.compose.getKoin
import org.koin.core.context.startKoin

class myApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)?.let {
                Log.d("FCM_DEBUG", "Firebase initialized")
                Log.d("lalo", "Firebase initialized")
                Log.d("FirebaseInit", "Firebase initialized successfully.")
            } ?: Log.e("FirebaseInit", "Firebase initialization failed.")
        } catch (e: Exception) {
            Log.e("FirebaseInit", "Firebase initialization exception: ${e.message}")
        }



        // Initialize Koin
        try {
            startKoin {
                androidContext(this@myApp)
                modules(appModule)
            }
        } catch (e: Exception) {
            Log.e("KoinInit", "Koin initialization failed: ${e.message}")
        }

        // Initialize Cloudinary
        try {
            val config = HashMap<String, String>().apply {
                put("cloud_name", "dpu79qv4i")
                put("api_key", "461143212564624")
                put("api_secret", "0FBEgH6lY_SEHnH_IPEAl_IX7Ss")
            }
            MediaManager.init(this, config)
            Log.d("CloudinaryInit", "Cloudinary initialized successfully.")
        } catch (e: Exception) {
            Log.e("CloudinaryInit", "Cloudinary initialization failed: ${e.message}")
        }

        // Initialize Presence Tracking
        // Initialize Presence Tracking
        try {
            val presenceRepository = get<PresenceRepository>()
            presenceRepository.registerActivityLifecycleCallbacks(this)
            Log.d("PresenceInit", "Presence tracking initialized successfully.")
        } catch (e: Exception) {
            Log.e("PresenceInit", "Presence tracking initialization failed: ${e.message}")
        }
    }
}

