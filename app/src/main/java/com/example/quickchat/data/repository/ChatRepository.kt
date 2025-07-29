package com.example.quickchat.data.repository

import com.example.quickchat.data.model.ChatMessage
import java.util.UUID


class ChatRepository {
    private val dummyMessages = mutableListOf(
        ChatMessage(
            id = "1",
            text = "Andres joined the chat!",
            senderId = "user1",
            timestamp = System.currentTimeMillis() - 10000,
            isSystemMessage = true
        ),
        ChatMessage(
            id = "2",
            text = "Hi! How are you?",
            senderId = "user2",
            timestamp = System.currentTimeMillis() - 5000
        ),
        ChatMessage(
            id = "3",
            text = "I'm good, thanks!",
            senderId = "user1",
            timestamp = System.currentTimeMillis()
        ), ChatMessage(
                id = "3",
        text = "I'm good, thanks!",
        senderId = "user1",
        timestamp = System.currentTimeMillis()
    ), ChatMessage(
    id = "3",
    text = "I'm good, thanks!",
    senderId = "user1",
    timestamp = System.currentTimeMillis()
    )
    )

    fun getMessages(): List<ChatMessage> = dummyMessages

    fun addMessage(message: ChatMessage) {
        println("Adding message: ${message.text}")

        dummyMessages.add(message)
    }
    fun addSystemMessage(text: String) {
        dummyMessages.add(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                text = text,
                senderId = "system",
                isSystemMessage = true
            )
        )
    }
}