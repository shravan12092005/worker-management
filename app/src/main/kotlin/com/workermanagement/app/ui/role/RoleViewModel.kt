package com.workermanagement.app.ui.role

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class RoleViewModel(private val db: AppDatabase) : ViewModel() {

    private val _roles = MutableStateFlow<List<Role>>(emptyList())
    val roles: StateFlow<List<Role>> = _roles.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _roles.value = db.roleDao().getAll()
        }
    }

    /** Add a new role. Returns false if name is blank or already exists. */
    fun addRole(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _error.value = "Name cannot be empty"
            return false
        }
        if (_roles.value.any { it.name.equals(trimmed, ignoreCase = true) }) {
            _error.value = "A role named \"$trimmed\" already exists"
            return false
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.roleDao().insert(Role(id = UUID.randomUUID().toString(), name = trimmed))
            load()
        }
        return true
    }

    /** Rename an existing role. */
    fun renameRole(role: Role, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            _error.value = "Name cannot be empty"
            return false
        }
        if (_roles.value.any { it.id != role.id && it.name.equals(trimmed, ignoreCase = true) }) {
            _error.value = "A role named \"$trimmed\" already exists"
            return false
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.roleDao().update(role.copy(name = trimmed, synced = false))
            load()
        }
        return true
    }

    /** Toggle active/inactive. An inactive role is hidden from new-worker dropdowns. */
    fun toggleActive(role: Role) {
        viewModelScope.launch(Dispatchers.IO) {
            db.roleDao().update(role.copy(isActive = !role.isActive, synced = false))
            load()
        }
    }

    fun clearError() { _error.value = null }

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoleViewModel(db) as T
    }
}
