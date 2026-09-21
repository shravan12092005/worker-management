package com.workermanagement.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * §5.2 site — a work location.
 * Closed sites are deactivated, never deleted (G-2, M-3).
 *
 * Types:
 *   id                     — UUID as TEXT
 *   is_active              — Boolean mapped to INTEGER 0/1
 *   created_at, updated_at — ISO-8601 TEXT
 */
@Entity(tableName = "site")
data class Site(
    @PrimaryKey val id: String,
    val name: String,
    val location: String? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)
