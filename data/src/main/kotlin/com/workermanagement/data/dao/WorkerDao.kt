package com.workermanagement.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.workermanagement.data.entity.Worker

@Dao
interface WorkerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(worker: Worker)

    @Update
    fun update(worker: Worker)

    @Query("SELECT * FROM worker WHERE id = :id")
    fun getById(id: String): Worker?

    @Query("SELECT * FROM worker ORDER BY name ASC")
    fun getAll(): List<Worker>

    @Query("SELECT * FROM worker WHERE is_active = 1 ORDER BY name ASC")
    fun getActive(): List<Worker>

    /**
     * §8 — case-insensitive partial match on name, code, or phone.
     * SQLite LIKE is case-insensitive for ASCII; for Unicode, LOWER() is used.
     */
    @Query("""
        SELECT * FROM worker
        WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(code) LIKE '%' || LOWER(:query) || '%'
           OR (phone IS NOT NULL AND LOWER(phone) LIKE '%' || LOWER(:query) || '%')
        ORDER BY name ASC
    """)
    fun searchByNameOrCodeOrPhone(query: String): List<Worker>

    /**
     * Returns workers who have a positive outstanding advance balance.
     * Balance = SUM(ADVANCE_GIVEN) − SUM(DEDUCTION) − SUM(WRITE_OFF) per rule A-1.
     * Used for the Workers list "has outstanding advance" filter (§7 screen 4)
     * and the Outstanding Advances report (§9).
     */
    @Query("""
        SELECT w.* FROM worker w
        WHERE w.is_active = 1
          AND (
            SELECT COALESCE(
              SUM(CASE WHEN a.type = 'ADVANCE_GIVEN' THEN a.amount
                       WHEN a.type IN ('DEDUCTION','WRITE_OFF') THEN -a.amount
                       ELSE 0 END), 0)
            FROM advance_txn a WHERE a.worker_id = w.id
          ) > 0
        ORDER BY w.name ASC
    """)
    fun getWorkersWithOutstandingAdvance(): List<Worker>

    /**
     * Workers who have no PAID or PARTIALLY_PAID settlement for the given week.
     * Used for "unpaid this week" filter on the Workers list (§7 screen 4).
     */
    @Query("""
        SELECT w.* FROM worker w
        WHERE w.is_active = 1
          AND NOT EXISTS (
            SELECT 1 FROM weekly_settlement ws
            WHERE ws.worker_id = w.id
              AND ws.week_start_date = :weekStartDate
              AND ws.status != 'PENDING'
          )
        ORDER BY w.name ASC
    """)
    fun getWorkersUnpaidForWeek(weekStartDate: String): List<Worker>
}
