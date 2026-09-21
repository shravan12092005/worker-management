package com.workermanagement.app.db

import android.content.Context
import androidx.room.Room
import com.workermanagement.data.db.AppDatabase

/**
 * Simple singleton holder for the Room database.
 *
 * Deliberately no DI framework for the skeleton; swap to
 * Hilt's @Provides when the screens multiply.
 */
object DatabaseProvider {

    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "worker-management.db"
            )
                // FK enforcement on every connection — PRAGMA foreign_keys
                // resets to OFF on each new connection, so this callback
                // (registered in onOpen) is mandatory for every build.
                .addCallback(AppDatabase.FOREIGN_KEYS_CALLBACK)
                .build()
                .also { instance = it }
        }
}
