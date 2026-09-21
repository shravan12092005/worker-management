package com.workermanagement.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * §5.11 settings — single-row table holding all business decisions from §11.
 *
 * Singleton enforced by @PrimaryKey with a fixed value of 1.
 * The DAO uses @Insert(onConflict = REPLACE) so a second upsert silently
 * overwrites the existing row; exactly one row ever exists.
 *
 * Field notes (all from spec §11 table):
 *   week_start_day        — day name, e.g. "MONDAY"
 *   week_end_day          — e.g. "SUNDAY"
 *   payment_day           — e.g. "MONDAY" (following week)
 *   half_day_fraction_pct — stored as an integer percentage (50 = 50%)
 *                           to remain integer-only (G-1). The wage engine
 *                           interprets it as numerator/100.
 *   absent_value          — always 0 per spec; kept configurable
 *   overtime_entry        — "FLAT_AMOUNT" in V1
 *   allow_two_sites_one_day  — false in V1
 *   allow_two_roles_one_day  — false in V1
 *
 * AMBIGUITY NOTE: spec §5.11 does not list field names; these are inferred
 * from §11. Confirm with the contractor before building the Settings screen.
 */
@Entity(tableName = "settings")
data class Settings(
    /** Always 1 — enforces the single-row invariant. */
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = 1,

    @ColumnInfo(name = "week_start_day") val weekStartDay: String = "MONDAY",
    @ColumnInfo(name = "week_end_day") val weekEndDay: String = "SUNDAY",
    @ColumnInfo(name = "payment_day") val paymentDay: String = "MONDAY",

    /**
     * Half-day wage as integer percentage of full wage (50 = 50%).
     * Rule C-3 rounding applies when applying this fraction.
     */
    @ColumnInfo(name = "half_day_fraction_pct") val halfDayFractionPct: Int = 50,

    @ColumnInfo(name = "absent_value") val absentValue: Int = 0,
    @ColumnInfo(name = "overtime_entry") val overtimeEntry: String = "FLAT_AMOUNT",
    @ColumnInfo(name = "allow_two_sites_one_day") val allowTwoSitesOneDay: Boolean = false,
    @ColumnInfo(name = "allow_two_roles_one_day") val allowTwoRolesOneDay: Boolean = false
)
