package com.example.quickchat.apiservice

import android.app.Application
import com.cloudinary.android.MediaManager

class CloudinaryApplication: Application() {
    override fun onCreate() {
        super.onCreate()


        val config = HashMap<String, String>()
        config["cloud_name"] = "dpu79qv4i"
        config["api_key"] = "461143212564624"
        config["api_secret"] = "0FBEgH6lY_SEHnH_IPEAl_IX7Ss"

        MediaManager.init(this,config)
    }
}