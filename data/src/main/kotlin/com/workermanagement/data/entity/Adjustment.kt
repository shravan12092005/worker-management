package com.workermanagement.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * §5.8 adjustment — created when a locked daily_record is edited (J-1,J-2).
 *
 * The original weekly_settlement and payment rows are NEVER modified (J-3).
 * Multiple edits to the same week produce multiple adjustment rows (J-4).
 *
 * amount is SIGNED:
 *   positive = worker is owed more
 *   negative = worker was overpaid
 * (contrast with Payment.amount which is always positive)
 *
 * settled_in_settlement_id: nullable FK to the later settlement in which
 * this adjustment was included (J-5).
 *
 * Types:
 *   id, settlement_id,
 *   settled_in_settlement_id — UUID as TEXT
 *   created_at               — ISO-8601 TEXT
 *   amount                   — signed integer rupees
 */
@Entity(
    tableName = "adjustment",
    foreignKeys = [
        ForeignKey(
            entity = WeeklySettlement::class,
            parentColumns = ["id"],
            childColumns = ["settlement_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = WeeklySettlement::class,
            parentColumns = ["id"],
            childColumns = ["settled_in_settlement_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["settlement_id"]),
        Index(value = ["settled_in_settlement_id"])
    ]
)
data class Adjustment(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "settlement_id") val settlementId: String,
    val amount: Int,  // signed — positive = owed to worker, negative = worker overpaid
    val reason: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "settled_in_settlement_id") val settledInSettlementId: String? = null
)
