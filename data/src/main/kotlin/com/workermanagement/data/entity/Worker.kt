package com.workermanagement.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * §5.1 worker — worker profile.
 * default_wage and default_role_id are templates only; never used at
 * calculation time (rules D-2, R-1, R-2).
 *
 * Types:
 *   id, default_role_id       — UUID stored as TEXT
 *   joining_date, created_at,
 *   updated_at                — ISO-8601 TEXT ("yyyy-MM-dd" / "yyyy-MM-dd'T'HH:mm:ss")
 *   default_wage              — integer rupees (G-1)
 *   is_active                 — Boolean mapped to INTEGER 0/1 by Room
 */
@Entity(
    tableName = "worker",
    foreignKeys = [
        ForeignKey(
            entity = Role::class,
            parentColumns = ["id"],
            childColumns = ["default_role_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["default_role_id"])
    ]
)
data class Worker(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    @ColumnInfo(name = "default_role_id") val defaultRoleId: String,
    @ColumnInfo(name = "default_wage") val defaultWage: Int,
    @ColumnInfo(name = "joining_date") val joiningDate: String,  // "yyyy-MM-dd"
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: String,      // ISO-8601
    @ColumnInfo(name = "updated_at") val updatedAt: String       // ISO-8601
)
