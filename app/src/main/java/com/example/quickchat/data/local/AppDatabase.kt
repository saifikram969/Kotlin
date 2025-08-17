package com.example.quickchat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase


import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.quickchat.data.model.ChatRoom

@Database(
    entities = [ChatMessageEntity::class, ChatRoom::class, AppUserEntity::class],
    version = 21, //
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatRoomDao(): ChatRoomDao
    abstract fun userDao(): UserDao



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
                database.execSQL(
                    """
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
                """
                )

                // Copy data from old table
                database.execSQL(
                    """
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
                """
                )

                // Remove old table and rename new one
                database.execSQL("DROP TABLE chat_rooms")
                database.execSQL("ALTER TABLE new_chat_rooms RENAME TO chat_rooms")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create new table with all columns
                database.execSQL(
                    """
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
        """
                )

                // 2. Copy data with only the columns that exist
                database.execSQL(
                    """
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
        """
                )

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

        // Migration from 13 to 14 - Add fcmTokens column
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE chat_rooms ADD COLUMN fcmTokens TEXT NOT NULL DEFAULT '{}'"
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create the new table with the updated schema
                database.execSQL(
                    """
            CREATE TABLE new_chat_rooms (
                roomId TEXT NOT NULL PRIMARY KEY,
                name TEXT,
                lastMessage TEXT,
                lastTimestamp INTEGER NOT NULL,
                unreadCount INTEGER NOT NULL,
                userId TEXT NOT NULL,
                participants TEXT NOT NULL,
                lastRead INTEGER NOT NULL,
                lastUpdated INTEGER NOT NULL DEFAULT 0, -- new column with default
                isLocal INTEGER NOT NULL DEFAULT 0,     -- new column with default
                isArchived INTEGER NOT NULL DEFAULT 0,
                isDeleted INTEGER NOT NULL DEFAULT 0,
                isMuted INTEGER NOT NULL DEFAULT 0,
                isProcessingMute INTEGER NOT NULL DEFAULT 0,
                pendingMuteState INTEGER,
                fcmTokens TEXT NOT NULL DEFAULT '{}'
            )
        """
                )

                // 2. Copy over old data, setting defaults for new columns
                database.execSQL(
                    """
            INSERT INTO new_chat_rooms (
                roomId, name, lastMessage, lastTimestamp, unreadCount,
                userId, participants, lastRead,
                lastUpdated, isLocal,
                isArchived, isDeleted, isMuted, isProcessingMute,
                pendingMuteState, fcmTokens
            )
            SELECT
                roomId, name, lastMessage, lastTimestamp, unreadCount,
                userId, participants, lastRead,
                0 AS lastUpdated,  -- default value
                0 AS isLocal,      -- default value
                COALESCE(isArchived, 0),
                COALESCE(isDeleted, 0),
                COALESCE(isMuted, 0),
                COALESCE(isProcessingMute, 0),
                pendingMuteState,
                COALESCE(fcmTokens, '{}')
            FROM chat_rooms
        """
                )

                // 3. Drop the old table
                database.execSQL("DROP TABLE chat_rooms")

                // 4. Rename the new table to the original name
                database.execSQL("ALTER TABLE new_chat_rooms RENAME TO chat_rooms")
            }
        }


        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // If you added new columns, include them here
                // This should match exactly what your current schema should be
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS new_chat_rooms (
                        roomId TEXT NOT NULL PRIMARY KEY,
                        name TEXT,
                        lastMessage TEXT,
                        lastTimestamp INTEGER NOT NULL,
                        unreadCount INTEGER NOT NULL,
                        userId TEXT NOT NULL,
                        participants TEXT NOT NULL,
                        lastRead INTEGER NOT NULL,
                        lastUpdated INTEGER NOT NULL DEFAULT 0,
                        isLocal INTEGER NOT NULL DEFAULT 0,
                        isArchived INTEGER NOT NULL DEFAULT 0,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        isMuted INTEGER NOT NULL DEFAULT 0,
                        isProcessingMute INTEGER NOT NULL DEFAULT 0,
                        pendingMuteState INTEGER,
                        fcmTokens TEXT NOT NULL DEFAULT '{}'
                    )
                """)

                // Copy data from old table
                database.execSQL("""
                    INSERT INTO new_chat_rooms (
                        roomId, name, lastMessage, lastTimestamp, unreadCount,
                        userId, participants, lastRead,
                        lastUpdated, isLocal,
                        isArchived, isDeleted, isMuted, isProcessingMute,
                        pendingMuteState, fcmTokens
                    )
                    SELECT
                        roomId, name, lastMessage, lastTimestamp, unreadCount,
                        userId, participants, lastRead,
                        COALESCE(lastUpdated, 0),
                        COALESCE(isLocal, 0),
                        COALESCE(isArchived, 0),
                        COALESCE(isDeleted, 0),
                        COALESCE(isMuted, 0),
                        COALESCE(isProcessingMute, 0),
                        pendingMuteState,
                        COALESCE(fcmTokens, '{}')
                    FROM chat_rooms
                """)

                // Remove old table and rename new one
                database.execSQL("DROP TABLE chat_rooms")
                database.execSQL("ALTER TABLE new_chat_rooms RENAME TO chat_rooms")
            }
        }
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // First check if thumbnailUrl exists in the old table
                val cursor = database.query("PRAGMA table_info(chat_messages)")
                var hasThumbnailUrl = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == "thumbnailUrl") {
                        hasThumbnailUrl = true
                        break
                    }
                }
                cursor.close()

                // Recreate the table with the new schema
                database.execSQL("""
            CREATE TABLE new_chat_messages (
                id TEXT NOT NULL PRIMARY KEY,
                text TEXT NOT NULL,
                senderId TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                status TEXT NOT NULL,
                isSystemMessage INTEGER NOT NULL,
                clientGeneratedId TEXT NOT NULL,
                roomId TEXT NOT NULL,
                messageType TEXT NOT NULL,
                imageUrl TEXT,
                thumbnailUrl TEXT
            )
        """)

                // Copy data with conditional thumbnailUrl handling
                if (hasThumbnailUrl) {
                    database.execSQL("""
                INSERT INTO new_chat_messages (
                    id, text, senderId, timestamp, status,
                    isSystemMessage, clientGeneratedId, roomId,
                    messageType, imageUrl, thumbnailUrl
                )
                SELECT
                    id, text, senderId, timestamp, status,
                    isSystemMessage, clientGeneratedId, roomId,
                    messageType, imageUrl, thumbnailUrl
                FROM chat_messages
            """)
                } else {
                    database.execSQL("""
                INSERT INTO new_chat_messages (
                    id, text, senderId, timestamp, status,
                    isSystemMessage, clientGeneratedId, roomId,
                    messageType, imageUrl, thumbnailUrl
                )
                SELECT
                    id, text, senderId, timestamp, status,
                    isSystemMessage, clientGeneratedId, roomId,
                    messageType, imageUrl, NULL as thumbnailUrl
                FROM chat_messages
            """)
                }

                // Remove old table and rename new one
                database.execSQL("DROP TABLE chat_messages")
                database.execSQL("ALTER TABLE new_chat_messages RENAME TO chat_messages")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create new table with exact schema matching your entity
                database.execSQL("""
                    CREATE TABLE new_chat_rooms (
                        roomId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        lastMessage TEXT,
                        lastTimestamp INTEGER NOT NULL,
                        unreadCount INTEGER NOT NULL,
                        userId TEXT NOT NULL,
                        participants TEXT NOT NULL,
                        lastRead INTEGER NOT NULL,
                        lastUpdated INTEGER NOT NULL,
                        isLocal INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL,
                        isMuted INTEGER NOT NULL,
                        isProcessingMute INTEGER NOT NULL,
                        pendingMuteState INTEGER,
                        fcmTokens TEXT NOT NULL
                    )
                """)

                // 2. Copy data with proper type conversions
                database.execSQL("""
                    INSERT INTO new_chat_rooms (
                        roomId, name, lastMessage, lastTimestamp, unreadCount,
                        userId, participants, lastRead, lastUpdated,
                        isLocal, isArchived, isDeleted, isMuted, isProcessingMute,
                        pendingMuteState, fcmTokens
                    )
                    SELECT
                        roomId, 
                        COALESCE(name, '') as name,
                        lastMessage,
                        COALESCE(lastTimestamp, 0) as lastTimestamp,
                        COALESCE(unreadCount, 0) as unreadCount,
                        userId,
                        COALESCE(participants, '[]') as participants,
                        COALESCE(lastRead, 0) as lastRead,
                        COALESCE(lastUpdated, ${System.currentTimeMillis()}) as lastUpdated,
                        COALESCE(isLocal, 0) as isLocal,
                        COALESCE(isArchived, 0) as isArchived,
                        COALESCE(isDeleted, 0) as isDeleted,
                        COALESCE(isMuted, 0) as isMuted,
                        COALESCE(isProcessingMute, 0) as isProcessingMute,
                        pendingMuteState,
                        COALESCE(fcmTokens, '{}') as fcmTokens
                    FROM chat_rooms
                """)

                // 3. Replace old table
                database.execSQL("DROP TABLE chat_rooms")
                database.execSQL("ALTER TABLE new_chat_rooms RENAME TO chat_rooms")
            }
        }
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_user (
                        deviceId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        isNameSet INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_user ADD COLUMN hasShownNameDialog INTEGER NOT NULL DEFAULT 0")
            }
        }





        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Recreate tables with current schema
                database.execSQL("""
            CREATE TABLE new_chat_rooms (
                roomId TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                lastMessage TEXT,
                lastTimestamp INTEGER NOT NULL,
                unreadCount INTEGER NOT NULL,
                userId TEXT NOT NULL,
                participants TEXT NOT NULL,
                lastRead INTEGER NOT NULL,
                lastUpdated INTEGER NOT NULL,
                isLocal INTEGER NOT NULL,
                isArchived INTEGER NOT NULL,
                isDeleted INTEGER NOT NULL,
                isMuted INTEGER NOT NULL,
                isProcessingMute INTEGER NOT NULL,
                pendingMuteState INTEGER,
                fcmTokens TEXT NOT NULL,
                type TEXT NOT NULL DEFAULT 'dm',
                createdBy TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL DEFAULT 0,
                admins TEXT NOT NULL DEFAULT '[]'
            )
        """)

                // Copy data from old table
                database.execSQL("""
            INSERT INTO new_chat_rooms (
                roomId, name, lastMessage, lastTimestamp, unreadCount,
                userId, participants, lastRead, lastUpdated,
                isLocal, isArchived, isDeleted, isMuted, isProcessingMute,
                pendingMuteState, fcmTokens
            )
            SELECT 
                roomId, name, lastMessage, lastTimestamp, unreadCount,
                userId, participants, lastRead, lastUpdated,
                isLocal, isArchived, isDeleted, isMuted, isProcessingMute,
                pendingMuteState, fcmTokens
            FROM chat_rooms
        """)

                // Drop old table and rename new one
                database.execSQL("DROP TABLE chat_rooms")
                database.execSQL("ALTER TABLE new_chat_rooms RENAME TO chat_rooms")
            }
        }







    }
}



