package com.workermanagement.app.ui.worker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.workermanagement.data.entity.Worker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerDetailScreen(
    vm: WorkerViewModel,
    workerId: String,
    onNavigateUp: () -> Unit,
    onEditWorker: (Worker) -> Unit,
) {
    val workers by vm.workers.collectAsState()
    val roles by vm.roles.collectAsState()
    val worker = workers.firstOrNull { it.id == workerId }

    // Load in case list isn't populated yet
    LaunchedEffect(workerId) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(worker?.name ?: "Worker") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    worker?.let {
                        IconButton(onClick = { vm.startEdit(it); onEditWorker(it) }) {
                            Icon(Icons.Default.Edit, "Edit")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (worker == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status chip
                if (!worker.isActive) {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            "INACTIVE",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Profile card
                ProfileCard {
                    LabelValue("Code", worker.code)
                    LabelValue("Name", worker.name)
                    LabelValue("Phone", worker.phone ?: "—")
                    LabelValue("Address", worker.address ?: "—")
                    val roleName = roles.firstOrNull { it.id == worker.defaultRoleId }?.name ?: worker.defaultRoleId
                    LabelValue("Default role", roleName)
                    LabelValue("Default wage", "₹${worker.defaultWage}/day")
                    LabelValue("Joined", worker.joiningDate)
                }

                // Deactivate / reactivate
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (worker.isActive) {
                        OutlinedButton(
                            onClick = { vm.toggleActive(worker); onNavigateUp() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Deactivate") }
                    } else {
                        Button(onClick = { vm.toggleActive(worker); onNavigateUp() }) {
                            Text("Reactivate")
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.startEdit(worker); onEditWorker(worker) }) {
                        Text("Edit")
                    }
                }

                // Placeholder for future tabs (daily history / advances / payments)
                Text(
                    "Daily history, advances, and payments will appear here in a future update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { content() }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
