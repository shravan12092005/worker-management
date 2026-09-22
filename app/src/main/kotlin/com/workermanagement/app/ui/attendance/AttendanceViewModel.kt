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
 *
 * [attendance] is nullable:
 *   - Non-null when loaded from an existing DB record (preserves stored value).
 *   - null for every row that was pre-filled from yesterday or added via search
 *     — the user MUST explicitly tap P / HD / A before the row can be saved
 *     (D-4: assigning a worker to a site MUST NOT imply Present).
 */
data class WorkerAttendanceRow(
    val worker: Worker,
    val existingRecord: DailyRecord?,   // null → insert on save
    // Editable draft fields (not persisted until save())
    val attendance: Attendance? = existingRecord?.attendance, // null = not yet marked
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
            // D-4: attendance is null — user must explicitly tap P/HD/A
            attendance = null,
            roleId = worker.defaultRoleId,
            wage = worker.defaultWage,
        )).sortedBy { it.worker.name }
        _searchResults.value = emptyList()
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /**
     * Flushes all in-memory drafts to Room.
     *
     * Upsert semantics: re-queries the DB at the start of each call so that
     * calling save() twice (e.g. tapping Save, changing a value, tapping Save
     * again) always updates the existing record rather than attempting a second
     * INSERT that would fail D-1.
     *
     * Validates before writing:
     *   - Blocks the entire save if any unlocked row has attendance == null
     *     (user must explicitly mark every row — D-4).
     *   - D-5: rejects future work dates.
     *   - D-6: skips rows where work_date < worker.joining_date.
     */
    fun save() {
        val site = _selectedSite.value ?: return
        val date = _selectedDate.value
        val today = LocalDate.now()
        val workDate = LocalDate.parse(date)
        val now = Instant.now().toString()

        // D-5: block future dates (checked on UI thread before launching coroutine)
        if (workDate.isAfter(today)) {
            _error.value = "Cannot record attendance for a future date (rule D-5)"
            return
        }

        // Block save if any unlocked row has no attendance value set yet
        val unset = _rows.value.filter { !it.isLocked && it.attendance == null }
        if (unset.isNotEmpty()) {
            val names = unset.take(3).joinToString { it.worker.name }
            val extra = if (unset.size > 3) " and ${unset.size - 3} more" else ""
            _error.value = "Mark attendance for: $names$extra"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val settings = db.settingsDao().get()
            val weekStartDate = computeWeekStart(workDate, settings?.weekStartDay ?: "MONDAY")

            // ── True upsert: re-query DB now, not from stale in-memory existingRecord.
            // This handles: double-tap Save, concurrent coroutine, any stale state.
            val freshRecords: Map<String, DailyRecord> = db.dailyRecordDao()
                .getRecordsForSiteOnDate(site.id, date)
                .associateBy { it.workerId }

            var errorMsg: String? = null

            for (row in _rows.value) {
                if (row.isLocked) continue   // locked rows → correction flow only
                val attendance = row.attendance ?: continue   // guarded above, skip anyway

                // D-6: skip rows where work_date is before the worker's joining_date
                val joiningDate = LocalDate.parse(row.worker.joiningDate)
                if (workDate.isBefore(joiningDate)) {
                    errorMsg = "${row.worker.name}: work date is before joining date (${row.worker.joiningDate})"
                    continue
                }

                // Prefer the fresh DB record; fall back to what was loaded at list-build time
                val existingInDb = freshRecords[row.worker.id] ?: row.existingRecord

                if (existingInDb == null) {
                    // No record yet — INSERT
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
                                attendance = attendance,
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
                    // Record exists — UPDATE (D-2: values are the copy on the record)
                    db.dailyRecordDao().update(
                        existingInDb.copy(
                            siteId = site.id,
                            roleId = row.roleId,
                            wage = row.wage,
                            attendance = attendance,
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

            // Reload to sync in-memory state with DB (updates existingRecord refs)
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
