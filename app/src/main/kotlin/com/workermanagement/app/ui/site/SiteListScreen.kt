package com.workermanagement.app.ui.site

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import com.workermanagement.data.entity.Site
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteListScreen(vm: SiteViewModel) {
    val sites by vm.sites.collectAsState()
    val error by vm.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDialog by remember { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<Site?>(null) }
    var dialogName by remember { mutableStateOf("") }
    var dialogLocation by remember { mutableStateOf("") }

    LaunchedEffect(error) {
        error?.let { scope.launch { snackbar.showSnackbar(it) }; vm.clearError() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sites") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingSite = null; dialogName = ""; dialogLocation = ""; showDialog = true
            }) { Icon(Icons.Default.Add, "Add site") }
        }
    ) { padding ->
        if (sites.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No sites yet. Tap + to add one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(sites, key = { it.id }) { site ->
                    SiteRow(
                        site = site,
                        onEdit = {
                            editingSite = site
                            dialogName = site.name
                            dialogLocation = site.location ?: ""
                            showDialog = true
                        },
                        onToggle = { vm.toggleActive(site) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showDialog) {
        SiteDialog(
            title = if (editingSite == null) "New Site" else "Edit Site",
            name = dialogName,
            location = dialogLocation,
            onNameChange = { dialogName = it },
            onLocationChange = { dialogLocation = it },
            onConfirm = {
                val site = editingSite
                if (site == null) {
                    vm.addSite(dialogName, dialogLocation)
                } else {
                    vm.updateSite(site, dialogName, dialogLocation)
                }
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun SiteRow(site: Site, onEdit: () -> Unit, onToggle: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(site.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (!site.location.isNullOrBlank()) {
                    val loc = site.location
                    Text(loc ?: "", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!site.isActive)
                    Text("Inactive", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
            }
            Switch(checked = site.isActive, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun SiteDialog(
    title: String,
    name: String, location: String,
    onNameChange: (String) -> Unit, onLocationChange: (String) -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = onNameChange,
                    label = { Text("Site name *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = onLocationChange,
                    label = { Text("Location (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
