package com.example.quickchat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase


import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.quickchat.data.model.ChatRoom

@Database(
    entities = [ChatMessageEntity::class, ChatRoom::class], // Include both entities
    version = 13, // Increment version since we're changing schema
    exportSchema = true
)
@TypeConverters(Converters::class) // Add this for List<String> conversion
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatRoomDao(): ChatRoomDao



    companion object {
        const val DATABASE_NAME = "quickchat_db"

        // Migration from 3 to 4
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Your existing migration logic
            }
        }

        // Migration from 4 to 5 (added isArchived)
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE chat_rooms ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // Migration from 5 to 6 (added isMuted)
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE chat_rooms ADD COLUMN isMuted INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // Migration from 6 to 7 (added isProcessingMute)
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE chat_rooms ADD COLUMN isProcessingMute INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // Migration from 7 to 8 (placeholder for future changes)
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add any schema changes for version 8 here
                // Currently just a placeholder
            }
        }
        // In AppDatabase.kt
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE chat_rooms ADD COLUMN pendingMuteState INTEGER"
                )
            }
        }

        // Migration from 9 to 10 (added any new columns if needed)
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add any new schema changes here
            }
        }

        // Migration from 10 to 11 - Fixes the schema mismatch
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Recreate the table with all correct columns
                database.execSQL("""
                    CREATE TABLE new_chat_rooms (
                        roomId TEXT NOT NULL PRIMARY KEY,
                        name TEXT,
                        lastMessage TEXT,
                        lastTimestamp INTEGER NOT NULL,
                        unreadCount INTEGER NOT NULL,
                        userId TEXT NOT NULL,
                        participants TEXT NOT NULL,
                        lastRead INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL DEFAULT 0,
                        isMuted INTEGER NOT NULL DEFAULT 0,
                        isProcessingMute INTEGER NOT NULL DEFAULT 0,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        pendingMuteState INTEGER
                    )
                """)

                // Copy data from old table
                database.execSQL("""
                    INSERT INTO new_chat_rooms 
                    SELECT 
                        roomId, name, lastMessage, lastTimestamp, unreadCount, 
                        userId, participants, lastRead, 
                        CASE WHEN isArchived IS NULL THEN 0 ELSE isArchived END,
                        CASE WHEN isMuted IS NULL THEN 0 ELSE isMuted END,
                        CASE WHEN isProcessingMute IS NULL THEN 0 ELSE isProcessingMute END,
                        CASE WHEN isDeleted IS NULL THEN 0 ELSE isDeleted END,
                        pendingMuteState
                    FROM chat_rooms
                """)

                // Remove old table and rename new one
                database.execSQL("DROP TABLE chat_rooms")
                database.execSQL("ALTER TABLE new_chat_rooms RENAME TO chat_rooms")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create new table with all columns
                database.execSQL("""
            CREATE TABLE new_chat_rooms (
                roomId TEXT NOT NULL PRIMARY KEY,
                name TEXT,
                lastMessage TEXT,
                lastTimestamp INTEGER NOT NULL,
                unreadCount INTEGER NOT NULL,
                userId TEXT NOT NULL,
                participants TEXT NOT NULL,
                lastRead INTEGER NOT NULL,
                isArchived INTEGER NOT NULL DEFAULT 0,
                isMuted INTEGER NOT NULL DEFAULT 0,
                isProcessingMute INTEGER NOT NULL DEFAULT 0,
                isDeleted INTEGER NOT NULL DEFAULT 0,
                pendingMuteState INTEGER
            )
        """)

                // 2. Copy data with only the columns that exist
                database.execSQL("""
            INSERT INTO new_chat_rooms 
            SELECT 
                roomId, name, lastMessage, lastTimestamp, unreadCount, 
                userId, participants, lastRead, 
                COALESCE(isArchived, 0),
                COALESCE(isMuted, 0),
                COALESCE(isProcessingMute, 0),
                0,  -- Default value for isDeleted
                NULL -- Default value for pendingMuteState
            FROM chat_rooms
        """)

                database.execSQL("DROP TABLE chat_rooms")
                database.execSQL("ALTER TABLE new_chat_rooms RENAME TO chat_rooms")
            }
        }
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN messageType TEXT NOT NULL DEFAULT 'TEXT'")
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN imageUrl TEXT")
            }
        }



    }
}