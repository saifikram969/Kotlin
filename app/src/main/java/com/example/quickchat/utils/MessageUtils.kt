package com.example.quickchat.utils

import com.example.quickchat.data.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
object MessageUtils {
    fun groupMessagesByDate(messages: List<ChatMessage>): List<Any> {
        return messages.sortedBy { it.timestamp }
            .groupBy {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(it.timestamp))
            }
            .flatMap { (date, messagesForDate) ->
                listOf(date) + messagesForDate
            }
    }

    fun formatDateHeader(dateString: String): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))

        return when (dateString) {
            today -> "Today"
            else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                .format(
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .parse(dateString)!!
                )
        }
    }
}