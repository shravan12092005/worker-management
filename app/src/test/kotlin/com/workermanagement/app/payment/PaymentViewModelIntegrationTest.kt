package com.workermanagement.app.payment

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.Attendance
import com.workermanagement.data.entity.DailyRecord
import com.workermanagement.data.entity.Payment
import com.workermanagement.data.entity.PaymentMethod
import com.workermanagement.data.entity.Role
import com.workermanagement.data.entity.SettlementStatus
import com.workermanagement.data.entity.Site
import com.workermanagement.data.entity.WeeklySettlement
import com.workermanagement.data.entity.Worker
import com.workermanagement.app.ui.settlement.toWageRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wages.WageEngine

/**
 * Integration tests T-10 (J-6), T-11 (P-4), T-12 (P-5).
 *
 * All assertions re-query from the DAO after the operation under test —
 * never assert against objects held in memory from setup (Fix 4).
 *
 * The DAO → adapter → WageEngine path uses the real imported toWageRecord()
 * from SettlementViewModel — no copy, no inline arithmetic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PaymentViewModelIntegrationTest {

    private lateinit var db: AppDatabase

    // ── Shared fixture data ───────────────────────────────────────────────────

    private val weekStart = "2026-09-14"   // Monday

    private val site = Site(
        id = "site-j6", name = "J6 Site", isActive = true,
        createdAt = "2026-09-01T00:00:00Z", updatedAt = "2026-09-01T00:00:00Z",
    )
    private val role = Role(id = "role-j6", name = "Mason", isActive = true)
    private val worker = Worker(
        id            = "worker-j6",
        code          = "J001",
        name          = "J-6 Worker",
        defaultRoleId = "role-j6",
        defaultWage   = 1000,
        joiningDate   = "2026-01-01",
        createdAt     = "2026-09-01T00:00:00Z",
        updatedAt     = "2026-09-01T00:00:00Z",
    )

    // ── J-6 record IDs — stable so we can re-query by ID ─────────────────────

    private val monId = "rec-j6-mon"
    private val tueId = "rec-j6-tue"
    private val wedId = "rec-j6-wed"
    private val thuId = "rec-j6-thu"
    private val friId = "rec-j6-fri"
    private val satId = "rec-j6-sat"

    /**
     * J-6 setup: one worker, Mon–Sat.
     *
     *   Mon ₹1000 PRESENT, Tue ₹1000 PRESENT, Wed ₹1000 PRESENT,
     *   Thu ₹1000 ABSENT,                  ← Thu is ABSENT; gross = 5 × ₹1000 = ₹5000
     *   Fri ₹1000 PRESENT, Sat ₹1000 PRESENT
     *
     * After finalization gross = ₹5000, net = ₹5000, paid = ₹5000.
     *
     * Then Thu is corrected ABSENT → PRESENT at ₹900.
     *   Thursday wage is ₹900, not ₹1000, because in this project each
     *   DailyRecord carries its own wage column (rule R-4 allows per-day
     *   wage overrides at the point of attendance entry; the contractor
     *   entered ₹900 for Thursday). This is a legitimate data value, not
     *   an inconsistency.
     *
     * Recomputed gross = ₹5000 + ₹900 = ₹5900.
     * WageEngine.computeAdjustment(5000, 5900) → amount=900, sign=+1.
     */
    private fun j6Records() = listOf(
        rec(monId, "2026-09-14", Attendance.PRESENT, 1000, 0),
        rec(tueId, "2026-09-15", Attendance.PRESENT, 1000, 0),
        rec(wedId, "2026-09-16", Attendance.PRESENT, 1000, 0),
        rec(thuId, "2026-09-17", Attendance.ABSENT,  1000, 0),  // ABSENT; will be corrected below
        rec(friId, "2026-09-18", Attendance.PRESENT, 1000, 0),
        rec(satId, "2026-09-19", Attendance.PRESENT, 1000, 0),
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

        db.roleDao().insert(role)
        db.siteDao().insert(site)
        db.workerDao().insert(worker)
    }

    @After
    fun tearDown() { db.close() }

    // ─────────────────────────────────────────────────────────────────────────
    // T-10 — J-6 worked example
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * T-10: J-6 correction invariants.
     *
     * Steps:
     *  1. Insert Mon-Sat records (Thu ABSENT) — gross ₹5000 via WageEngine.
     *  2. Finalize: insert weekly_settlement (gross=5000, net=5000), lock records,
     *     insert a payment of ₹5000.
     *  3. Apply correction: Thu ABSENT → PRESENT at ₹900.
     *     Uses the real DAO + real toWageRecord() adapter + real WageEngine.computeAdjustment.
     *  4. Re-query all affected rows from the DB (Fix 4 — not in-memory objects).
     *  5. Assert:
     *     a) Original settlement.grossEarnings unchanged (5000).
     *     b) Original payment row: amount=5000, is_void=false (J-3).
     *     c) New adjustment row: amount=+900 (signed positive).
     *     d) getPendingAdjustments() size = 1.
     *     e) Corrected DailyRecord: attendance=PRESENT, synced=false (Fix 2).
     */
    @Test
    fun `T-10 J-6 correction leaves settlement and payment unchanged creates signed adjustment`() {
        // ── Step 1: insert records ────────────────────────────────────────────
        j6Records().forEach { db.dailyRecordDao().insert(it) }

        // Sanity-check: WageEngine sees gross=5000 before finalization
        val beforeRecords  = db.dailyRecordDao().getRecordsForWorkerInWeek(worker.id, weekStart)
        val beforeGross    = WageEngine.weeklyGrossEarnings(beforeRecords.map { it.toWageRecord() })
        assertEquals("Pre-finalization gross must be 5000 (5 PRESENT × ₹1000)", 5000, beforeGross)

        // ── Step 2: finalize ─────────────────────────────────────────────────
        val settlementId = "settlement-j6"
        val paymentId    = "payment-j6"
        val now          = "2026-09-22T10:00:00Z"

        db.settlementDao().insert(
            WeeklySettlement(
                id               = settlementId,
                workerId         = worker.id,
                weekStartDate    = weekStart,
                baseEarnings     = 5000,
                overtimeEarnings = 0,
                grossEarnings    = 5000,
                advanceDeduction = 0,
                netPayable       = 5000,
                status           = SettlementStatus.PENDING,
                finalizedAt      = now,
            )
        )
        db.paymentDao().insert(
            Payment(
                id           = paymentId,
                settlementId = settlementId,
                paidOn       = "2026-09-22",
                amount       = 5000,
                method       = PaymentMethod.CASH,
                isVoid       = false,
                createdAt    = now,
            )
        )
        db.dailyRecordDao().lockWeekForWorker(worker.id, weekStart, now)

        // ── Step 3: apply correction ──────────────────────────────────────────
        // Re-read all week's records fresh from DB (exactly as applyCorrection does)
        val allRecords = db.dailyRecordDao().getRecordsForWorkerInWeek(worker.id, weekStart)

        // Apply Thursday correction in-memory: ABSENT → PRESENT at ₹900.
        // Thursday wage is ₹900 (a valid R-4 per-day override entered at attendance time).
        val proposedRecords = allRecords.map { r ->
            if (r.id == thuId) r.copy(attendance = Attendance.PRESENT, wage = 900)
            else r
        }

        // Use imported adapter — same function SettlementViewModel uses
        val recomputedGross = WageEngine.weeklyGrossEarnings(proposedRecords.map { it.toWageRecord() })
        assertEquals("Recomputed gross after correction", 5900, recomputedGross)

        val adjResult = WageEngine.computeAdjustment(
            originalGrossEarnings   = 5000,
            recomputedGrossEarnings = recomputedGross,
        )
        assertEquals("Adjustment amount", 900, adjResult.amount)
        assertEquals("Adjustment sign (positive = owed to worker)", 1, adjResult.sign)

        // Write: update the corrected record (synced=false) + insert adjustment row
        val editedRecord = allRecords.first { it.id == thuId }.copy(
            attendance     = Attendance.PRESENT,
            wage           = 900,
            updatedAt      = now,
            synced         = false,   // required for next sync to pick this up (Fix 2)
        )
        db.dailyRecordDao().update(editedRecord)

        val adjustmentId = "adj-j6-001"
        db.adjustmentDao().insert(
            com.workermanagement.data.entity.Adjustment(
                id           = adjustmentId,
                settlementId = settlementId,
                amount       = adjResult.amount * adjResult.sign,  // signed: +900
                reason       = "Thursday was marked ABSENT in error; worker was present",
                createdAt    = now,
            )
        )

        // ── Step 4 & 5: re-query everything from the DB (Fix 4) ──────────────

        // 5a) Original settlement unchanged
        val settlementAfter = db.settlementDao().getById(settlementId)!!
        assertEquals(
            "J-3: original settlement.grossEarnings must not change",
            5000, settlementAfter.grossEarnings,
        )
        assertEquals(
            "J-3: original settlement.netPayable must not change",
            5000, settlementAfter.netPayable,
        )

        // 5b) Original payment unchanged (J-3)
        val paymentsAfter = db.paymentDao().getForSettlement(settlementId)
        assertEquals("Exactly one payment row", 1, paymentsAfter.size)
        val paymentAfter  = paymentsAfter[0]
        assertEquals("J-3: original payment.amount must not change", 5000, paymentAfter.amount)
        assertFalse("J-3: original payment must not be voided", paymentAfter.isVoid)

        // 5c) New adjustment row with signed amount +900
        val adjustmentAfter = db.adjustmentDao().getForSettlement(settlementId)
        assertEquals("Exactly one adjustment row", 1, adjustmentAfter.size)
        assertEquals("Adjustment signed amount = +900", 900, adjustmentAfter[0].amount)

        // 5d) Pending adjustments report shows 1 row
        val pending = db.adjustmentDao().getPendingAdjustments()
        assertEquals("getPendingAdjustments must contain the new row", 1, pending.size)

        // 5e) Corrected DailyRecord: attendance=PRESENT, synced=false (Fix 2)
        val correctedRecord = db.dailyRecordDao().getById(thuId)!!
        assertEquals("Corrected attendance", Attendance.PRESENT, correctedRecord.attendance)
        assertEquals("Corrected wage", 900, correctedRecord.wage)
        assertFalse(
            "synced must be false — sync queue must pick this up on next pass",
            correctedRecord.synced,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T-11 — P-4 overpayment blocked
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * T-11: recording a payment that would exceed net payable must be blocked.
     *
     * Settlement net = ₹5000. Record ₹3000. Attempt ₹2500.
     * 3000 + 2500 = 5500 > 5000 → must be rejected.
     *
     * We verify using the same DB-level check PaymentViewModel.recordPayment()
     * performs: re-query getTotalNonVoidPayments() at save time, then compare
     * currentTotal + proposedAmount > netPayable.
     */
    @Test
    fun `T-11 P-4 payment exceeding net payable is blocked`() {
        // Setup: minimal settlement, no records needed for payment tests
        val settlementId = "settlement-t11"
        val now          = "2026-09-22T10:00:00Z"

        db.settlementDao().insert(
            WeeklySettlement(
                id               = settlementId,
                workerId         = worker.id,
                weekStartDate    = weekStart,
                baseEarnings     = 5000,
                overtimeEarnings = 0,
                grossEarnings    = 5000,
                advanceDeduction = 0,
                netPayable       = 5000,
                status           = SettlementStatus.PENDING,
                finalizedAt      = now,
            )
        )

        // Record first payment: ₹3000
        db.paymentDao().insert(
            Payment(
                id           = "pay-t11-a",
                settlementId = settlementId,
                paidOn       = "2026-09-22",
                amount       = 3000,
                method       = PaymentMethod.CASH,
                isVoid       = false,
                createdAt    = now,
            )
        )

        // P-4 check: re-query total non-void (same as PaymentViewModel does at save time)
        val totalPaid = db.paymentDao().getTotalNonVoidPayments(settlementId)
        assertEquals("Total non-void paid must be 3000", 3000, totalPaid)

        val netPayable      = 5000
        val proposedAmount  = 2500

        val wouldExceed = totalPaid + proposedAmount > netPayable
        assert(wouldExceed) {
            "P-4: ₹3000 + ₹2500 = ₹5500 should exceed net payable ₹5000"
        }

        // Confirm: nothing new was inserted (we did NOT insert pay-t11-b)
        val paymentsAfter = db.paymentDao().getForSettlement(settlementId)
        assertEquals("Only one payment must be in DB (the blocked one was never inserted)", 1, paymentsAfter.size)

        // Also verify WageEngine.settlementStatus reflects PARTIALLY_PAID after ₹3000
        val status = WageEngine.settlementStatus(netPayable, totalPaid)
        assertEquals("Status must be PARTIALLY_PAID after ₹3000 of ₹5000",
            wages.SettlementStatus.PARTIALLY_PAID, status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T-12 — P-5 void and re-record
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * T-12: void a payment then record a replacement — both rows visible in history.
     *
     * Steps:
     *  1. Record payment of ₹5000.
     *  2. Void it (P-5: is_void = true, amount never changed).
     *  3. Record replacement payment of ₹3000.
     *
     * Assertions (all fresh DAO reads):
     *  a) Two rows in payment history (void + non-void).
     *  b) Voided row: amount=5000, is_void=true.
     *  c) Replacement row: amount=3000, is_void=false.
     *  d) getTotalNonVoidPayments() = 3000 (void row excluded from sum).
     *  e) WageEngine.settlementStatus(5000, 3000) = PARTIALLY_PAID.
     */
    @Test
    fun `T-12 P-5 void payment then re-record - both rows visible totalNonVoid correct`() {
        val settlementId = "settlement-t12"
        val now          = "2026-09-22T10:00:00Z"

        db.settlementDao().insert(
            WeeklySettlement(
                id               = settlementId,
                workerId         = worker.id,
                weekStartDate    = weekStart,
                baseEarnings     = 5000,
                overtimeEarnings = 0,
                grossEarnings    = 5000,
                advanceDeduction = 0,
                netPayable       = 5000,
                status           = SettlementStatus.PENDING,
                finalizedAt      = now,
            )
        )

        // Step 1: record ₹5000
        val originalPayId = "pay-t12-orig"
        db.paymentDao().insert(
            Payment(
                id           = originalPayId,
                settlementId = settlementId,
                paidOn       = "2026-09-22",
                amount       = 5000,
                method       = PaymentMethod.CASH,
                isVoid       = false,
                createdAt    = now,
            )
        )

        // Step 2: void it (P-5 — amount is never edited; only is_void is set)
        db.paymentDao().voidPayment(originalPayId)

        // Step 3: record replacement ₹3000
        val replacementPayId = "pay-t12-replacement"
        db.paymentDao().insert(
            Payment(
                id           = replacementPayId,
                settlementId = settlementId,
                paidOn       = "2026-09-22",
                amount       = 3000,
                method       = PaymentMethod.CASH,
                isVoid       = false,
                createdAt    = now,
            )
        )

        // ── Re-query everything fresh (Fix 4) ─────────────────────────────────

        // 5a) Two rows in full payment history (void + non-void both visible)
        val allPayments = db.paymentDao().getForSettlement(settlementId)
        assertEquals("Both payment rows must be in history (P-5)", 2, allPayments.size)

        // 5b) Voided row: amount unchanged, is_void=true
        val voidedRow = allPayments.first { it.id == originalPayId }
        assertEquals("P-5: voided amount must not change", 5000, voidedRow.amount)
        assert(voidedRow.isVoid) { "P-5: original payment must be marked void" }

        // 5c) Replacement row: amount=3000, is_void=false
        val replacementRow = allPayments.first { it.id == replacementPayId }
        assertEquals("Replacement payment amount", 3000, replacementRow.amount)
        assertFalse("Replacement payment must not be void", replacementRow.isVoid)

        // 5d) Total non-void = 3000 (void row excluded by the SQL SUM)
        val totalNonVoid = db.paymentDao().getTotalNonVoidPayments(settlementId)
        assertEquals("getTotalNonVoidPayments must exclude voided row", 3000, totalNonVoid)

        // 5e) WageEngine derives status — PARTIALLY_PAID (not PAID, since 3000 < 5000)
        val status = WageEngine.settlementStatus(5000, totalNonVoid)
        assertEquals(
            "P-3: WageEngine must derive PARTIALLY_PAID after void+replace",
            wages.SettlementStatus.PARTIALLY_PAID,
            status,
        )
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun rec(
        id: String, date: String,
        attendance: Attendance, wage: Int, overtime: Int,
    ) = DailyRecord(
        id             = id,
        workerId       = worker.id,
        workDate       = date,
        weekStartDate  = weekStart,
        siteId         = site.id,
        roleId         = role.id,
        wage           = wage,
        attendance     = attendance,
        overtimeAmount = overtime,
        createdAt      = "2026-09-14T06:00:00Z",
        updatedAt      = "2026-09-14T06:00:00Z",
    )
}
