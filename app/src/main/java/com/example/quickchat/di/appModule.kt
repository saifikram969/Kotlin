package com.example.quickchat.di

import androidx.room.Room
import com.example.quickchat.data.local.AppDatabase
import com.example.quickchat.data.repository.ChatRepository
import com.example.quickchat.data.repository.ChatRoomRepository
import com.example.quickchat.data.repository.FirestoreChatRoomRepository
import com.example.quickchat.data.repository.PresenceRepository
import com.example.quickchat.data.repository.PresenceRepositoryImpl
import com.example.quickchat.data.repository.UserRepository
import com.example.quickchat.presentation.viewmodel.ChatRoomListViewModel
import com.example.quickchat.presentation.viewmodel.ChatViewModel
import com.example.quickchat.utils.ConnectivityObserver
import com.example.quickchat.utils.NetworkConnectivityObserver
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
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17,
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19,
            )
        .fallbackToDestructiveMigration() // Keep for development
        .build()
    }

    // ChatMessageDao/// roomChat
    single { get<AppDatabase>().chatMessageDao() }
    single { get<AppDatabase>().chatRoomDao() }
    single { get<AppDatabase>().userDao() }

    // Repository Bindings
    single<ChatRoomRepository> { FirestoreChatRoomRepository(firestore = get(), chatRoomDao = get()) }

    //user repo
    single { UserRepository(get()) }


    // ChatRepository with both Firestore and Room DAO
    single { ChatRepository(get(), get()) }
    single<ConnectivityObserver> { NetworkConnectivityObserver(get()) }

    single<PresenceRepository> { PresenceRepositoryImpl(firestore = get()) }


    // provide viewMOdel
    viewModel { ChatViewModel(get(), get(),get(),get(),get(),get()) }

    // Add ChatRoomListViewModel
    viewModel { (userId: String) -> ChatRoomListViewModel(repository = get(), chatRepository = get(), userId = userId) }






}