package com.example.quickchat

import android.app.Application
import com.example.quickchat.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class myApp : Application() {
   override fun onCreate() {
       super.onCreate()
       startKoin {
           androidContext(this@myApp)
           modules(appModule)
       }
   }
}