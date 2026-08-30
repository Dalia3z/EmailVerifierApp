package com.example.emailverifier.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database holding both email and phone verification progress.
 *
 * A single (thread-safe) instance is shared by the whole app.
 * `exportSchema = false` keeps the build free of schema-export warnings.
 */
@Database(
    entities = [EmailEntity::class, PhoneNumberEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class EmailDatabase : RoomDatabase() {

    abstract fun emailDao(): EmailDao

    abstract fun phoneNumberDao(): PhoneNumberDao

    companion object {

        /** v1 -> v2: adds the phone_numbers table (existing email rows are preserved). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `phone_numbers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `rawNumber` TEXT NOT NULL,
                        `defaultRegion` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `numberType` TEXT,
                        `formattedE164` TEXT,
                        `reason` TEXT,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        @Volatile
        private var INSTANCE: EmailDatabase? = null

        fun getInstance(context: Context): EmailDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EmailDatabase::class.java,
                    "email_verifier.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

