package com.workermanagement.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * §5.6 weekly_settlement — snapshot created when a week is finalized.
 *
 * Constraints:
 *  - UNIQUE(worker_id, week_start_date) — rule S-2
 *  - FK → worker
 *
 * Snapshot values MUST NOT be modified after creation (S-4).
 *
 * Types:
 *   id, worker_id         — UUID as TEXT
 *   week_start_date,
 *   finalized_at          — ISO-8601 TEXT
 *   base_earnings,
 *   overtime_earnings,
 *   gross_earnings,
 *   advance_deduction,
 *   net_payable           — integer rupees (G-1)
 *   status                — SettlementStatus enum stored as TEXT via Converters
 */
@Entity(
    tableName = "weekly_settlement",
    foreignKeys = [
        ForeignKey(
            entity = Worker::class,
            parentColumns = ["id"],
            childColumns = ["worker_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        // S-2: unique settlement per worker per week
        Index(value = ["worker_id", "week_start_date"], unique = true),
        Index(value = ["worker_id"])
    ]
)
data class WeeklySettlement(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "worker_id") val workerId: String,
    @ColumnInfo(name = "week_start_date") val weekStartDate: String,
    @ColumnInfo(name = "base_earnings") val baseEarnings: Int,
    @ColumnInfo(name = "overtime_earnings") val overtimeEarnings: Int,
    @ColumnInfo(name = "gross_earnings") val grossEarnings: Int,
    @ColumnInfo(name = "advance_deduction") val advanceDeduction: Int,
    @ColumnInfo(name = "net_payable") val netPayable: Int,
    val status: SettlementStatus,
    @ColumnInfo(name = "finalized_at") val finalizedAt: String
)
