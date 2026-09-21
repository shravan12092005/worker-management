package com.workermanagement.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.workermanagement.data.entity.Site

@Dao
interface SiteDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(site: Site)

    @Update
    fun update(site: Site)

    @Query("SELECT * FROM site WHERE id = :id")
    fun getById(id: String): Site?

    @Query("SELECT * FROM site WHERE is_active = 1 ORDER BY name ASC")
    fun getActive(): List<Site>

    @Query("SELECT * FROM site ORDER BY name ASC")
    fun getAll(): List<Site>

    /**
     * Returns workers assigned to a site on a given date, with their
     * attendance records — drives the attendance screen (§6.1).
     * Returns the daily_record ids only; the caller fetches full records.
     */
    @Query("""
        SELECT worker_id FROM daily_record
        WHERE site_id = :siteId AND work_date = :date
        ORDER BY worker_id ASC
    """)
    fun getWorkerIdsAtSiteOnDate(siteId: String, date: String): List<String>

    /**
     * Headcount per site on a given date — site-wise attendance report (§9).
     * Returns (site_id, count) pairs; sites with 0 workers that day are excluded.
     */
    @Query("""
        SELECT site_id, COUNT(*) AS headcount
        FROM daily_record
        WHERE work_date = :date AND attendance != 'ABSENT'
        GROUP BY site_id
    """)
    fun getHeadcountPerSiteOnDate(date: String): List<SiteHeadcount>

    /**
     * Total wages generated per site for a date range — site-wise wage cost (§9).
     */
    @Query("""
        SELECT dr.site_id,
               SUM(CASE WHEN dr.attendance = 'PRESENT'  THEN dr.wage
                        WHEN dr.attendance = 'HALF_DAY' THEN dr.wage / 2
                        ELSE 0 END
               + dr.overtime_amount) AS totalWageCost
        FROM daily_record dr
        WHERE dr.work_date >= :from AND dr.work_date <= :to
        GROUP BY dr.site_id
    """)
    fun getSiteWageCotsInRange(from: String, to: String): List<SiteWageCost>
}

data class SiteHeadcount(val site_id: String, val headcount: Int)
data class SiteWageCost(val site_id: String, val totalWageCost: Int)
