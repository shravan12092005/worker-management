package com.workermanagement.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.workermanagement.data.entity.Role

@Dao
interface RoleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(role: Role)

    @Update
    fun update(role: Role)

    @Query("SELECT * FROM role WHERE id = :id")
    fun getById(id: String): Role?

    @Query("SELECT * FROM role ORDER BY name ASC")
    fun getAll(): List<Role>

    @Query("SELECT * FROM role WHERE is_active = 1 ORDER BY name ASC")
    fun getActive(): List<Role>
}
