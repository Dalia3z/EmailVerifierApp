package com.example.emailverifier.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database holding the email verification progress.
 *
 * A single (thread-safe) instance is shared by the whole app.
 * `exportSchema = false` keeps the build free of schema-export warnings.
 */
@Database(entities = [EmailEntity::class], version = 1, exportSchema = false)
abstract class EmailDatabase : RoomDatabase() {

    abstract fun emailDao(): EmailDao

    companion object {
        @Volatile
        private var INSTANCE: EmailDatabase? = null

        fun getInstance(context: Context): EmailDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EmailDatabase::class.java,
                    "email_verifier.db",
                ).build().also { INSTANCE = it }
            }
    }
}
