package com.workermanagement.app.ui.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.Role
import com.workermanagement.data.entity.Worker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class WorkerFilter(
    val showInactive: Boolean = false,
    val onlyWithAdvance: Boolean = false,
)

data class DuplicateWarning(
    val nameSimilar: List<Worker> = emptyList(),
    val phoneExact: Worker? = null,
)

class WorkerViewModel(private val db: AppDatabase) : ViewModel() {

    private val _workers = MutableStateFlow<List<Worker>>(emptyList())
    val workers: StateFlow<List<Worker>> = _workers.asStateFlow()

    private val _roles = MutableStateFlow<List<Role>>(emptyList())
    val roles: StateFlow<List<Role>> = _roles.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(WorkerFilter())
    val filter: StateFlow<WorkerFilter> = _filter.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Non-null while the add/edit form is open. Null = closed. */
    private val _editingWorker = MutableStateFlow<Worker?>(null)
    val editingWorker: StateFlow<Worker?> = _editingWorker.asStateFlow()

    /** Duplicate warning to show before saving a new worker. */
    private val _duplicateWarning = MutableStateFlow<DuplicateWarning?>(null)
    val duplicateWarning: StateFlow<DuplicateWarning?> = _duplicateWarning.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _roles.value = db.roleDao().getActive()
            refreshWorkerList()
        }
    }

    fun search(q: String) {
        _query.value = q
        viewModelScope.launch(Dispatchers.IO) { refreshWorkerList() }
    }

    fun setFilter(f: WorkerFilter) {
        _filter.value = f
        viewModelScope.launch(Dispatchers.IO) { refreshWorkerList() }
    }

    private suspend fun refreshWorkerList() {
        val q = _query.value.trim()
        val f = _filter.value
        val all: List<Worker> = if (q.isBlank()) {
            if (f.onlyWithAdvance) db.workerDao().getWorkersWithOutstandingAdvance()
            else db.workerDao().getAll()
        } else {
            db.workerDao().searchByNameOrCodeOrPhone(q)
        }
        _workers.value = all.filter { w -> if (!f.showInactive) w.isActive else true }
    }

    // ─── Add ────────────────────────────────────────────────────────────

    /**
     * Check for duplicates (rule M-1) before saving a new worker.
     * If duplicates exist, stores a warning for the UI to show a confirmation dialog.
     * If no duplicates, saves immediately.
     */
    fun checkAndSaveNewWorker(
        code: String, name: String, phone: String?,
        address: String?, roleId: String, wage: Int, joiningDate: String,
        forceOverride: Boolean = false,
    ) {
        val trimCode = code.trim()
        val trimName = name.trim()
        if (trimCode.isBlank()) { _error.value = "Worker code is required"; return }
        if (trimName.isBlank()) { _error.value = "Name is required"; return }
        if (wage <= 0) { _error.value = "Wage must be greater than 0"; return }

        viewModelScope.launch(Dispatchers.IO) {
            val allWorkers = db.workerDao().getAll()

            if (!forceOverride) {
                // Rule M-1: warn on name similarity or exact phone match
                val nameSimilar = allWorkers.filter { w ->
                    w.name.contains(trimName, ignoreCase = true) ||
                    trimName.contains(w.name, ignoreCase = true)
                }
                val trimPhone = phone?.trim()
                val phoneExact = trimPhone?.let { p ->
                    allWorkers.firstOrNull { it.phone?.trim() == p }
                }
                if (nameSimilar.isNotEmpty() || phoneExact != null) {
                    _duplicateWarning.value = DuplicateWarning(nameSimilar, phoneExact)
                    return@launch
                }
            }

            _duplicateWarning.value = null
            val now = Instant.now().toString()
            db.workerDao().insert(
                Worker(
                    id = UUID.randomUUID().toString(),
                    code = trimCode,
                    name = trimName,
                    phone = phone?.trim()?.ifBlank { null },
                    address = address?.trim()?.ifBlank { null },
                    defaultRoleId = roleId,
                    defaultWage = wage,
                    joiningDate = joiningDate.ifBlank { LocalDate.now().toString() },
                    createdAt = now,
                    updatedAt = now,
                )
            )
            refreshWorkerList()
            _editingWorker.value = null
        }
    }

    // ─── Edit ───────────────────────────────────────────────────────────

    fun startEdit(worker: Worker) { _editingWorker.value = worker }
    fun cancelEdit() { _editingWorker.value = null; _duplicateWarning.value = null }

    fun saveEdit(
        worker: Worker, name: String, phone: String?,
        address: String?, roleId: String, wage: Int,
    ) {
        val trimName = name.trim()
        if (trimName.isBlank()) { _error.value = "Name is required"; return }
        if (wage <= 0) { _error.value = "Wage must be greater than 0"; return }
        viewModelScope.launch(Dispatchers.IO) {
            db.workerDao().update(
                worker.copy(
                    name = trimName,
                    phone = phone?.trim()?.ifBlank { null },
                    address = address?.trim()?.ifBlank { null },
                    defaultRoleId = roleId,
                    defaultWage = wage,
                    updatedAt = Instant.now().toString(),
                    synced = false,
                )
            )
            refreshWorkerList()
            _editingWorker.value = null
        }
    }

    // ─── Deactivate ─────────────────────────────────────────────────────

    /** Deactivate (never delete — rule M-3). */
    fun toggleActive(worker: Worker) {
        viewModelScope.launch(Dispatchers.IO) {
            db.workerDao().update(
                worker.copy(isActive = !worker.isActive, updatedAt = Instant.now().toString(), synced = false)
            )
            refreshWorkerList()
        }
    }

    fun dismissDuplicateWarning() { _duplicateWarning.value = null }
    fun clearError() { _error.value = null }

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkerViewModel(db) as T
    }
}
