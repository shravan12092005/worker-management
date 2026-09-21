package com.workermanagement.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workermanagement.data.entity.AppUser

@Dao
interface AppUserDao {

    /**
     * Inserts a user. Throws SQLiteConstraintException on duplicate username.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(user: AppUser)

    @Query("SELECT * FROM app_user WHERE username = :username")
    fun getByUsername(username: String): AppUser?

    @Query("SELECT * FROM app_user WHERE id = :id")
    fun getById(id: String): AppUser?

    @Query("SELECT * FROM app_user ORDER BY name ASC")
    fun getAll(): List<AppUser>
}
