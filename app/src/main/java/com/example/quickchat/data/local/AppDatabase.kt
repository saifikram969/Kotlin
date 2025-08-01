package com.example.quickchat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase


import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.quickchat.data.model.ChatRoom

@Database(
    entities = [ChatMessageEntity::class, ChatRoom::class], // Include both entities
    version = 4, // Increment version since we're changing schema
    exportSchema = false
)
@TypeConverters(Converters::class) // Add this for List<String> conversion
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatRoomDao(): ChatRoomDao

    companion object {
        const val DATABASE_NAME = "quickchat_db"

        // Add migration if needed
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No schema changes needed, just version bump
            }
        }
    }
}