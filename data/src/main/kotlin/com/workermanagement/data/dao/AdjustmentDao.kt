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
     * Pending adjustments for a specific worker (J-5, SettlementViewModel).
     * Joins through weekly_settlement to filter by worker_id at the DB level
     * rather than fetching all rows and filtering in Kotlin.
     */
    @Query("""
        SELECT a.* FROM adjustment a
        INNER JOIN weekly_settlement ws ON ws.id = a.settlement_id
        WHERE a.settled_in_settlement_id IS NULL
          AND ws.worker_id = :workerId
        ORDER BY a.created_at ASC
    """)
    fun getPendingAdjustmentsForWorker(workerId: String): List<Adjustment>

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
