package com.workermanagement.app.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.Attendance
import com.workermanagement.data.entity.DailyRecord
import com.workermanagement.data.entity.Role
import com.workermanagement.data.entity.Site
import com.workermanagement.data.entity.Worker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ─── UI model ────────────────────────────────────────────────────────────────

/**
 * One row in the attendance list.
 * [existingRecord] is null when the worker has been added from search but
 * no daily_record exists yet. On save, a new record is inserted.
 */
data class WorkerAttendanceRow(
    val worker: Worker,
    val existingRecord: DailyRecord?,   // null → new record on save
    // Editable draft fields (not persisted until save())
    val attendance: Attendance = existingRecord?.attendance ?: Attendance.PRESENT,
    val roleId: String = existingRecord?.roleId ?: worker.defaultRoleId,
    val wage: Int = existingRecord?.wage ?: worker.defaultWage,
    val overtimeAmount: Int = existingRecord?.overtimeAmount ?: 0,
    val note: String = existingRecord?.note ?: "",
    val expanded: Boolean = false,
    val isLocked: Boolean = existingRecord?.isLocked ?: false,
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

class AttendanceViewModel(private val db: AppDatabase) : ViewModel() {

    // ── Selection state ──────────────────────────────────────────────────────

    private val _sites = MutableStateFlow<List<Site>>(emptyList())
    val sites: StateFlow<List<Site>> = _sites.asStateFlow()

    private val _selectedSite = MutableStateFlow<Site?>(null)
    val selectedSite: StateFlow<Site?> = _selectedSite.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now().toString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // ── List state ───────────────────────────────────────────────────────────

    private val _rows = MutableStateFlow<List<WorkerAttendanceRow>>(emptyList())
    val rows: StateFlow<List<WorkerAttendanceRow>> = _rows.asStateFlow()

    private val _roles = MutableStateFlow<List<Role>>(emptyList())
    val roles: StateFlow<List<Role>> = _roles.asStateFlow()

    // ── Search-to-add state ──────────────────────────────────────────────────

    private val _searchResults = MutableStateFlow<List<Worker>>(emptyList())
    val searchResults: StateFlow<List<Worker>> = _searchResults.asStateFlow()

    // ── Feedback ─────────────────────────────────────────────────────────────

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    // ── Init ─────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _sites.value = db.siteDao().getActive()
            _roles.value = db.roleDao().getActive()
        }
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    fun selectSite(site: Site) {
        _selectedSite.value = site
        loadRows()
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
        loadRows()
    }

    // ── Load rows ─────────────────────────────────────────────────────────────

    /**
     * Builds the attendance list for the current site + date:
     *  1. Existing daily_records for site+date (already saved rows).
     *  2. Yesterday's assignments at the same site that don't yet have a
     *     record today (pre-fill per §6.1).
     * Workers in (2) are added as unsaved drafts with no record yet.
     */
    fun loadRows() {
        val site = _selectedSite.value ?: return
        val date = _selectedDate.value
        val yesterday = LocalDate.parse(date).minusDays(1).toString()

        viewModelScope.launch(Dispatchers.IO) {
            val allWorkers = db.workerDao().getAll().associateBy { it.id }

            // Existing records for today at this site
            val todayRecords = db.dailyRecordDao()
                .getRecordsForSiteOnDate(site.id, date)

            val todayWorkerIds = todayRecords.map { it.workerId }.toSet()

            // Yesterday's assignments — used to pre-fill workers not yet recorded
            val yesterdayRecords = db.dailyRecordDao()
                .getYesterdayAssignmentForSite(site.id, yesterday)

            val newRows = mutableListOf<WorkerAttendanceRow>()

            // Rows for workers already recorded today
            for (rec in todayRecords) {
                val worker = allWorkers[rec.workerId] ?: continue
                newRows += WorkerAttendanceRow(worker = worker, existingRecord = rec)
            }

            // Pre-fill from yesterday — only workers not already in today's list
            for (rec in yesterdayRecords) {
                if (rec.workerId in todayWorkerIds) continue
                val worker = allWorkers[rec.workerId] ?: continue
                // D-7: only add if worker is still active
                if (!worker.isActive) continue
                newRows += WorkerAttendanceRow(
                    worker = worker,
                    existingRecord = null,
                    // D-4: assignment ≠ present; default to PRESENT as a helpful
                    // pre-fill (user can change), but attendance is independent
                    attendance = Attendance.PRESENT,
                    roleId = worker.defaultRoleId,
                    wage = worker.defaultWage,
                )
            }

            // Sort by worker name for stable display
            _rows.value = newRows.sortedBy { it.worker.name }
        }
    }

    // ── Row mutations (all in-memory, not saved until save()) ─────────────────

    fun setAttendance(workerId: String, attendance: Attendance) {
        _rows.value = _rows.value.map { row ->
            if (row.worker.id == workerId) row.copy(attendance = attendance) else row
        }
    }

    fun markAllPresent() {
        _rows.value = _rows.value.map { row ->
            if (!row.isLocked) row.copy(attendance = Attendance.PRESENT) else row
        }
    }

    fun toggleExpanded(workerId: String) {
        _rows.value = _rows.value.map { row ->
            if (row.worker.id == workerId) row.copy(expanded = !row.expanded) else row
        }
    }

    fun setRole(workerId: String, roleId: String) {
        _rows.value = _rows.value.map { row ->
            if (row.worker.id == workerId) row.copy(roleId = roleId) else row
        }
        // R-7: changing role MUST NOT auto-change wage
    }

    fun setWage(workerId: String, wage: Int) {
        _rows.value = _rows.value.map { row ->
            if (row.worker.id == workerId) row.copy(wage = wage) else row
        }
    }

    fun setOvertime(workerId: String, amount: Int) {
        _rows.value = _rows.value.map { row ->
            if (row.worker.id == workerId) row.copy(overtimeAmount = amount) else row
        }
    }

    fun setNote(workerId: String, note: String) {
        _rows.value = _rows.value.map { row ->
            if (row.worker.id == workerId) row.copy(note = note) else row
        }
    }

    // ── Add worker from search ─────────────────────────────────────────────────

    fun searchWorkers(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        viewModelScope.launch(Dispatchers.IO) {
            val currentIds = _rows.value.map { it.worker.id }.toSet()
            _searchResults.value = db.workerDao()
                .searchByNameOrCodeOrPhone(query)
                .filter { it.isActive && it.id !in currentIds }
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }

    /** Add a worker to the list as an unsaved draft (no DB write yet). */
    fun addWorkerToList(worker: Worker) {
        if (_rows.value.any { it.worker.id == worker.id }) return
        _rows.value = (_rows.value + WorkerAttendanceRow(
            worker = worker,
            existingRecord = null,
            attendance = Attendance.PRESENT,
            roleId = worker.defaultRoleId,
            wage = worker.defaultWage,
        )).sortedBy { it.worker.name }
        _searchResults.value = emptyList()
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /**
     * Flushes all in-memory drafts to Room.
     * Validates D-5 and D-6 per row before touching the DB.
     * Skips locked rows (they must go through the correction flow).
     */
    fun save() {
        val site = _selectedSite.value ?: return
        val date = _selectedDate.value
        val today = LocalDate.now()
        val workDate = LocalDate.parse(date)
        val now = Instant.now().toString()

        // D-5: block future dates
        if (workDate.isAfter(today)) {
            _error.value = "Cannot record attendance for a future date (rule D-5)"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val settings = db.settingsDao().get()
            val weekStartDate = computeWeekStart(workDate, settings?.weekStartDay ?: "MONDAY")

            var errorMsg: String? = null

            for (row in _rows.value) {
                if (row.isLocked) continue   // locked rows are read-only

                // D-6: block dates before joining_date
                val joiningDate = LocalDate.parse(row.worker.joiningDate)
                if (workDate.isBefore(joiningDate)) {
                    errorMsg = "${row.worker.name}: work date is before joining date (${row.worker.joiningDate})"
                    continue
                }

                val existing = row.existingRecord
                if (existing == null) {
                    // New record — INSERT
                    try {
                        db.dailyRecordDao().insert(
                            DailyRecord(
                                id = UUID.randomUUID().toString(),
                                workerId = row.worker.id,
                                workDate = date,
                                weekStartDate = weekStartDate,
                                siteId = site.id,
                                roleId = row.roleId,
                                wage = row.wage,
                                attendance = row.attendance,
                                overtimeAmount = row.overtimeAmount,
                                note = row.note.ifBlank { null },
                                createdAt = now,
                                updatedAt = now,
                            )
                        )
                    } catch (e: Exception) {
                        errorMsg = "Save failed for ${row.worker.name}: ${e.message}"
                    }
                } else {
                    // Existing record — UPDATE (D-2: store copy of values)
                    db.dailyRecordDao().update(
                        existing.copy(
                            siteId = site.id,
                            roleId = row.roleId,
                            wage = row.wage,
                            attendance = row.attendance,
                            overtimeAmount = row.overtimeAmount,
                            note = row.note.ifBlank { null },
                            updatedAt = now,
                            synced = false,
                        )
                    )
                }
            }

            if (errorMsg != null) {
                _error.value = errorMsg
            } else {
                _saveSuccess.value = true
            }

            // Reload to sync in-memory state with DB
            loadRows()
        }
    }

    fun clearError() { _error.value = null }
    fun clearSaveSuccess() { _saveSuccess.value = false }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Computes the ISO date of the week-start that contains [date],
     * given the configured [weekStartDayName] (e.g. "MONDAY").
     * Rule W-1: week boundaries are defined by the settings table.
     */
    private fun computeWeekStart(date: LocalDate, weekStartDayName: String): String {
        val startDay = try {
            java.time.DayOfWeek.valueOf(weekStartDayName.uppercase())
        } catch (_: Exception) {
            java.time.DayOfWeek.MONDAY
        }
        var d = date
        while (d.dayOfWeek != startDay) d = d.minusDays(1)
        return d.toString()
    }

    // ─── Factory ─────────────────────────────────────────────────────────────

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AttendanceViewModel(db) as T
    }
}
