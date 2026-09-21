package com.workermanagement.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workermanagement.data.entity.Adjustment

@Dao
interface AdjustmentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(adjustment: Adjustment)

    @Query("SELECT * FROM adjustment WHERE settlement_id = :settlementId ORDER BY created_at ASC")
    fun getForSettlement(settlementId: String): List<Adjustment>

    /**
     * Pending adjustments report (§9) — all adjustments not yet included
     * in any subsequent settlement.
     */
    @Query("""
        SELECT * FROM adjustment
        WHERE settled_in_settlement_id IS NULL
        ORDER BY created_at ASC
    """)
    fun getPendingAdjustments(): List<Adjustment>

    /**
     * Marks an adjustment as settled in a later week's settlement (J-5).
     */
    @Query("""
        UPDATE adjustment
        SET settled_in_settlement_id = :settledInSettlementId
        WHERE id = :id
    """)
    fun markSettled(id: String, settledInSettlementId: String)
}
