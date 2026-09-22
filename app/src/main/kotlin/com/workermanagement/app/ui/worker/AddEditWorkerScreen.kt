package com.workermanagement.app.ui.worker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.workermanagement.data.entity.Worker
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Add-new worker screen.
 * [existingWorker] is non-null when editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditWorkerScreen(
    vm: WorkerViewModel,
    existingWorker: Worker? = null,
    onNavigateUp: () -> Unit,
) {
    val roles by vm.roles.collectAsState()
    val error by vm.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isEdit = existingWorker != null

    // Form state — pre-fill when editing
    var code by remember { mutableStateOf(existingWorker?.code ?: "") }
    var name by remember { mutableStateOf(existingWorker?.name ?: "") }
    var phone by remember { mutableStateOf(existingWorker?.phone ?: "") }
    var address by remember { mutableStateOf(existingWorker?.address ?: "") }
    var wage by remember { mutableStateOf(existingWorker?.defaultWage?.toString() ?: "") }
    var joiningDate by remember {
        mutableStateOf(existingWorker?.joiningDate ?: LocalDate.now().toString())
    }
    var selectedRoleId by remember {
        mutableStateOf(existingWorker?.defaultRoleId ?: roles.firstOrNull()?.id ?: "")
    }
    var roleMenuExpanded by remember { mutableStateOf(false) }

    // Keep selectedRoleId valid when roles load async
    LaunchedEffect(roles) {
        if (selectedRoleId.isBlank() && roles.isNotEmpty())
            selectedRoleId = roles.first().id
    }

    LaunchedEffect(error) {
        error?.let { scope.launch { snackbar.showSnackbar(it) }; vm.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit Worker" else "New Worker") },
                navigationIcon = {
                    IconButton(onClick = { vm.cancelEdit(); onNavigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Code — immutable once set (rule M-5), show as read-only when editing
            OutlinedTextField(
                value = code,
                onValueChange = { if (!isEdit) code = it },
                label = { Text("Worker code *") },
                readOnly = isEdit,
                singleLine = true,
                supportingText = if (isEdit) {{ Text("Code cannot be changed after creation") }} else null,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address (optional)") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            // Default role dropdown
            val selectedRoleName = roles.firstOrNull { it.id == selectedRoleId }?.name ?: "Select role"
            ExposedDropdownMenuBox(
                expanded = roleMenuExpanded,
                onExpandedChange = { roleMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedRoleName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Default role *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roleMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = roleMenuExpanded,
                    onDismissRequest = { roleMenuExpanded = false }) {
                    roles.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role.name) },
                            onClick = { selectedRoleId = role.id; roleMenuExpanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = wage,
                onValueChange = { wage = it },
                label = { Text("Default wage (₹/day) *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = joiningDate,
                onValueChange = { joiningDate = it },
                label = { Text("Joining date (yyyy-MM-dd)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (roles.isEmpty()) {
                Text(
                    "⚠ No active roles found. Add a role first.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    enabled = roles.isNotEmpty(),
                    onClick = {
                        val wageInt = wage.trim().toIntOrNull() ?: 0
                        if (isEdit && existingWorker != null) {
                            vm.saveEdit(existingWorker, name, phone, address, selectedRoleId, wageInt)
                            onNavigateUp()
                        } else {
                            vm.checkAndSaveNewWorker(
                                code, name, phone.ifBlank { null },
                                address.ifBlank { null }, selectedRoleId, wageInt, joiningDate
                            )
                            // Navigation happens via duplicateWarning dialog or direct save
                        }
                    }
                ) { Text(if (isEdit) "Save changes" else "Add worker") }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
