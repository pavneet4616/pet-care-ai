package com.petcare.companionlite.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.petcare.companionlite.data.model.ActivityEntity
import com.petcare.companionlite.data.model.ChatDraftEntity
import com.petcare.companionlite.data.model.ChatMessageEntity
import com.petcare.companionlite.data.model.ChatSessionEntity
import com.petcare.companionlite.data.model.ExpenseEntity
import com.petcare.companionlite.data.model.HealthLogEntity
import com.petcare.companionlite.data.model.NoteEntity
import com.petcare.companionlite.data.model.PetEntity
import com.petcare.companionlite.data.model.ReminderEntity
import com.petcare.companionlite.data.model.WaterLogEntity

@Database(
    entities = [
        PetEntity::class, 
        ActivityEntity::class, 
        ReminderEntity::class, 
        HealthLogEntity::class,
        WaterLogEntity::class,
        NoteEntity::class,
        ExpenseEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        ChatDraftEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class PetCareDatabase : RoomDatabase() {
    abstract fun petDao(): PetDao
    abstract fun activityDao(): ActivityDao
    abstract fun reminderDao(): ReminderDao
    abstract fun healthLogDao(): HealthLogDao
    abstract fun waterLogDao(): WaterLogDao
    abstract fun noteDao(): NoteDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatDraftDao(): ChatDraftDao

    companion object {
        @Volatile
        private var INSTANCE: PetCareDatabase? = null

        /**
         * Migration from v1 to v2: added health_logs table and unique index on activities.
         * For any future schema changes, add a new Migration(N, N+1) here so existing
         * user data stays intact across upgrades.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `health_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `petId` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `value` REAL,
                        `note` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        FOREIGN KEY(`petId`) REFERENCES `pets`(`id`) ON DELETE CASCADE
                    )""",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_logs_petId` ON `health_logs` (`petId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Water Logs
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `water_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `petId` INTEGER NOT NULL,
                        `amountMl` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        FOREIGN KEY(`petId`) REFERENCES `pets`(`id`) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_logs_petId` ON `water_logs` (`petId`)")

                // Notes
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `petId` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `isTodo` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        FOREIGN KEY(`petId`) REFERENCES `pets`(`id`) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_petId` ON `notes` (`petId`)")

                // Expenses
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `expenses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `petId` INTEGER NOT NULL,
                        `amount` REAL NOT NULL,
                        `category` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        FOREIGN KEY(`petId`) REFERENCES `pets`(`id`) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_petId` ON `expenses` (`petId`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // AI chat sessions (local-first history; server keeps no permanent chats)
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `chat_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `petId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `language` TEXT,
                        `archived` INTEGER NOT NULL,
                        FOREIGN KEY(`petId`) REFERENCES `pets`(`id`) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_sessions_petId_updatedAt` ON `chat_sessions` (`petId`, `updatedAt`)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `requestId` TEXT,
                        `isSaved` INTEGER NOT NULL,
                        `reportState` TEXT,
                        FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions` (`id`) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId_createdAt` ON `chat_messages` (`sessionId`, `createdAt`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Structured answers + send status + feedback + drafts.
                // Existing chat history is preserved.
                db.execSQL("ALTER TABLE `chat_sessions` ADD COLUMN `lastMessagePreview` TEXT")
                db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `structuredPayload` TEXT")
                db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'SENT'")
                db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `feedback` TEXT")
                db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `errorCode` TEXT")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `chat_drafts` (
                        `sessionId` INTEGER NOT NULL PRIMARY KEY,
                        `text` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions` (`id`) ON DELETE CASCADE
                    )"""
                )
            }
        }

        fun getDatabase(context: Context): PetCareDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context,
                    PetCareDatabase::class.java,
                    "petcare_companion.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
