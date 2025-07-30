package com.example.quickchat

import android.app.Application
import android.util.Log
import com.example.quickchat.di.appModule
import com.google.firebase.FirebaseApp
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class myApp : Application() {
   override fun onCreate() {
       super.onCreate()
       FirebaseApp.initializeApp(this)?.let{
           Log.d("FirebaseInit", "Firebase initialized successfully.")
       } ?: Log.e("FirebaseInit", "Firebase initialization failed.")

       startKoin {
           androidContext(this@myApp)
           modules(appModule)
       }
   }
}