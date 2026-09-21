package com.workermanagement.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * §5.10 app_user — authenticated user of the application.
 *
 * password_hash must use Argon2id or bcrypt; plaintext passwords MUST
 * NEVER be stored (spec §12).
 *
 * Types:
 *   id        — UUID as TEXT
 *   role      — UserRole enum stored as TEXT via Converters
 *   is_active — Boolean → INTEGER 0/1
 */
@Entity(
    tableName = "app_user",
    indices = [Index(value = ["username"], unique = true)]
)
data class AppUser(
    @PrimaryKey val id: String,
    val name: String,
    val username: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    val role: UserRole,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true
)
