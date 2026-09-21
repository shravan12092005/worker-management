package com.workermanagement.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * §5.5 advance_txn — advance ledger.
 *
 * Note: spec.md §5.5 listed payment_id as the FK target, but rule A-7
 * says "linked to the settlement that produced it". The column is
 * therefore settlement_id → weekly_settlement (nullable). This also
 * eliminates the circular FK that would have existed with payment.
 *
 * Balance is always DERIVED (never stored) per rule A-1:
 *   SUM(ADVANCE_GIVEN) − SUM(DEDUCTION) − SUM(WRITE_OFF)
 *
 * Types:
 *   id, worker_id,
 *   settlement_id — UUID as TEXT
 *   txn_date,
 *   created_at    — ISO-8601 TEXT
 *   amount        — integer rupees, always positive; type carries sign (A-1)
 *   type          — AdvanceTxnType enum stored as TEXT via Converters
 */
@Entity(
    tableName = "advance_txn",
    foreignKeys = [
        ForeignKey(
            entity = Worker::class,
            parentColumns = ["id"],
            childColumns = ["worker_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = WeeklySettlement::class,
            parentColumns = ["id"],
            childColumns = ["settlement_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["worker_id"]),
        Index(value = ["settlement_id"])
    ]
)
data class AdvanceTxn(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "worker_id") val workerId: String,
    @ColumnInfo(name = "txn_date") val txnDate: String,        // "yyyy-MM-dd"
    val type: AdvanceTxnType,
    val amount: Int,                                           // always positive
    @ColumnInfo(name = "settlement_id") val settlementId: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String
)
