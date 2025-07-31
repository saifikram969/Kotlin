package com.example.quickchat.di

import androidx.room.Room
import com.example.quickchat.data.local.AppDatabase
import com.example.quickchat.data.repository.ChatRepository
import com.example.quickchat.presentation.viewmodel.ChatViewModel
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    //provider firestore databasw singleton
    single { FirebaseFirestore.getInstance() }


    // Room Database
    single {
        Room.databaseBuilder(
            androidApplication(),
            AppDatabase::class.java,
            "quickchat-db"
        ).build()
    }
    // ChatMessageDao
    single { get<AppDatabase>().chatMessageDao() }

    // ChatRepository with both Firestore and Room DAO
    single { ChatRepository(get(), get()) }

// provide viewMOdel
    viewModel { ChatViewModel(get()) }

}