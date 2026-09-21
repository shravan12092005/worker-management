package com.workermanagement.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workermanagement.data.entity.Settings

@Dao
interface SettingsDao {

    /**
     * Upserts the single settings row.
     * OnConflictStrategy.REPLACE ensures only one row with singleton_id = 1
     * ever exists — a second call silently overwrites the first.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(settings: Settings)

    @Query("SELECT * FROM settings WHERE singleton_id = 1")
    fun get(): Settings?
}
