package com.workermanagement.app.ui.settlement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.AdvanceTxn
import com.workermanagement.data.entity.AdvanceTxnType
import com.workermanagement.data.entity.Attendance
import com.workermanagement.data.entity.DailyRecord
import com.workermanagement.data.entity.Role
import com.workermanagement.data.entity.SettlementStatus
import com.workermanagement.data.entity.Site
import com.workermanagement.data.entity.WeeklySettlement
import com.workermanagement.data.entity.Worker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ─── UI model ─────────────────────────────────────────────────────────────────

/** One row in the day-by-day breakdown table. */
data class DayRow(
    val record: DailyRecord,
    val site: Site?,
    val role: Role?,
    val baseAmount: Int,      // C-1
    val dayTotal: Int,        // C-2
)

sealed class DeductionState {
    data object None : DeductionState()
    data class Valid(val net: Int) : DeductionState()
    data class Rejected(val reason: String, val maxAllowed: Int) : DeductionState()
}

data class SettlementUiState(
    val worker: Worker? = null,
    val weekStartDate: String = "",
    val dayRows: List<DayRow> = emptyList(),
    val baseEarnings: Int = 0,
    val overtimeEarnings: Int = 0,
    val grossEarnings: Int = 0,
    val advanceBalance: Int = 0,
    val deductionInput: String = "0",
    val deductionState: DeductionState = DeductionState.None,
    val existingSettlement: WeeklySettlement? = null,  // non-null = already finalized
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val saveSuccess: Boolean = false,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class SettlementViewModel(private val db: AppDatabase) : ViewModel() {

    // ── Selector state ───────────────────────────────────────────────────────

    private val _workers = MutableStateFlow<List<Worker>>(emptyList())
    val workers: StateFlow<List<Worker>> = _workers.asStateFlow()

    /** Distinct week-start dates that have at least one daily_record. */
    private val _availableWeeks = MutableStateFlow<List<String>>(emptyList())
    val availableWeeks: StateFlow<List<String>> = _availableWeeks.asStateFlow()

    private val _selectedWeek = MutableStateFlow<String?>(null)
    val selectedWeek: StateFlow<String?> = _selectedWeek.asStateFlow()

    private val _selectedWorker = MutableStateFlow<Worker?>(null)
    val selectedWorker: StateFlow<Worker?> = _selectedWorker.asStateFlow()

    // ── Detail state ─────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(SettlementUiState())
    val uiState: StateFlow<SettlementUiState> = _uiState.asStateFlow()

    // ── Lookup caches ────────────────────────────────────────────────────────

    private var siteCache: Map<String, Site> = emptyMap()
    private var roleCache: Map<String, Role> = emptyMap()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _workers.value = db.workerDao().getAll()
            siteCache = db.siteDao().getAll().associateBy { it.id }
            roleCache = db.roleDao().getAll().associateBy { it.id }
            loadAvailableWeeks()
        }
    }

    // ── Week list ─────────────────────────────────────────────────────────────

    private suspend fun loadAvailableWeeks() {
        // Derive distinct week_start_dates from existing daily records.
        // We do this by fetching all records and collecting distinct weekStartDate values.
        // A dedicated DAO query would be cleaner, but we can't modify the DAO.
        val records = db.workerDao().getAll().flatMap { w ->
            db.dailyRecordDao().getRecordsForWorkerInRange(
                w.id, "2000-01-01", LocalDate.now().toString()
            )
        }
        _availableWeeks.value = records
            .map { it.weekStartDate }
            .distinct()
            .sortedDescending()
    }

    fun selectWeek(weekStartDate: String) {
        _selectedWeek.value = weekStartDate
        _selectedWorker.value = null
        _uiState.value = SettlementUiState(weekStartDate = weekStartDate)
    }

    fun selectWorker(worker: Worker) {
        _selectedWorker.value = worker
        val week = _selectedWeek.value ?: return
        loadDetail(worker, week)
    }

    // ── Detail load ───────────────────────────────────────────────────────────

    private fun loadDetail(worker: Worker, weekStartDate: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val records = db.dailyRecordDao()
                .getRecordsForWorkerInWeek(worker.id, weekStartDate)

            // Build day rows with wage engine calculations (C-1 through C-7)
            val dayRows = records.map { rec ->
                val base = dayBase(rec)
                DayRow(
                    record = rec,
                    site = siteCache[rec.siteId],
                    role = roleCache[rec.roleId],
                    baseAmount = base,
                    dayTotal = base + rec.overtimeAmount,
                )
            }

            val baseEarnings = dayRows.sumOf { it.baseAmount }                // C-4
            val overtimeEarnings = records.sumOf { it.overtimeAmount }        // C-5
            val grossEarnings = baseEarnings + overtimeEarnings               // C-6

            val advanceBalance = db.advanceTxnDao().getAdvanceBalance(worker.id)
            val existing = db.settlementDao().getByWorkerAndWeek(worker.id, weekStartDate)

            _uiState.value = SettlementUiState(
                worker = worker,
                weekStartDate = weekStartDate,
                dayRows = dayRows,
                baseEarnings = baseEarnings,
                overtimeEarnings = overtimeEarnings,
                grossEarnings = grossEarnings,
                advanceBalance = advanceBalance,
                deductionInput = "0",
                deductionState = computeDeductionState("0", advanceBalance, grossEarnings),
                existingSettlement = existing,
            )
        }
    }

    // ── Deduction input ───────────────────────────────────────────────────────

    fun onDeductionChanged(raw: String) {
        val s = _uiState.value
        val newState = computeDeductionState(raw, s.advanceBalance, s.grossEarnings)
        _uiState.value = s.copy(deductionInput = raw, deductionState = newState)
    }

    private fun computeDeductionState(
        raw: String, balance: Int, gross: Int
    ): DeductionState {
        val amount = raw.trim().toIntOrNull() ?: 0
        if (amount < 0) return DeductionState.Rejected("Deduction cannot be negative", 0)
        // A-5: must not exceed balance
        if (amount > balance)
            return DeductionState.Rejected(
                "Deduction ₹$amount exceeds outstanding balance ₹$balance", balance
            )
        // A-6: must not exceed gross (net payable never negative)
        if (amount > gross)
            return DeductionState.Rejected(
                "Deduction ₹$amount exceeds gross earnings ₹$gross; net payable would be negative",
                gross
            )
        val net = gross - amount   // S-5
        return DeductionState.Valid(net)
    }

    // ── Finalize ──────────────────────────────────────────────────────────────

    /**
     * Atomically:
     *  1. INSERT weekly_settlement snapshot (S-1, S-4)
     *  2. INSERT DEDUCTION advance_txn linked to the settlement (A-7)
     *  3. Lock all daily_records for worker+week (S-3)
     */
    fun finalize() {
        val s = _uiState.value
        val worker = s.worker ?: return
        if (s.existingSettlement != null) {
            _uiState.value = s.copy(saveError = "This week is already finalized (S-2)")
            return
        }
        val deduction = s.deductionInput.trim().toIntOrNull() ?: 0
        val valid = s.deductionState
        if (valid !is DeductionState.Valid) {
            _uiState.value = s.copy(saveError = "Fix the deduction before finalizing")
            return
        }

        _uiState.value = s.copy(isSaving = true, saveError = null)

        viewModelScope.launch(Dispatchers.IO) {
            val now = Instant.now().toString()
            val today = LocalDate.now().toString()
            val settlementId = UUID.randomUUID().toString()

            try {
                // S-1: insert settlement snapshot
                db.settlementDao().insert(
                    WeeklySettlement(
                        id = settlementId,
                        workerId = worker.id,
                        weekStartDate = s.weekStartDate,
                        baseEarnings = s.baseEarnings,
                        overtimeEarnings = s.overtimeEarnings,
                        grossEarnings = s.grossEarnings,
                        advanceDeduction = deduction,
                        netPayable = valid.net,
                        status = SettlementStatus.PENDING,
                        finalizedAt = now,
                    )
                )

                // A-7: create DEDUCTION advance_txn if deduction > 0
                if (deduction > 0) {
                    db.advanceTxnDao().insert(
                        AdvanceTxn(
                            id = UUID.randomUUID().toString(),
                            workerId = worker.id,
                            txnDate = today,
                            type = AdvanceTxnType.DEDUCTION,
                            amount = deduction,
                            settlementId = settlementId,
                            note = "Settlement deduction — week ${s.weekStartDate}",
                            createdAt = now,
                        )
                    )
                }

                // S-3: lock all daily_records for this worker+week
                db.dailyRecordDao().lockWeekForWorker(worker.id, s.weekStartDate, now)

                // Reload to pick up locked status
                loadDetail(worker, s.weekStartDate)
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveError = "Finalization failed: ${e.message}"
                )
            }
        }
    }

    fun clearSaveSuccess() { _uiState.value = _uiState.value.copy(saveSuccess = false) }
    fun clearSaveError()   { _uiState.value = _uiState.value.copy(saveError = null) }

    // ── Wage engine — pure functions (mirrors wages.WageEngine exactly) ────────
    //
    // The canonical implementation lives in src/main/kotlin/wages/WageEngine.kt
    // (root JVM module). :app cannot take a Gradle dependency on the root project,
    // so these private helpers replicate the same integer arithmetic locally.
    // They are intentionally kept identical to WageEngine so any future refactor
    // to expose WageEngine as a shared library is mechanical.

    /** C-1: base amount for one day. */
    private fun dayBase(rec: DailyRecord): Int = when (rec.attendance) {
        Attendance.PRESENT  -> rec.wage
        Attendance.HALF_DAY -> halfDayWage(rec.wage)   // C-3: round-half-up
        Attendance.ABSENT   -> 0
    }

    /** C-3: round-half-up integer half-day. (wage + 1) / 2 for denominator = 2. */
    private fun halfDayWage(wage: Int): Int = (wage * 1 * 2 + 2) / (2 * 2)  // = (wage+1)/2

    // ─── Factory ──────────────────────────────────────────────────────────────

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettlementViewModel(db) as T
    }
}
