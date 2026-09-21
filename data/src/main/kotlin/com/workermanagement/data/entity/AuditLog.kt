package com.workermanagement.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * §5.9 audit_log — immutable append-only history of financial mutations.
 *
 * Written for every change to: daily_record.wage, attendance,
 * overtime_amount, site_id, role_id; worker.default_wage; and every
 * row in advance_txn, payment, adjustment (rule G-3).
 *
 * old_value and new_value are TEXT representations of the changed field.
 * Integer money values are serialised as their decimal string form.
 *
 * Types:
 *   id, entity_id,
 *   changed_by    — UUID as TEXT
 *   entity_type,
 *   field,
 *   old_value,
 *   new_value     — TEXT
 *   changed_at    — ISO-8601 TEXT
 */
@Entity(
    tableName = "audit_log",
    foreignKeys = [
        ForeignKey(
            entity = AppUser::class,
            parentColumns = ["id"],
            childColumns = ["changed_by"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["entity_type", "entity_id"]),
        Index(value = ["changed_by"])
    ]
)
data class AuditLog(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val field: String,
    @ColumnInfo(name = "old_value") val oldValue: String?,
    @ColumnInfo(name = "new_value") val newValue: String?,
    @ColumnInfo(name = "changed_by") val changedBy: String,
    @ColumnInfo(name = "changed_at") val changedAt: String
)
