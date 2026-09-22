package com.workermanagement.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workermanagement.data.entity.WeeklySettlement

@Dao
interface SettlementDao {

    /**
     * Inserts a settlement snapshot.
     * Throws SQLiteConstraintException on duplicate (worker_id, week_start_date) — S-2.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(settlement: WeeklySettlement)

    @Query("SELECT * FROM weekly_settlement WHERE id = :id")
    fun getById(id: String): WeeklySettlement?

    /**
     * All settlements, newest first — used by the Payments screen to list
     * settlements by derived status (PaymentViewModel derives status via
     * WageEngine.settlementStatus; it is never read from this column).
     */
    @Query("SELECT * FROM weekly_settlement ORDER BY week_start_date DESC, worker_id ASC")
    fun getAllSettlements(): List<WeeklySettlement>

    @Query("""
        SELECT * FROM weekly_settlement
        WHERE worker_id = :workerId AND week_start_date = :weekStartDate
    """)
    fun getByWorkerAndWeek(workerId: String, weekStartDate: String): WeeklySettlement?

    @Query("SELECT * FROM weekly_settlement WHERE worker_id = :workerId ORDER BY week_start_date DESC")
    fun getSettlementsForWorker(workerId: String): List<WeeklySettlement>

    /**
     * All PENDING settlements — payment status report (§9).
     */
    @Query("SELECT * FROM weekly_settlement WHERE status = 'PENDING' ORDER BY week_start_date DESC")
    fun getPendingSettlements(): List<WeeklySettlement>

    /**
     * Weekly wage report — one row per worker for a given week (§9).
     */
    @Query("""
        SELECT * FROM weekly_settlement
        WHERE week_start_date = :weekStartDate
        ORDER BY worker_id ASC
    """)
    fun getSettlementsForWeek(weekStartDate: String): List<WeeklySettlement>

    /**
     * Deletes a settlement — used during settlement reversal (S-6).
     * Only permitted before any non-void payment exists.
     */
    @Query("DELETE FROM weekly_settlement WHERE id = :id")
    fun deleteById(id: String)
}
