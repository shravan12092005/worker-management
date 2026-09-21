package com.workermanagement.data.sync

import com.workermanagement.data.BuildConfig
import com.workermanagement.data.db.AppDatabase
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal push-only sync manager.
 *
 * Reads rows where synced = false, pushes them to Supabase via its REST
 * API in dependency order, then marks them synced locally on success.
 *
 * This is a stopgap while the rest of the app is built. Full background
 * sync (queue, retry-with-backoff, pull, conflict resolution per §10)
 * is a separate, later task.
 *
 * Credentials are read only from [BuildConfig] — never hardcoded here.
 *
 * @param db        The Room database instance.
 * @param httpClient OkHttpClient to use for outbound requests. Injectable
 *                   for testing (e.g. with MockWebServer).
 */
class SyncManager(
    private val db: AppDatabase,
    private val httpClient: OkHttpClient = OkHttpClient(),
    /** Override the base URL — used in tests to point at MockWebServer. */
    baseUrlOverride: String? = null,
) {
    // Base URL already ends with "/rest/v1/" — append table name directly.
    private val baseUrl: String = baseUrlOverride ?: BuildConfig.SUPABASE_URL
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY
    private val json = "application/json".toMediaType()

    /**
     * Pushes all unsynced rows to Supabase in dependency order.
     *
     * Dependency order (so server FK constraints are always satisfied):
     *  1. role          — no FKs
     *  2. site          — no FKs
     *  3. worker        — FK → role
     *  4. daily_record  — FK → worker, site, role
     *  5. weekly_settlement — FK → worker
     *  6. advance_txn   — FK → worker, weekly_settlement (nullable)
     *  7. payment       — FK → weekly_settlement
     *  8. adjustment    — FK → weekly_settlement ×2 (nullable)
     *  9. settings      — singleton, no FKs
     * 10. audit_log     — changed_by has no server FK (app_user not yet synced)
     *
     * Returns [SyncResult.Success] with total rows synced, or
     * [SyncResult.Failure] naming the table that failed and the exception.
     * On failure, rows synced in completed earlier phases remain marked
     * synced; the failing phase and all later phases are left unsynced.
     */
    fun syncNow(): SyncResult {
        var totalSynced = 0
        val dao = db.syncDao()

        // ── Phase 1: role ─────────────────────────────────────────────────
        val roles = dao.unsyncedRoles()
        if (roles.isNotEmpty()) {
            val result = pushRows("role", roles.toJsonArray { it.toJson() })
            if (result != null) return SyncResult.Failure("role", result)
            dao.markRolesSynced(roles.map { it.id })
            totalSynced += roles.size
        }

        // ── Phase 2: site ─────────────────────────────────────────────────
        val sites = dao.unsyncedSites()
        if (sites.isNotEmpty()) {
            val result = pushRows("site", sites.toJsonArray { it.toJson() })
            if (result != null) return SyncResult.Failure("site", result)
            dao.markSitesSynced(sites.map { it.id })
            totalSynced += sites.size
        }

        // ── Phase 3: worker ───────────────────────────────────────────────
        val workers = dao.unsyncedWorkers()
        if (workers.isNotEmpty()) {
            val result = pushRows("worker", workers.toJsonArray { it.toJson() })
            if (result != null) return SyncResult.Failure("worker", result)
            dao.markWorkersSynced(workers.map { it.id })
            totalSynced += workers.size
        }

        // ── Phase 4: daily_record ─────────────────────────────────────────
        val records = dao.unsyncedDailyRecords()
        if (records.isNotEmpty()) {
            val result = pushRows("daily_record", records.toJsonArray { it.toJson() })
            if (result != null) return SyncResult.Failure("daily_record", result)
            dao.markDailyRecordsSynced(records.map { it.id })
            totalSynced += records.size
        }

        // ── Phase 5: weekly_settlement ────────────────────────────────────
        val settlements = dao.unsyncedSettlements()
        if (settlements.isNotEmpty()) {
            val result = pushRows("weekly_settlement", settlements.toJsonArray { it.toJson() })
            if (result != null) return SyncResult.Failure("weekly_settlement", result)
            dao.markSettlementsSynced(settlements.map { it.id })
            totalSynced += settlements.size
        }

        // ── Phase 6: advance_txn ──────────────────────────────────────────
        val advances = dao.unsyncedAdvanceTxns()
        if (advances.isNotEmpty()) {
            val result = pushRows("advance_txn", advances.toJsonArray { it.toJson() })
            if (result != null) return SyncResult.Failure("advance_txn", result)
            dao.markAdvanceTxnsSynced(advances.map { it.id })
            totalSynced += advances.size
        }

        // ── Phase 7: payment ──────────────────────────────────────────────
        val payments = dao.unsyncedPayments()
        if (payments.isNotEmpty()) {
            val result = pushRows("payment", payments.toJsonArray { it.toJson() })
            if (result != null) return SyncResult.Failure("payment", result)
            dao.markPaymentsSynced(payments.map { it.id })
            totalSynced += payments.size
        }

        // ── Phase 8: adjustment ───────────────────────────────────────────
        val adjustments = dao.unsyncedAdjustments()
        if (adjustments.isNotEmpty()) {
            val result = pushRows("adjustment", adjustments.toJsonArray { it.toJson() })
            if (result != null) return SyncResult.Failure("adjustment", result)
            dao.markAdjustmentsSynced(adjustments.map { it.id })
            totalSynced += adjustments.size
        }

        // ── Phase 9: settings (singleton) ─────────────────────────────────
        val settingsList = dao.unsyncedSettings()
        if (settingsList.isNotEmpty()) {
            val result = pushRows("settings", settingsList.toJsonArray { it.toJson() })
            if (result != null) return SyncResult.Failure("settings", result)
            dao.markSettingsSynced(settingsList.map { it.singletonId })
            totalSynced += settingsList.size
        }

        // ── Phase 10: audit_log ───────────────────────────────────────────
        val auditLogs = dao.unsyncedAuditLogs()
        if (auditLogs.isNotEmpty()) {
            val result = pushRows("audit_log", auditLogs.toJsonArray { it.toJson() })
            if (result != null) return SyncResult.Failure("audit_log", result)
            dao.markAuditLogsSynced(auditLogs.map { it.id })
            totalSynced += auditLogs.size
        }

        return SyncResult.Success(totalSynced)
    }

    // ─── HTTP ─────────────────────────────────────────────────────────────

    /**
     * POSTs [body] to `/rest/v1/{table}` with upsert semantics.
     * Returns null on success (HTTP 200 or 201), or an Exception on failure.
     * Never throws — callers check the return value.
     */
    private fun pushRows(table: String, body: JSONArray): Exception? {
        if (body.length() == 0) return null
        return try {
            val request = Request.Builder()
                .url("$baseUrl$table")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                // merge-duplicates: upsert on conflict (idempotent push)
                // return=minimal: server does not echo the rows back (saves bandwidth)
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body.toString().toRequestBody(json))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    val errorBody = response.body?.string() ?: "(no body)"
                    Exception("HTTP ${response.code} pushing $table: $errorBody")
                }
            }
        } catch (e: Exception) {
            e
        }
    }

    // ─── JSON serializers ─────────────────────────────────────────────────
    // `synced` is intentionally omitted — it is a local-only tracking field.

    private fun Role.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("is_active", isActive)
    }

    private fun Site.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        putOpt("location", location)
        put("is_active", isActive)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun Worker.toJson() = JSONObject().apply {
        put("id", id)
        put("code", code)
        put("name", name)
        putOpt("phone", phone)
        putOpt("address", address)
        put("default_role_id", defaultRoleId)
        put("default_wage", defaultWage)
        put("joining_date", joiningDate)
        put("is_active", isActive)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun DailyRecord.toJson() = JSONObject().apply {
        put("id", id)
        put("worker_id", workerId)
        put("work_date", workDate)
        put("week_start_date", weekStartDate)
        put("site_id", siteId)
        put("role_id", roleId)
        put("wage", wage)
        put("attendance", attendance.name)
        put("overtime_amount", overtimeAmount)
        putOpt("note", note)
        put("is_locked", isLocked)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun WeeklySettlement.toJson() = JSONObject().apply {
        put("id", id)
        put("worker_id", workerId)
        put("week_start_date", weekStartDate)
        put("base_earnings", baseEarnings)
        put("overtime_earnings", overtimeEarnings)
        put("gross_earnings", grossEarnings)
        put("advance_deduction", advanceDeduction)
        put("net_payable", netPayable)
        put("status", status.name)
        put("finalized_at", finalizedAt)
    }

    private fun AdvanceTxn.toJson() = JSONObject().apply {
        put("id", id)
        put("worker_id", workerId)
        put("txn_date", txnDate)
        put("type", type.name)
        put("amount", amount)
        putOpt("settlement_id", settlementId)
        putOpt("note", note)
        put("created_at", createdAt)
    }

    private fun Payment.toJson() = JSONObject().apply {
        put("id", id)
        put("settlement_id", settlementId)
        put("paid_on", paidOn)
        put("amount", amount)
        put("method", method.name)
        putOpt("note", note)
        put("is_void", isVoid)
        put("created_at", createdAt)
    }

    private fun Adjustment.toJson() = JSONObject().apply {
        put("id", id)
        put("settlement_id", settlementId)
        put("amount", amount)
        put("reason", reason)
        put("created_at", createdAt)
        putOpt("settled_in_settlement_id", settledInSettlementId)
    }

    private fun Settings.toJson() = JSONObject().apply {
        put("singleton_id", singletonId)
        put("week_start_day", weekStartDay)
        put("week_end_day", weekEndDay)
        put("payment_day", paymentDay)
        put("half_day_fraction_pct", halfDayFractionPct)
        put("absent_value", absentValue)
        put("overtime_entry", overtimeEntry)
        put("allow_two_sites_one_day", allowTwoSitesOneDay)
        put("allow_two_roles_one_day", allowTwoRolesOneDay)
    }

    private fun AuditLog.toJson() = JSONObject().apply {
        put("id", id)
        put("entity_type", entityType)
        put("entity_id", entityId)
        put("field", field)
        putOpt("old_value", oldValue)
        putOpt("new_value", newValue)
        put("changed_by", changedBy)
        put("changed_at", changedAt)
    }

    // ─── Helper ───────────────────────────────────────────────────────────

    private fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray {
        val arr = JSONArray()
        forEach { arr.put(mapper(it)) }
        return arr
    }
}
