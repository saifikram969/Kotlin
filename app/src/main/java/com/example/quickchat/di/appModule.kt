package com.example.quickchat.di

import com.example.quickchat.data.repository.ChatRepository
import com.example.quickchat.presentation.viewmodel.ChatViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { ChatRepository() }
    viewModel { ChatViewModel(get()) }
}