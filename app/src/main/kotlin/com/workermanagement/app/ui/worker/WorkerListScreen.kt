package com.workermanagement.app.ui.worker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.workermanagement.data.entity.Worker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerListScreen(
    vm: WorkerViewModel,
    onAddWorker: () -> Unit,
    onWorkerClick: (Worker) -> Unit,
) {
    val workers by vm.workers.collectAsState()
    val filter by vm.filter.collectAsState()
    val query by vm.query.collectAsState()
    val error by vm.error.collectAsState()
    val duplicateWarning by vm.duplicateWarning.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searchActive by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let { scope.launch { snackbar.showSnackbar(it) }; vm.clearError() }
    }

    // Duplicate warning dialog — shown when M-1 check fires
    duplicateWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = { vm.dismissDuplicateWarning() },
            title = { Text("Possible duplicate") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (warning.phoneExact != null)
                        Text("A worker with this phone number already exists: ${warning.phoneExact.name}")
                    if (warning.nameSimilar.isNotEmpty())
                        Text("Similar name(s) found: ${warning.nameSimilar.joinToString { it.name }}")
                    Text("Save anyway?")
                }
            },
            confirmButton = { TextButton(onClick = { vm.dismissDuplicateWarning(); onAddWorker() }) {
                Text("Yes, save")
            }},
            dismissButton = { TextButton(onClick = { vm.dismissDuplicateWarning() }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workers") },
                actions = {
                    IconButton(onClick = { searchActive = !searchActive }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(Icons.Default.FilterList, "Filter")
                        }
                        DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Checkbox(checked = filter.showInactive,
                                            onCheckedChange = null)
                                        Text("Show inactive")
                                    }
                                },
                                onClick = { vm.setFilter(filter.copy(showInactive = !filter.showInactive)) }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Checkbox(checked = filter.onlyWithAdvance,
                                            onCheckedChange = null)
                                        Text("Has outstanding advance")
                                    }
                                },
                                onClick = { vm.setFilter(filter.copy(onlyWithAdvance = !filter.onlyWithAdvance)) }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddWorker) {
                Icon(Icons.Default.Add, "Add worker")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchActive) {
                SearchBar(
                    query = query,
                    onQueryChange = { vm.search(it) },
                    onSearch = { vm.search(it) },
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text("Search name, code, phone…") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {}
            }

            if (workers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isNotBlank()) "No workers match \"$query\""
                        else "No workers. Tap + to add one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(workers, key = { it.id }) { worker ->
                        WorkerRow(worker = worker, onClick = { onWorkerClick(worker) })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun WorkerRow(worker: Worker, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (worker.isActive)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(worker.name, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium)
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(worker.code, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                Text("₹${worker.defaultWage}/day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!worker.isActive)
                    Text("Inactive", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
