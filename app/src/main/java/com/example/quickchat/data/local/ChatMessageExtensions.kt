package com.example.quickchat.data.local

import com.example.quickchat.data.model.ChatMessage
import com.example.quickchat.data.model.MessageStatus

fun ChatMessage.toEntity(roomId: String): ChatMessageEntity {
    return ChatMessageEntity(
        id = id,
        text = text,
        senderId = senderId,
        timestamp = timestamp,
        status = status.name,
        isSystemMessage = isSystemMessage,
        clientGeneratedId = clientGeneratedId,
        roomId = roomId
    )
}

fun ChatMessageEntity.toChatMessage(): ChatMessage {
    return ChatMessage(
        id = id,
        text = text,
        senderId = senderId,
        timestamp = timestamp,
        isSystemMessage = isSystemMessage,
        clientGeneratedId = clientGeneratedId,
        status = MessageStatus.valueOf(status)
    )
}