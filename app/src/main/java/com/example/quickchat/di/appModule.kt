package com.example.quickchat.di

import androidx.room.Room
import com.example.quickchat.data.local.AppDatabase
import com.example.quickchat.data.repository.ChatRepository
import com.example.quickchat.data.repository.ChatRoomRepository
import com.example.quickchat.data.repository.FirestoreChatRoomRepository
import com.example.quickchat.presentation.viewmodel.ChatRoomListViewModel
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
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
            )
        .fallbackToDestructiveMigration() // Keep for development
        .build()
    }


    // ChatMessageDao/// roomChat
    single { get<AppDatabase>().chatMessageDao() }
    single { get<AppDatabase>().chatRoomDao() }

    // Repository Bindings
    single<ChatRoomRepository> {
        FirestoreChatRoomRepository(
            firestore = get(),
            chatRoomDao = get()
        )
    }
    // ChatRepository with both Firestore and Room DAO
    single { ChatRepository(get(), get()) }

// provide viewMOdel
    viewModel { ChatViewModel(get()) }




    // Add ChatRoomListViewModel
    viewModel { (userId: String) ->
        ChatRoomListViewModel(
            repository = get(),
            chatRepository = get(),
            userId = userId
        )
    }






}