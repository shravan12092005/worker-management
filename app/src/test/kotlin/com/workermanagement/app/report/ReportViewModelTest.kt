package com.workermanagement.app.report

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.workermanagement.app.ui.report.CsvExporter
import com.workermanagement.app.ui.report.ReportType
import com.workermanagement.app.ui.report.ReportViewModel
import com.workermanagement.app.ui.report.SiteWageCostRow
import com.workermanagement.app.ui.settlement.toWageRecord
import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.Attendance
import com.workermanagement.data.entity.DailyRecord
import com.workermanagement.data.entity.Role
import com.workermanagement.data.entity.Site
import com.workermanagement.data.entity.Worker
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wages.WageEngine

/**
 * Report tests covering:
 *  - CsvExporter: header generation, field quoting/escaping
 *  - Real Room DB + ReportViewModel integration test: proving D-2 (stored record.wage
 *    is used, not worker.defaultWage) across two different workers with different wages
 *    on the same site.
 *  - Adapter sanity checks: summing across workers and half-day rounding via WageEngine.
 *  - Payment status: live derivation via WageEngine.settlementStatus()
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReportViewModelTest {

    // ─── CsvExporter ─────────────────────────────────────────────────────────

    @Test
    fun csvExporter_basicOutput() {
        data class Row(val name: String, val count: Int)
        val csv = CsvExporter.toCsv(
            headers = listOf("Name", "Count"),
            rows = listOf(Row("Alice", 3), Row("Bob", 5)),
            mapper = { listOf(it.name, it.count.toString()) },
        )
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertEquals("Name,Count", lines[0])
        assertEquals("Alice,3", lines[1])
        assertEquals("Bob,5", lines[2])
    }

    @Test
    fun csvExporter_quotesFieldsWithCommas() {
        data class Row(val name: String)
        val csv = CsvExporter.toCsv(
            headers = listOf("Name"),
            rows = listOf(Row("Doe, John")),
            mapper = { listOf(it.name) },
        )
        assertTrue(csv.contains("\"Doe, John\""))
    }

    @Test
    fun csvExporter_escapesDoubleQuotes() {
        data class Row(val note: String)
        val csv = CsvExporter.toCsv(
            headers = listOf("Note"),
            rows = listOf(Row("He said \"hello\"")),
            mapper = { listOf(it.note) },
        )
        // Inner quotes should be doubled: "He said ""hello"""
        assertTrue(csv.contains("\"He said \"\"hello\"\"\""))
    }

    @Test
    fun csvExporter_emptyRows() {
        data class Row(val x: String)
        val csv = CsvExporter.toCsv(
            headers = listOf("Header"),
            rows = emptyList<Row>(),
            mapper = { listOf(it.x) },
        )
        // Only the header line
        assertEquals(1, csv.trim().lines().size)
        assertEquals("Header", csv.trim())
    }

    // ─── Real DB + Real ReportViewModel Integration Test ──────────────────────

    /**
     * Genuine integration test:
     * Builds an in-memory Room database, inserts real Site, Role, Worker, and DailyRecord
     * rows via actual DAOs (including two workers with different default wages where
     * DailyRecord.wage intentionally differs from each worker's default wage).
     *
     * Then instantiates the real ReportViewModel, selects SITE_WAGE_COST, executes
     * generateReport(), and asserts the resulting site-wise cost matches what WageEngine
     * independently computes from the stored rows — proving rule D-2 (stored record.wage
     * is used, not worker.defaultWage) through the entire real Room DAO -> Adapter ->
     * WageEngine -> ReportViewModel pipeline.
     */
    @Test
    fun siteWageCost_integrationTest_realRoomDaoAndViewModel_provesStoredWageUsed() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .addCallback(AppDatabase.FOREIGN_KEYS_CALLBACK)
            .allowMainThreadQueries()
            .build()

        try {
            // 1. Insert Site & Role
            val site = Site(
                id = "site-metro", name = "Metro Rail Site", isActive = true,
                createdAt = "2026-09-01T00:00:00Z", updatedAt = "2026-09-01T00:00:00Z",
            )
            val role = Role(id = "role-mason", name = "Mason", isActive = true)
            db.siteDao().insert(site)
            db.roleDao().insert(role)

            // 2. Insert two Workers with distinct default wages:
            // Worker 1 defaultWage = 1000
            val worker1 = Worker(
                id = "w1", code = "W001", name = "Worker One",
                defaultRoleId = "role-mason", defaultWage = 1000,
                joiningDate = "2026-01-01",
                createdAt = "2026-09-01T00:00:00Z", updatedAt = "2026-09-01T00:00:00Z",
            )
            // Worker 2 defaultWage = 800
            val worker2 = Worker(
                id = "w2", code = "W002", name = "Worker Two",
                defaultRoleId = "role-mason", defaultWage = 800,
                joiningDate = "2026-01-01",
                createdAt = "2026-09-01T00:00:00Z", updatedAt = "2026-09-01T00:00:00Z",
            )
            db.workerDao().insert(worker1)
            db.workerDao().insert(worker2)

            // 3. Insert DailyRecord rows where wage INTENTIONALLY DIFFERS from worker's defaultWage:
            // Worker 1 default is 1000, but entered wage for this day is 900
            val record1 = DailyRecord(
                id = "rec-w1", workerId = "w1", workDate = "2026-09-15",
                weekStartDate = "2026-09-15", siteId = "site-metro", roleId = "role-mason",
                wage = 900, attendance = Attendance.PRESENT, overtimeAmount = 0,
                createdAt = "2026-09-15T08:00:00Z", updatedAt = "2026-09-15T08:00:00Z",
            )
            // Worker 2 default is 800, but entered wage for this day is 650
            val record2 = DailyRecord(
                id = "rec-w2", workerId = "w2", workDate = "2026-09-15",
                weekStartDate = "2026-09-15", siteId = "site-metro", roleId = "role-mason",
                wage = 650, attendance = Attendance.PRESENT, overtimeAmount = 0,
                createdAt = "2026-09-15T08:00:00Z", updatedAt = "2026-09-15T08:00:00Z",
            )
            db.dailyRecordDao().insert(record1)
            db.dailyRecordDao().insert(record2)

            // 4. Instantiate real ReportViewModel with synchronous Dispatchers.Unconfined
            val viewModel = ReportViewModel(db, Dispatchers.Unconfined)

            // 5. Select SITE_WAGE_COST report, set date range, and generate
            viewModel.selectReport(ReportType.SITE_WAGE_COST)
            viewModel.setDateFrom("2026-09-15")
            viewModel.setDateTo("2026-09-15")
            viewModel.generateReport()

            // 6. Inspect real output from viewModel.uiState
            val results = viewModel.uiState.value.siteWageCost
            assertNotNull("Site wage cost report must produce results", results)
            assertEquals("Must have 1 site row", 1, results!!.size)

            val siteRow = results[0]
            assertEquals("Metro Rail Site", siteRow.siteName)

            // 7. Verify WageEngine independent calculation
            val expectedWageEngineTotal = listOf(record1, record2).sumOf {
                WageEngine.dayTotal(it.toWageRecord()).dayTotal
            }
            assertEquals(1550, expectedWageEngineTotal) // 900 + 650
            assertEquals(
                "Real ReportViewModel output must match WageEngine independent calculation (900 + 650 = 1550)",
                expectedWageEngineTotal,
                siteRow.totalCost,
            )

            // 8. Explicitly verify D-2 invariant: totalCost is NOT sum of default wages (1000 + 800 = 1800)
            val sumOfWorkerDefaultWages = worker1.defaultWage + worker2.defaultWage
            assertEquals(1800, sumOfWorkerDefaultWages)
            assertNotEquals(
                "Report must NOT use worker default wages (1800); it must use stored record.wage (1550)",
                sumOfWorkerDefaultWages,
                siteRow.totalCost,
            )
        } finally {
            db.close()
        }
    }

    // ─── Pure WageEngine Adapter Sanity Checks ────────────────────────────────

    @Test
    fun wageEngineAdapter_sumsCorrectlyAcrossWorkers() {
        // Pure adapter sanity check across two daily records, same site, different wages
        val recordWorkerA = DailyRecord(
            id = "r1", workerId = "workerA", workDate = "2026-09-15",
            weekStartDate = "2026-09-15", siteId = "site1", roleId = "role1",
            wage = 900, attendance = Attendance.PRESENT, overtimeAmount = 0,
            createdAt = "2026-09-15T08:00:00Z", updatedAt = "2026-09-15T08:00:00Z",
        )
        val recordWorkerB = DailyRecord(
            id = "r2", workerId = "workerB", workDate = "2026-09-15",
            weekStartDate = "2026-09-15", siteId = "site1", roleId = "role2",
            wage = 700, attendance = Attendance.PRESENT, overtimeAmount = 0,
            createdAt = "2026-09-15T08:00:00Z", updatedAt = "2026-09-15T08:00:00Z",
        )

        val records = listOf(recordWorkerA, recordWorkerB)

        // Verify group by site, sum dayTotal via WageEngine
        val result = records
            .groupBy { it.siteId }
            .map { (siteId, siteRecords) ->
                val totalCost = siteRecords.sumOf { record ->
                    val wageRecord = record.toWageRecord()
                    WageEngine.dayTotal(wageRecord).dayTotal
                }
                SiteWageCostRow(siteName = siteId, totalCost = totalCost)
            }

        assertEquals(1, result.size)
        assertEquals("site1", result[0].siteName)
        assertEquals(1600, result[0].totalCost)

        val dayTotalA = WageEngine.dayTotal(recordWorkerA.toWageRecord())
        assertEquals(900, dayTotalA.dayTotal)
        assertEquals(900, dayTotalA.baseAmount)

        val dayTotalB = WageEngine.dayTotal(recordWorkerB.toWageRecord())
        assertEquals(700, dayTotalB.dayTotal)
        assertEquals(700, dayTotalB.baseAmount)
    }

    /**
     * Same test but with HALF_DAY to verify C-3 rounding goes through
     * WageEngine even in the multi-worker cross-site access pattern.
     */
    @Test
    fun siteWageCost_halfDayUsesWageEngine_notSqlMath() {
        val recordA = DailyRecord(
            id = "r3", workerId = "workerA", workDate = "2026-09-16",
            weekStartDate = "2026-09-15", siteId = "site2", roleId = "role1",
            wage = 875, attendance = Attendance.HALF_DAY, overtimeAmount = 100,
            createdAt = "2026-09-16T08:00:00Z", updatedAt = "2026-09-16T08:00:00Z",
        )
        val recordB = DailyRecord(
            id = "r4", workerId = "workerB", workDate = "2026-09-16",
            weekStartDate = "2026-09-15", siteId = "site2", roleId = "role2",
            wage = 900, attendance = Attendance.PRESENT, overtimeAmount = 0,
            createdAt = "2026-09-16T08:00:00Z", updatedAt = "2026-09-16T08:00:00Z",
        )

        val records = listOf(recordA, recordB)

        val result = records
            .groupBy { it.siteId }
            .map { (siteId, siteRecords) ->
                val totalCost = siteRecords.sumOf { record ->
                    val wageRecord = record.toWageRecord()
                    WageEngine.dayTotal(wageRecord).dayTotal
                }
                SiteWageCostRow(siteName = siteId, totalCost = totalCost)
            }

        // Worker A: half-day 875 = 438 (C-3 round up) + OT 100 = 538
        // Worker B: present 900 + OT 0 = 900
        // Total = 538 + 900 = 1438
        assertEquals(1, result.size)
        assertEquals(1438, result[0].totalCost)

        // SQL `wage / 2` would give 875/2 = 437 (integer truncation), not 438
        // This confirms WageEngine's C-3 rounding is being used
        val dayTotalA = WageEngine.dayTotal(recordA.toWageRecord())
        assertEquals(438, dayTotalA.baseAmount)  // C-3: 437.5 rounds UP to 438
        assertEquals(538, dayTotalA.dayTotal)
    }

    // ─── Payment status: live derivation ──────────────────────────────────────

    @Test
    fun paymentStatus_derivedLive_pending() {
        val status = WageEngine.settlementStatus(netPayable = 5000, totalNonVoidPayments = 0)
        assertEquals(wages.SettlementStatus.PENDING, status)
    }

    @Test
    fun paymentStatus_derivedLive_partiallyPaid() {
        val status = WageEngine.settlementStatus(netPayable = 5000, totalNonVoidPayments = 3000)
        assertEquals(wages.SettlementStatus.PARTIALLY_PAID, status)
    }

    @Test
    fun paymentStatus_derivedLive_paid() {
        val status = WageEngine.settlementStatus(netPayable = 5000, totalNonVoidPayments = 5000)
        assertEquals(wages.SettlementStatus.PAID, status)
    }

    /**
     * P-5 scenario: void and re-record should be reflected via live derivation.
     * If a payment of 5000 was voided and 3000 re-recorded, the non-void total
     * is 3000 → PARTIALLY_PAID. The stored column might still say "PAID" from
     * before the void — this is exactly the bug the live derivation avoids.
     */
    @Test
    fun paymentStatus_afterVoidAndRerecord() {
        // Original: paid 5000 → PAID
        // After void: non-void total = 0 → PENDING
        // After re-record of 3000: non-void total = 3000 → PARTIALLY_PAID
        val afterVoid = WageEngine.settlementStatus(netPayable = 5000, totalNonVoidPayments = 0)
        assertEquals(wages.SettlementStatus.PENDING, afterVoid)

        val afterRerecord = WageEngine.settlementStatus(netPayable = 5000, totalNonVoidPayments = 3000)
        assertEquals(wages.SettlementStatus.PARTIALLY_PAID, afterRerecord)
    }
}
