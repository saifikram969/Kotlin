package com.example.quickchat.di

import com.example.quickchat.data.repository.ChatRepository
import com.example.quickchat.presentation.viewmodel.ChatViewModel
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    //provider firestore databasw singleton
    single { FirebaseFirestore.getInstance() }

    //provider repostory with database dependency

    single { ChatRepository(get()) }

    // provide viewMOdel
    viewModel { ChatViewModel(get()) }
}