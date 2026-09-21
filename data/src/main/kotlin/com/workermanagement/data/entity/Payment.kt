package com.workermanagement.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * §5.7 payment — recorded against a weekly_settlement, never against a
 * week directly (P-1).
 *
 * Multiple payments per settlement are supported (P-2).
 * A wrong payment is voided (is_void = true) and a new row created (P-5);
 * both rows remain visible in history.
 *
 * Types:
 *   id, settlement_id — UUID as TEXT
 *   paid_on, created_at — ISO-8601 TEXT
 *   amount            — integer rupees, always positive
 *   method            — PaymentMethod enum stored as TEXT
 *   is_void           — Boolean → INTEGER 0/1
 */
@Entity(
    tableName = "payment",
    foreignKeys = [
        ForeignKey(
            entity = WeeklySettlement::class,
            parentColumns = ["id"],
            childColumns = ["settlement_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["settlement_id"])]
)
data class Payment(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "settlement_id") val settlementId: String,
    @ColumnInfo(name = "paid_on") val paidOn: String,           // "yyyy-MM-dd"
    val amount: Int,
    val method: PaymentMethod,
    val note: String? = null,
    @ColumnInfo(name = "is_void", defaultValue = "0") val isVoid: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "synced", defaultValue = "0") val synced: Boolean = false
)
