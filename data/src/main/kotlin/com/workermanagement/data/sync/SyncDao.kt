package com.workermanagement.data.sync

import androidx.room.Dao
import androidx.room.Query
import com.workermanagement.data.entity.AdvanceTxn
import com.workermanagement.data.entity.Adjustment
import com.workermanagement.data.entity.AuditLog
import com.workermanagement.data.entity.DailyRecord
import com.workermanagement.data.entity.Payment
import com.workermanagement.data.entity.Role
import com.workermanagement.data.entity.Settings
import com.workermanagement.data.entity.Site
import com.workermanagement.data.entity.WeeklySettlement
import com.workermanagement.data.entity.Worker

/**
 * Sync-specific DAO — only reads unsynced rows and marks rows as synced.
 * Kept separate from the feature DAOs so sync concerns don't bleed into
 * the application data access layer.
 */
@Dao
interface SyncDao {

    // ─── Unsynced reads ───────────────────────────────────────────────────

    @Query("SELECT * FROM role WHERE synced = 0")
    fun unsyncedRoles(): List<Role>

    @Query("SELECT * FROM site WHERE synced = 0")
    fun unsyncedSites(): List<Site>

    @Query("SELECT * FROM worker WHERE synced = 0")
    fun unsyncedWorkers(): List<Worker>

    @Query("SELECT * FROM daily_record WHERE synced = 0")
    fun unsyncedDailyRecords(): List<DailyRecord>

    @Query("SELECT * FROM weekly_settlement WHERE synced = 0")
    fun unsyncedSettlements(): List<WeeklySettlement>

    @Query("SELECT * FROM advance_txn WHERE synced = 0")
    fun unsyncedAdvanceTxns(): List<AdvanceTxn>

    @Query("SELECT * FROM payment WHERE synced = 0")
    fun unsyncedPayments(): List<Payment>

    @Query("SELECT * FROM adjustment WHERE synced = 0")
    fun unsyncedAdjustments(): List<Adjustment>

    @Query("SELECT * FROM settings WHERE synced = 0")
    fun unsyncedSettings(): List<Settings>

    @Query("SELECT * FROM audit_log WHERE synced = 0")
    fun unsyncedAuditLogs(): List<AuditLog>

    // ─── Mark synced ──────────────────────────────────────────────────────

    @Query("UPDATE role SET synced = 1 WHERE id IN (:ids)")
    fun markRolesSynced(ids: List<String>)

    @Query("UPDATE site SET synced = 1 WHERE id IN (:ids)")
    fun markSitesSynced(ids: List<String>)

    @Query("UPDATE worker SET synced = 1 WHERE id IN (:ids)")
    fun markWorkersSynced(ids: List<String>)

    @Query("UPDATE daily_record SET synced = 1 WHERE id IN (:ids)")
    fun markDailyRecordsSynced(ids: List<String>)

    @Query("UPDATE weekly_settlement SET synced = 1 WHERE id IN (:ids)")
    fun markSettlementsSynced(ids: List<String>)

    @Query("UPDATE advance_txn SET synced = 1 WHERE id IN (:ids)")
    fun markAdvanceTxnsSynced(ids: List<String>)

    @Query("UPDATE payment SET synced = 1 WHERE id IN (:ids)")
    fun markPaymentsSynced(ids: List<String>)

    @Query("UPDATE adjustment SET synced = 1 WHERE id IN (:ids)")
    fun markAdjustmentsSynced(ids: List<String>)

    /** Settings singleton — keyed by singleton_id (always 1). */
    @Query("UPDATE settings SET synced = 1 WHERE singleton_id IN (:ids)")
    fun markSettingsSynced(ids: List<Int>)

    @Query("UPDATE audit_log SET synced = 1 WHERE id IN (:ids)")
    fun markAuditLogsSynced(ids: List<String>)
}
