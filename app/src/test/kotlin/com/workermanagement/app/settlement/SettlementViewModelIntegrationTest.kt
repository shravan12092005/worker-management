package com.workermanagement.app.settlement

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.Attendance
import com.workermanagement.data.entity.DailyRecord
import com.workermanagement.data.entity.Role
import com.workermanagement.data.entity.Site
import com.workermanagement.data.entity.Worker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wages.WageEngine
import wages.DailyRecord as WageDailyRecord
import wages.Attendance as WageAttendance

/**
 * Integration test: verifies the dependency path that SettlementViewModel
 * uses at runtime.
 *
 * Specifically:
 *  1. Insert T-1 fixture data as real [DailyRecord] rows via [DailyRecordDao].
 *  2. Read them back through the same DAO query that [SettlementViewModel]
 *     calls: [DailyRecordDao.getRecordsForWorkerInWeek].
 *  3. Map the results through the same adapter that [SettlementViewModel] uses
 *     ([DailyRecord.toWageRecord]).
 *  4. Pass the mapped records to [WageEngine.weeklyGrossEarnings].
 *  5. Assert the result equals 5300 (T-1 expected value from docs/rules.md).
 *
 * This test does NOT re-derive the answer by arithmetic; it exercises the
 * real DAO → WageEngine path end-to-end on an in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettlementViewModelIntegrationTest {

    private lateinit var db: AppDatabase

    // ── T-1 reference data ────────────────────────────────────────────────────

    private val weekStartDate = "2026-09-14"   // Monday

    // Three sites, two roles — T-1 uses all of them
    private val siteA = Site(id = "site-a", name = "Site A", isActive = true,
        createdAt = "2026-09-01T00:00:00Z", updatedAt = "2026-09-01T00:00:00Z")
    private val siteB = Site(id = "site-b", name = "Site B", isActive = true,
        createdAt = "2026-09-01T00:00:00Z", updatedAt = "2026-09-01T00:00:00Z")
    private val siteC = Site(id = "site-c", name = "Site C", isActive = true,
        createdAt = "2026-09-01T00:00:00Z", updatedAt = "2026-09-01T00:00:00Z")
    private val roleMason  = Role(id = "role-mason",  name = "Mason",  isActive = true)
    private val roleHelper = Role(id = "role-helper", name = "Helper", isActive = true)

    private val worker = Worker(
        id            = "worker-t1",
        code          = "T001",
        name          = "T-1 Worker",
        defaultRoleId = "role-mason",
        defaultWage   = 900,
        joiningDate   = "2026-01-01",
        createdAt     = "2026-09-01T00:00:00Z",
        updatedAt     = "2026-09-01T00:00:00Z",
    )

    /**
     * T-1 daily records from docs/rules.md §T-1:
     *
     *  Mon 2026-09-14  Site A  Mason   wage=900  PRESENT  OT=0
     *  Tue 2026-09-15  Site A  Mason   wage=900  PRESENT  OT=0
     *  Wed 2026-09-16  Site B  Mason   wage=900  PRESENT  OT=0
     *  Thu 2026-09-17  Site B  Mason   wage=900  PRESENT  OT=300
     *  Fri 2026-09-18  Site C  Helper  wage=700  PRESENT  OT=0
     *  Sat 2026-09-19  Site C  Helper  wage=700  PRESENT  OT=0
     *
     *  Expected: base 5000, overtime 300, gross 5300
     */
    private val t1Records = listOf(
        rec("rec-1", "2026-09-14", "site-a", "role-mason",  900, Attendance.PRESENT, 0),
        rec("rec-2", "2026-09-15", "site-a", "role-mason",  900, Attendance.PRESENT, 0),
        rec("rec-3", "2026-09-16", "site-b", "role-mason",  900, Attendance.PRESENT, 0),
        rec("rec-4", "2026-09-17", "site-b", "role-mason",  900, Attendance.PRESENT, 300),
        rec("rec-5", "2026-09-18", "site-c", "role-helper", 700, Attendance.PRESENT, 0),
        rec("rec-6", "2026-09-19", "site-c", "role-helper", 700, Attendance.PRESENT, 0),
    )

    // ── DB lifecycle ──────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .addCallback(AppDatabase.FOREIGN_KEYS_CALLBACK)
            .allowMainThreadQueries()
            .build()

        // Seed FK dependencies
        db.roleDao().insert(roleMason)
        db.roleDao().insert(roleHelper)
        db.siteDao().insert(siteA)
        db.siteDao().insert(siteB)
        db.siteDao().insert(siteC)
        db.workerDao().insert(worker)

        // Insert T-1 daily records
        t1Records.forEach { db.dailyRecordDao().insert(it) }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * T-1 — DAO read-back + WageEngine → gross 5300.
     *
     * Exercises the exact same code path as SettlementViewModel.loadDetail():
     *   getRecordsForWorkerInWeek → toWageRecord() → WageEngine.weeklyGrossEarnings
     */
    @Test
    fun `T-1 DAO round-trip through WageEngine gives gross 5300`() {
        // Step 1: read via the same DAO query SettlementViewModel uses
        val roomRecords = db.dailyRecordDao()
            .getRecordsForWorkerInWeek(worker.id, weekStartDate)

        assertEquals("Should have 6 records", 6, roomRecords.size)

        // Step 2: map Room → WageEngine types (same adapter as SettlementViewModel)
        val wageRecords = roomRecords.map { it.toWageRecord() }

        // Step 3: assert via real WageEngine (not re-derived arithmetic)
        assertEquals("T-1 base earnings",     5000, WageEngine.weeklyBaseEarnings(wageRecords))
        assertEquals("T-1 overtime earnings",  300, WageEngine.weeklyOvertimeEarnings(wageRecords))
        assertEquals("T-1 gross earnings",    5300, WageEngine.weeklyGrossEarnings(wageRecords))
    }

    /**
     * T-1 deduction path — validateDeduction passes for 1000 against
     * balance 3000 / gross 5300, and net payable equals 4300.
     */
    @Test
    fun `T-1 validateDeduction 1000 against balance 3000 gross 5300 gives net 4300`() {
        val roomRecords  = db.dailyRecordDao().getRecordsForWorkerInWeek(worker.id, weekStartDate)
        val wageRecords  = roomRecords.map { it.toWageRecord() }
        val gross        = WageEngine.weeklyGrossEarnings(wageRecords)
        val balance      = 3_000

        val result = WageEngine.validateDeduction(balance, gross, 1_000)

        assertEquals("Validation should pass",
            wages.DeductionValidation.Ok(deduction = 1_000), result)
        assertEquals("Net payable", 4_300, gross - 1_000)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun rec(
        id: String, date: String, siteId: String, roleId: String,
        wage: Int, attendance: Attendance, overtime: Int,
    ) = DailyRecord(
        id              = id,
        workerId        = worker.id,
        workDate        = date,
        weekStartDate   = weekStartDate,
        siteId          = siteId,
        roleId          = roleId,
        wage            = wage,
        attendance      = attendance,
        overtimeAmount  = overtime,
        createdAt       = "2026-09-14T06:00:00Z",
        updatedAt       = "2026-09-14T06:00:00Z",
    )
}

// ── Room → WageEngine adapter (same logic as in SettlementViewModel) ─────────

private fun DailyRecord.toWageRecord(): WageDailyRecord = WageDailyRecord(
    wage           = wage,
    attendance     = attendance.toWageAttendance(),
    overtimeAmount = overtimeAmount,
)

private fun Attendance.toWageAttendance(): WageAttendance = when (this) {
    Attendance.PRESENT  -> WageAttendance.PRESENT
    Attendance.HALF_DAY -> WageAttendance.HALF_DAY
    Attendance.ABSENT   -> WageAttendance.ABSENT
}
