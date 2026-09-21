package com.workermanagement.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workermanagement.data.entity.AdvanceTxn

@Dao
interface AdvanceTxnDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(txn: AdvanceTxn)

    @Query("SELECT * FROM advance_txn WHERE worker_id = :workerId ORDER BY txn_date ASC")
    fun getForWorker(workerId: String): List<AdvanceTxn>

    /**
     * Outstanding advance balance for a worker — rule A-1.
     *   balance = SUM(ADVANCE_GIVEN) − SUM(DEDUCTION) − SUM(WRITE_OFF)
     * Returns 0 if the worker has no transactions (A-2).
     */
    @Query("""
        SELECT COALESCE(
            SUM(CASE
                    WHEN type = 'ADVANCE_GIVEN' THEN amount
                    WHEN type IN ('DEDUCTION', 'WRITE_OFF') THEN -amount
                    ELSE 0
                END), 0)
        FROM advance_txn
        WHERE worker_id = :workerId
    """)
    fun getAdvanceBalance(workerId: String): Int

    /**
     * Outstanding advance balances for all workers, descending —
     * Outstanding advances report (§9). Only workers with balance > 0
     * are returned (A-2, A-8).
     */
    @Query("""
        SELECT worker_id,
               SUM(CASE
                       WHEN type = 'ADVANCE_GIVEN' THEN amount
                       WHEN type IN ('DEDUCTION', 'WRITE_OFF') THEN -amount
                       ELSE 0
                   END) AS balance
        FROM advance_txn
        GROUP BY worker_id
        HAVING balance > 0
        ORDER BY balance DESC
    """)
    fun getOutstandingBalances(): List<WorkerBalance>
}

data class WorkerBalance(val worker_id: String, val balance: Int)
