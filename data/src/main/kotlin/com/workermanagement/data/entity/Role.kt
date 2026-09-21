package com.workermanagement.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * §5.3 role — lookup table for worker roles (Mason, Helper, …).
 * Role names may be edited without affecting daily_record history (D-2).
 *
 * Types:
 *   id        — UUID as TEXT
 *   is_active — Boolean mapped to INTEGER 0/1
 */
@Entity(tableName = "role")
data class Role(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true
)
