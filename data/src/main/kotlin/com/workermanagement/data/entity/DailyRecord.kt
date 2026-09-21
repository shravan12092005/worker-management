package com.workermanagement.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * §5.4 daily_record — the central table; single source of truth (spec §3).
 *
 * Constraints:
 *  - UNIQUE(worker_id, work_date)   — rule D-1
 *  - Index on (week_start_date, worker_id) — drives every weekly query
 *  - Index on (site_id, work_date)  — drives attendance screen (§6.1)
 *  - FK → worker, site, role
 *
 * Important: week_start_date is NOT auto-computed by Room; the caller
 * (repository layer) must supply it correctly from work_date and the
 * configured week_start_day setting (rule W-1).
 *
 * Types:
 *   id, worker_id, site_id,
 *   role_id               — UUID as TEXT
 *   work_date,
 *   week_start_date,
 *   created_at, updated_at — ISO-8601 TEXT
 *   wage, overtime_amount — integer rupees (G-1)
 *   attendance            — Attendance enum stored as TEXT via Converters
 *   is_locked, is_active  — Boolean → INTEGER 0/1
 */
@Entity(
    tableName = "daily_record",
    foreignKeys = [
        ForeignKey(
            entity = Worker::class,
            parentColumns = ["id"],
            childColumns = ["worker_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Site::class,
            parentColumns = ["id"],
            childColumns = ["site_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Role::class,
            parentColumns = ["id"],
            childColumns = ["role_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        // D-1: unique constraint on (worker_id, work_date)
        Index(value = ["worker_id", "work_date"], unique = true),
        // Weekly query index
        Index(value = ["week_start_date", "worker_id"]),
        // Site attendance screen index
        Index(value = ["site_id", "work_date"]),
        // role_id FK index — prevents full scans on role table updates
        Index(value = ["role_id"])
    ]
)
data class DailyRecord(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "worker_id") val workerId: String,
    @ColumnInfo(name = "work_date") val workDate: String,           // "yyyy-MM-dd"
    @ColumnInfo(name = "week_start_date") val weekStartDate: String,// "yyyy-MM-dd" — caller computes
    @ColumnInfo(name = "site_id") val siteId: String,
    @ColumnInfo(name = "role_id") val roleId: String,
    val wage: Int,
    val attendance: Attendance,
    @ColumnInfo(name = "overtime_amount", defaultValue = "0") val overtimeAmount: Int = 0,
    val note: String? = null,
    @ColumnInfo(name = "is_locked", defaultValue = "0") val isLocked: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)
