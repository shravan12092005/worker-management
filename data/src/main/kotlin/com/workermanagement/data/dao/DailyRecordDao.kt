package com.workermanagement.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.workermanagement.data.entity.DailyRecord

@Dao
interface DailyRecordDao {

    /**
     * Inserts a daily record.
     * Throws SQLiteConstraintException if (worker_id, work_date) already exists — D-1.
     * The caller must supply week_start_date computed from work_date and settings (W-1).
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(record: DailyRecord)

    @Update
    fun update(record: DailyRecord)

    @Query("SELECT * FROM daily_record WHERE id = :id")
    fun getById(id: String): DailyRecord?

    /**
     * Core weekly query — drives settlement (§6.2) and all weekly reports (§9).
     * Uses the (week_start_date, worker_id) index.
     */
    @Query("""
        SELECT * FROM daily_record
        WHERE worker_id = :workerId AND week_start_date = :weekStartDate
        ORDER BY work_date ASC
    """)
    fun getRecordsForWorkerInWeek(workerId: String, weekStartDate: String): List<DailyRecord>

    /**
     * Worker history — all records for a worker across a date range (§9).
     */
    @Query("""
        SELECT * FROM daily_record
        WHERE worker_id = :workerId
          AND work_date >= :from AND work_date <= :to
        ORDER BY work_date ASC
    """)
    fun getRecordsForWorkerInRange(workerId: String, from: String, to: String): List<DailyRecord>

    /**
     * Attendance screen — all records for a site on a given date (§6.1).
     * Uses the (site_id, work_date) index.
     */
    @Query("""
        SELECT * FROM daily_record
        WHERE site_id = :siteId AND work_date = :date
        ORDER BY worker_id ASC
    """)
    fun getRecordsForSiteOnDate(siteId: String, date: String): List<DailyRecord>

    /**
     * Locks all daily records for a worker in a week (S-3 finalization).
     */
    @Query("""
        UPDATE daily_record SET is_locked = 1, updated_at = :updatedAt
        WHERE worker_id = :workerId AND week_start_date = :weekStartDate
    """)
    fun lockWeekForWorker(workerId: String, weekStartDate: String, updatedAt: String)

    /**
     * Returns unlocked records for a worker's week — used to guard
     * against double-finalization.
     */
    @Query("""
        SELECT * FROM daily_record
        WHERE worker_id = :workerId AND week_start_date = :weekStartDate
          AND is_locked = 0
        ORDER BY work_date ASC
    """)
    fun getUnlockedRecordsForWorkerInWeek(workerId: String, weekStartDate: String): List<DailyRecord>

    /**
     * Weekly attendance report — days worked (non-ABSENT) per worker in a week (§9).
     */
    @Query("""
        SELECT worker_id, COUNT(*) AS daysWorked
        FROM daily_record
        WHERE week_start_date = :weekStartDate AND attendance != 'ABSENT'
        GROUP BY worker_id
    """)
    fun getDaysWorkedInWeek(weekStartDate: String): List<WorkerDaysWorked>

    /**
     * Yesterday's assignment at a site — used to pre-fill today's attendance list (§6.1).
     */
    @Query("""
        SELECT * FROM daily_record
        WHERE site_id = :siteId AND work_date = :yesterday
        ORDER BY worker_id ASC
    """)
    fun getYesterdayAssignmentForSite(siteId: String, yesterday: String): List<DailyRecord>

    /**
     * Raw daily records for a date range — used by site-wise wage cost report (§9).
     * No SQL aggregation or arithmetic; caller groups by site and computes
     * totals via WageEngine.dayTotal() in Kotlin (G-1, C-3 compliance).
     */
    @Query("""
        SELECT * FROM daily_record
        WHERE work_date >= :from AND work_date <= :to
        ORDER BY site_id ASC, work_date ASC
    """)
    fun getRecordsInDateRange(from: String, to: String): List<DailyRecord>

    /**
     * Site-wise attendance — headcount per site per day in range (§9).
     * Uses COUNT(*) only — no monetary arithmetic in SQL.
     */
    @Query("""
        SELECT site_id, work_date, COUNT(*) AS headcount
        FROM daily_record
        WHERE work_date >= :from AND work_date <= :to
          AND attendance != 'ABSENT'
        GROUP BY site_id, work_date
        ORDER BY work_date ASC, site_id ASC
    """)
    fun getHeadcountBySiteInRange(from: String, to: String): List<SiteDateHeadcount>
}

data class WorkerDaysWorked(val worker_id: String, val daysWorked: Int)
data class SiteDateHeadcount(val site_id: String, val work_date: String, val headcount: Int)
