package com.workermanagement.app.ui.settlement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.AdvanceTxn
import com.workermanagement.data.entity.AdvanceTxnType
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
import wages.DeductionValidation
import wages.WageEngine
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import com.workermanagement.data.entity.Attendance as RoomAttendance
import wages.Attendance as WageAttendance
import wages.DailyRecord as WageDailyRecord

// ─── UI model ─────────────────────────────────────────────────────────────────

/** One row in the day-by-day breakdown table. */
data class DayRow(
    val record: DailyRecord,
    val site: Site?,
    val role: Role?,
    val baseAmount: Int,   // C-1, from WageEngine.dayTotal
    val dayTotal: Int,     // C-2, from WageEngine.dayTotal
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
    val existingSettlement: WeeklySettlement? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val saveSuccess: Boolean = false,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class SettlementViewModel(private val db: AppDatabase) : ViewModel() {

    // ── Selector state ───────────────────────────────────────────────────────

    private val _workers = MutableStateFlow<List<Worker>>(emptyList())
    val workers: StateFlow<List<Worker>> = _workers.asStateFlow()

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
            val roomRecords = db.dailyRecordDao()
                .getRecordsForWorkerInWeek(worker.id, weekStartDate)

            // Map Room entities → WageEngine plain data types.
            // This is the only conversion layer — no arithmetic lives here.
            val wageRecords = roomRecords.map { it.toWageRecord() }

            // Build UI rows using WageEngine.dayTotal for each record (C-1, C-2)
            val dayRows = roomRecords.zip(wageRecords).map { (room, wage) ->
                val result = WageEngine.dayTotal(wage)
                DayRow(
                    record     = room,
                    site       = siteCache[room.siteId],
                    role       = roleCache[room.roleId],
                    baseAmount = result.baseAmount,
                    dayTotal   = result.dayTotal,
                )
            }

            // Weekly totals — all via WageEngine (C-4, C-5, C-6)
            val baseEarnings     = WageEngine.weeklyBaseEarnings(wageRecords)
            val overtimeEarnings = WageEngine.weeklyOvertimeEarnings(wageRecords)
            val grossEarnings    = WageEngine.weeklyGrossEarnings(wageRecords)

            val advanceBalance = db.advanceTxnDao().getAdvanceBalance(worker.id)
            val existing       = db.settlementDao().getByWorkerAndWeek(worker.id, weekStartDate)

            _uiState.value = SettlementUiState(
                worker           = worker,
                weekStartDate    = weekStartDate,
                dayRows          = dayRows,
                baseEarnings     = baseEarnings,
                overtimeEarnings = overtimeEarnings,
                grossEarnings    = grossEarnings,
                advanceBalance   = advanceBalance,
                deductionInput   = "0",
                deductionState   = validateDeduction("0", advanceBalance, grossEarnings),
                existingSettlement = existing,
            )
        }
    }

    // ── Deduction input ───────────────────────────────────────────────────────

    fun onDeductionChanged(raw: String) {
        val s = _uiState.value
        _uiState.value = s.copy(
            deductionInput = raw,
            deductionState = validateDeduction(raw, s.advanceBalance, s.grossEarnings),
        )
    }

    /**
     * Maps raw string input → [DeductionState] via [WageEngine.validateDeduction].
     * The ViewModel's job here is purely input parsing and result mapping —
     * the A-5/A-6 business logic lives entirely in WageEngine.
     */
    private fun validateDeduction(raw: String, balance: Int, gross: Int): DeductionState {
        val amount = raw.trim().toIntOrNull() ?: 0
        if (amount < 0) return DeductionState.Rejected("Deduction cannot be negative", 0)
        return when (val v = WageEngine.validateDeduction(balance, gross, amount)) {
            is DeductionValidation.Ok       -> DeductionState.Valid(gross - v.deduction)
            is DeductionValidation.Rejected -> DeductionState.Rejected(v.reason, v.maxAllowed)
        }
    }

    // ── Finalize ──────────────────────────────────────────────────────────────

    /**
     * Atomically:
     *  S-1: INSERT weekly_settlement snapshot (values from WageEngine, stored as-is)
     *  A-7: INSERT DEDUCTION advance_txn linked via settlement_id
     *  S-3: SET is_locked = true on every daily_record in this worker+week
     */
    fun finalize() {
        val s      = _uiState.value
        val worker = s.worker ?: return
        if (s.existingSettlement != null) {
            _uiState.value = s.copy(saveError = "This week is already finalized (S-2)")
            return
        }
        val deduction = s.deductionInput.trim().toIntOrNull() ?: 0
        val validState = s.deductionState as? DeductionState.Valid
            ?: run { _uiState.value = s.copy(saveError = "Fix the deduction before finalizing"); return }
        val net = validState.net

        _uiState.value = s.copy(isSaving = true, saveError = null)

        viewModelScope.launch(Dispatchers.IO) {
            val now          = Instant.now().toString()
            val today        = LocalDate.now().toString()
            val settlementId = UUID.randomUUID().toString()

            try {
                // S-1: insert settlement — snapshot values are whatever WageEngine computed
                db.settlementDao().insert(
                    WeeklySettlement(
                        id               = settlementId,
                        workerId         = worker.id,
                        weekStartDate    = s.weekStartDate,
                        baseEarnings     = s.baseEarnings,
                        overtimeEarnings = s.overtimeEarnings,
                        grossEarnings    = s.grossEarnings,
                        advanceDeduction = deduction,
                        netPayable       = net,
                        status           = SettlementStatus.PENDING,
                        finalizedAt      = now,
                    )
                )

                // A-7: DEDUCTION txn (only if deduction > 0)
                if (deduction > 0) {
                    db.advanceTxnDao().insert(
                        AdvanceTxn(
                            id           = UUID.randomUUID().toString(),
                            workerId     = worker.id,
                            txnDate      = today,
                            type         = AdvanceTxnType.DEDUCTION,
                            amount       = deduction,
                            settlementId = settlementId,
                            note         = "Settlement deduction — week ${s.weekStartDate}",
                            createdAt    = now,
                        )
                    )
                }

                // S-3: lock daily records
                db.dailyRecordDao().lockWeekForWorker(worker.id, s.weekStartDate, now)

                loadDetail(worker, s.weekStartDate)
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving  = false,
                    saveError = "Finalization failed: ${e.message}",
                )
            }
        }
    }

    fun clearSaveSuccess() { _uiState.value = _uiState.value.copy(saveSuccess = false) }
    fun clearSaveError()   { _uiState.value = _uiState.value.copy(saveError = null) }

    // ─── Factory ──────────────────────────────────────────────────────────────

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettlementViewModel(db) as T
    }
}

// ─── Room → WageEngine type adapter ──────────────────────────────────────────

/**
 * Maps a Room [DailyRecord] (from :data) to a [wages.DailyRecord] (from :wages)
 * so that WageEngine's pure functions can be called without knowing about Room.
 *
 * This is the only place where the two namespaces touch. The mapping is
 * mechanical: same field names, different packages.
 */
private fun DailyRecord.toWageRecord(): WageDailyRecord = WageDailyRecord(
    wage           = wage,
    attendance     = attendance.toWageAttendance(),
    overtimeAmount = overtimeAmount,
)

private fun RoomAttendance.toWageAttendance(): WageAttendance = when (this) {
    RoomAttendance.PRESENT  -> WageAttendance.PRESENT
    RoomAttendance.HALF_DAY -> WageAttendance.HALF_DAY
    RoomAttendance.ABSENT   -> WageAttendance.ABSENT
}
