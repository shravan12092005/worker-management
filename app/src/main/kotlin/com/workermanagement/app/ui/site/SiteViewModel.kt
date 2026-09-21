package com.workermanagement.app.ui.site

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class SiteViewModel(private val db: AppDatabase) : ViewModel() {

    private val _sites = MutableStateFlow<List<Site>>(emptyList())
    val sites: StateFlow<List<Site>> = _sites.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _sites.value = db.siteDao().getAll()
        }
    }

    fun addSite(name: String, location: String?) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) { _error.value = "Name cannot be empty"; return }
        if (_sites.value.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            _error.value = "A site named \"$trimmedName\" already exists"; return
        }
        val now = Instant.now().toString()
        viewModelScope.launch(Dispatchers.IO) {
            db.siteDao().insert(
                Site(
                    id = UUID.randomUUID().toString(),
                    name = trimmedName,
                    location = location?.trim()?.ifBlank { null },
                    createdAt = now,
                    updatedAt = now
                )
            )
            load()
        }
    }

    fun updateSite(site: Site, newName: String, newLocation: String?) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) { _error.value = "Name cannot be empty"; return }
        if (_sites.value.any { it.id != site.id && it.name.equals(trimmedName, ignoreCase = true) }) {
            _error.value = "A site named \"$trimmedName\" already exists"; return
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.siteDao().update(
                site.copy(
                    name = trimmedName,
                    location = newLocation?.trim()?.ifBlank { null },
                    updatedAt = Instant.now().toString(),
                    synced = false
                )
            )
            load()
        }
    }

    /** Deactivate (never delete — rule M-3). */
    fun toggleActive(site: Site) {
        viewModelScope.launch(Dispatchers.IO) {
            db.siteDao().update(
                site.copy(isActive = !site.isActive, updatedAt = Instant.now().toString(), synced = false)
            )
            load()
        }
    }

    fun clearError() { _error.value = null }

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SiteViewModel(db) as T
    }
}
