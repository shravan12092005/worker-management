package com.workermanagement.app.ui.attendance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workermanagement.data.entity.Attendance
import com.workermanagement.data.entity.Role
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ─── Attendance chip colors ───────────────────────────────────────────────────

private val PresentColor   = Color(0xFF4CAF50)   // green
private val HalfDayColor   = Color(0xFFFFB300)   // amber
private val AbsentColor    = Color(0xFFF44336)    // red
private val UnselectedBg   = Color(0xFF252840)    // surface variant

// ─── Main Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(vm: AttendanceViewModel) {
    val sites        by vm.sites.collectAsState()
    val selectedSite by vm.selectedSite.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    val rows         by vm.rows.collectAsState()
    val roles        by vm.roles.collectAsState()
    val error        by vm.error.collectAsState()
    val saveSuccess  by vm.saveSuccess.collectAsState()
    val searchResults by vm.searchResults.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDatePicker    by remember { mutableStateOf(false) }
    var showSitePicker    by remember { mutableStateOf(false) }
    var showAddWorker     by remember { mutableStateOf(false) }
    var workerSearchQuery by remember { mutableStateOf("") }

    // Snackbar for errors
    LaunchedEffect(error) {
        error?.let { scope.launch { snackbar.showSnackbar(it) }; vm.clearError() }
    }
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            scope.launch { snackbar.showSnackbar("✓ Attendance saved") }
            vm.clearSaveSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (selectedSite != null) {
                ExtendedFloatingActionButton(
                    onClick = { vm.save() },
                    icon = { Icon(Icons.Default.Save, null) },
                    text = { Text("Save") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor  = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Gradient header ──────────────────────────────────────────────
            AttendanceHeader(
                site = selectedSite?.name ?: "Select a site",
                date = selectedDate,
                workerCount = rows.size,
                onSiteClick = { showSitePicker = true },
                onDateClick = { showDatePicker = true },
            )

            if (selectedSite == null) {
                // Empty state — site not yet selected
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PersonSearch, null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Select a site to start marking attendance",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { showSitePicker = true }) {
                            Text("Choose site")
                        }
                    }
                }
            } else {
                // ── Mark-all bar ─────────────────────────────────────────────
                MarkAllBar(
                    workerCount = rows.size,
                    unsetCount = rows.count { !it.isLocked && it.attendance == null },
                    onMarkAllPresent = { vm.markAllPresent() },
                    onAddWorker = { showAddWorker = true },
                )

                // ── Worker list ───────────────────────────────────────────────
                if (rows.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No workers assigned here yesterday.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { showAddWorker = true }) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Add worker")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp, top = 4.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(rows, key = { it.worker.id }) { row ->
                            WorkerAttendanceCard(
                                row = row,
                                roles = roles,
                                onAttendanceTap = { vm.setAttendance(row.worker.id, it) },
                                onToggleExpand  = { vm.toggleExpanded(row.worker.id) },
                                onRoleChange    = { vm.setRole(row.worker.id, it) },
                                onWageChange    = { vm.setWage(row.worker.id, it) },
                                onOtChange      = { vm.setOvertime(row.worker.id, it) },
                                onNoteChange    = { vm.setNote(row.worker.id, it) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Site picker dialog ───────────────────────────────────────────────────
    if (showSitePicker) {
        AlertDialog(
            onDismissRequest = { showSitePicker = false },
            title = { Text("Select site") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (sites.isEmpty()) {
                        Text("No active sites. Add a site first.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    sites.forEach { site ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { vm.selectSite(site); showSitePicker = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(site.name, style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium)
                                if (!site.location.isNullOrBlank()) {
                                    val loc = site.location
                                    Text(loc ?: "", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (selectedSite?.id == site.id) {
                                Icon(Icons.Default.Check, null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSitePicker = false }) { Text("Cancel") } }
        )
    }

    // ── Date picker ───────────────────────────────────────────────────────────
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(selectedDate)
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC")).toLocalDate().toString()
                        vm.selectDate(picked)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState, title = { Text("Select date", Modifier.padding(16.dp)) })
        }
    }

    // ── Add worker dialog ─────────────────────────────────────────────────────
    if (showAddWorker) {
        AlertDialog(
            onDismissRequest = { showAddWorker = false; vm.clearSearch(); workerSearchQuery = "" },
            title = { Text("Add worker to list") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = workerSearchQuery,
                        onValueChange = { workerSearchQuery = it; vm.searchWorkers(it) },
                        label = { Text("Search by name, code, phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (searchResults.isEmpty() && workerSearchQuery.isNotBlank()) {
                        Text("No active workers found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    searchResults.forEach { worker ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    vm.addWorkerToList(worker)
                                    showAddWorker = false
                                    vm.clearSearch()
                                    workerSearchQuery = ""
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(worker.name, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium)
                                Text("${worker.code}  ·  ₹${worker.defaultWage}/day",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Add, null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddWorker = false; vm.clearSearch(); workerSearchQuery = "" }) {
                    Text("Close")
                }
            }
        )
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun AttendanceHeader(
    site: String, date: String, workerCount: Int,
    onSiteClick: () -> Unit, onDateClick: () -> Unit,
) {
    val displayDate = try {
        LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (_: Exception) { date }

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A2B6D), Color(0xFF0F111A))
                )
            )
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Attendance",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
            )
            // Site name — tappable
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSiteClick)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    site,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Icon(Icons.Default.ExpandMore, "Change site",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            // Date row
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onDateClick)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.CalendarMonth, null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp))
                Text(displayDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary)
            }
            if (workerCount > 0) {
                Text("$workerCount worker${if (workerCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─── Mark-all bar ─────────────────────────────────────────────────────────────

@Composable
private fun MarkAllBar(
    workerCount: Int, unsetCount: Int,
    onMarkAllPresent: () -> Unit, onAddWorker: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            TextButton(
                onClick = onMarkAllPresent,
                enabled = workerCount > 0,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Mark all present")
            }
            if (unsetCount > 0) {
                Text(
                    "$unsetCount row${if (unsetCount == 1) "" else "s"} not yet marked",
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = HalfDayColor
                )
            }
        }
        TextButton(onClick = onAddWorker) {
            Icon(Icons.Default.PersonSearch, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add worker")
        }
    }
}

// ─── Worker attendance card ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkerAttendanceCard(
    row: WorkerAttendanceRow,
    roles: List<Role>,
    onAttendanceTap: (Attendance) -> Unit,
    onToggleExpand: () -> Unit,
    onRoleChange: (String) -> Unit,
    onWageChange: (Int) -> Unit,
    onOtChange: (Int) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (row.isLocked)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // ── Main row ──────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Worker info
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.worker.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold)
                        if (row.isLocked) {
                            Icon(Icons.Default.Lock, "Locked",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (row.attendance == null) {
                            Text("· mark",
                                style = MaterialTheme.typography.labelSmall,
                                color = HalfDayColor)
                        }
                    }
                    val roleName = roles.firstOrNull { it.id == row.roleId }?.name ?: ""
                    Text("$roleName  ·  ₹${row.wage}/day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // P / HD / A chips
                AttendanceChips(
                    current = row.attendance,
                    locked = row.isLocked,
                    onSelect = onAttendanceTap,
                )

                // Expand chevron
                Icon(
                    if (row.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // OT badge on collapsed row
            if (!row.expanded && row.overtimeAmount > 0) {
                Text(
                    "OT +₹${row.overtimeAmount}",
                    modifier = Modifier.padding(start = 14.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // ── Expanded detail ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = row.expanded,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)),
            ) {
                ExpandedDetail(
                    row = row,
                    roles = roles,
                    onRoleChange = onRoleChange,
                    onWageChange = onWageChange,
                    onOtChange = onOtChange,
                    onNoteChange = onNoteChange,
                )
            }
        }
    }
}

// ─── Attendance chips (P / HD / A) ────────────────────────────────────────────

/**
 * [current] is nullable: null = unset (D-4 compliance — not yet marked).
 * When null all three chips show with an amber border to signal action required.
 */
@Composable
private fun AttendanceChips(
    current: Attendance?,
    locked: Boolean,
    onSelect: (Attendance) -> Unit,
) {
    val unset = current == null
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AttendanceChip(
            label = "P", selected = current == Attendance.PRESENT, unset = unset,
            selectedColor = PresentColor, locked = locked,
            onClick = { onSelect(Attendance.PRESENT) }
        )
        AttendanceChip(
            label = "HD", selected = current == Attendance.HALF_DAY, unset = unset,
            selectedColor = HalfDayColor, locked = locked,
            onClick = { onSelect(Attendance.HALF_DAY) }
        )
        AttendanceChip(
            label = "A", selected = current == Attendance.ABSENT, unset = unset,
            selectedColor = AbsentColor, locked = locked,
            onClick = { onSelect(Attendance.ABSENT) }
        )
    }
}

@Composable
private fun AttendanceChip(
    label: String, selected: Boolean, unset: Boolean,
    selectedColor: Color, locked: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) selectedColor else UnselectedBg,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chip_bg_$label"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "chip_scale_$label"
    )
    val textColor = when {
        selected -> Color.White
        unset    -> HalfDayColor.copy(alpha = 0.7f)
        else     -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when {
        selected -> selectedColor.copy(alpha = 0.6f)
        unset    -> HalfDayColor.copy(alpha = 0.5f)   // amber border = needs action
        else     -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .size(width = 38.dp, height = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .then(if (!locked) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor)
    }
}

// ─── Expanded detail section ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedDetail(
    row: WorkerAttendanceRow,
    roles: List<Role>,
    onRoleChange: (String) -> Unit,
    onWageChange: (Int) -> Unit,
    onOtChange: (Int) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // OT warning per rule C-2
        if (row.overtimeAmount > 0 && row.attendance == Attendance.ABSENT) {
            Text(
                "⚠ Overtime on an absent day (rule C-2)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Role dropdown — R-6: changes record only, not worker profile
        if (!row.isLocked) {
            var roleExpanded by remember { mutableStateOf(false) }
            val selectedRoleName = roles.firstOrNull { it.id == row.roleId }?.name ?: "—"
            ExposedDropdownMenuBox(
                expanded = roleExpanded,
                onExpandedChange = { roleExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedRoleName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Role (today only)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roleExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    supportingText = { Text("Does not change worker profile (rule R-6)") }
                )
                ExposedDropdownMenu(expanded = roleExpanded,
                    onDismissRequest = { roleExpanded = false }) {
                    roles.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role.name) },
                            onClick = { onRoleChange(role.id); roleExpanded = false }
                        )
                    }
                }
            }
        }

        // Wage — R-4: edit for this day only
        if (!row.isLocked) {
            OutlinedTextField(
                value = if (row.wage == 0) "" else row.wage.toString(),
                onValueChange = { onWageChange(it.toIntOrNull() ?: 0) },
                label = { Text("Wage today (₹)") },
                supportingText = { Text("Overrides default for this day only (rule R-4)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Overtime amount
        if (!row.isLocked) {
            OutlinedTextField(
                value = if (row.overtimeAmount == 0) "" else row.overtimeAmount.toString(),
                onValueChange = { onOtChange(it.toIntOrNull() ?: 0) },
                label = { Text("Overtime (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Note
        if (!row.isLocked) {
            OutlinedTextField(
                value = row.note,
                onValueChange = onNoteChange,
                label = { Text("Note (optional)") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Locked read-only summary
        if (row.isLocked) {
            Text("This record is locked (week finalized). Use the correction flow to edit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (row.overtimeAmount > 0)
                LabelValue("Overtime", "₹${row.overtimeAmount}")
            if (!row.note.isNullOrBlank())
                LabelValue("Note", row.note)
        }

        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
