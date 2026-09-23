package com.workermanagement.app.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(vm: ReportViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Reports",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Generate & export reports",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }

            // ── Report type selector chips ─────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportType.entries.forEach { type ->
                    FilterChip(
                        selected = state.selectedReport == type,
                        onClick = { vm.selectReport(type) },
                        label = { Text(type.label, fontSize = 12.sp) },
                        leadingIcon = if (state.selectedReport == type) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // ── Filters ─────────────────────────────────────────────────
            ReportFilters(state = state, vm = vm)

            // ── Generate button ─────────────────────────────────────────
            OutlinedButton(
                onClick = { vm.generateReport() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                enabled = !state.isLoading,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Generate Report")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // ── Results ─────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            ) {
                // Show appropriate result list based on report type
                when (state.selectedReport) {
                    ReportType.WEEKLY_ATTENDANCE -> state.weeklyAttendance?.let { rows ->
                        item { ReportSectionHeader("Weekly Attendance — ${state.weekStartDate}", rows.size) }
                        items(rows) { row -> WeeklyAttendanceCard(row) }
                    }
                    ReportType.WEEKLY_WAGE -> state.weeklyWage?.let { rows ->
                        item { ReportSectionHeader("Weekly Wage — ${state.weekStartDate}", rows.size) }
                        items(rows) { row -> WeeklyWageCard(row) }
                    }
                    ReportType.PAYMENT_STATUS -> state.paymentStatus?.let { rows ->
                        item { ReportSectionHeader("Payment Status — ${state.weekStartDate}", rows.size) }
                        items(rows) { row -> PaymentStatusCard(row) }
                    }
                    ReportType.OUTSTANDING_ADVANCES -> state.outstandingAdvances?.let { rows ->
                        item { ReportSectionHeader("Outstanding Advances", rows.size) }
                        items(rows) { row -> OutstandingAdvanceCard(row) }
                    }
                    ReportType.SITE_ATTENDANCE -> state.siteAttendance?.let { rows ->
                        item { ReportSectionHeader("Site Attendance — ${state.dateFrom} to ${state.dateTo}", rows.size) }
                        items(rows) { row -> SiteAttendanceCard(row) }
                    }
                    ReportType.SITE_WAGE_COST -> state.siteWageCost?.let { rows ->
                        item { ReportSectionHeader("Site Wage Cost — ${state.dateFrom} to ${state.dateTo}", rows.size) }
                        items(rows) { row -> SiteWageCostCard(row) }
                    }
                    ReportType.WORKER_HISTORY -> state.workerHistory?.let { rows ->
                        val wName = state.workers.firstOrNull { it.id == state.selectedWorkerId }?.name ?: ""
                        item { ReportSectionHeader("$wName — ${state.dateFrom} to ${state.dateTo}", rows.size) }
                        items(rows) { row -> WorkerHistoryCard(row) }
                    }
                    ReportType.PENDING_ADJUSTMENTS -> state.pendingAdjustments?.let { rows ->
                        item { ReportSectionHeader("Pending Adjustments", rows.size) }
                        items(rows) { row -> PendingAdjustmentCard(row) }
                    }
                }

                // Empty state
                val hasData = listOfNotNull(
                    state.weeklyAttendance, state.weeklyWage, state.paymentStatus,
                    state.outstandingAdvances, state.siteAttendance, state.siteWageCost,
                    state.workerHistory, state.pendingAdjustments,
                ).any { (it as? List<*>)?.isNotEmpty() == true }

                val hasAnyResult = listOfNotNull(
                    state.weeklyAttendance, state.weeklyWage, state.paymentStatus,
                    state.outstandingAdvances, state.siteAttendance, state.siteWageCost,
                    state.workerHistory, state.pendingAdjustments,
                ).isNotEmpty()

                if (hasAnyResult && !hasData) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No data found for the selected filters.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (!hasAnyResult && !state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Assessment,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Select a report type and tap Generate",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Export FAB ─────────────────────────────────────────────────────
        val hasResults = listOfNotNull(
            state.weeklyAttendance, state.weeklyWage, state.paymentStatus,
            state.outstandingAdvances, state.siteAttendance, state.siteWageCost,
            state.workerHistory, state.pendingAdjustments,
        ).any { (it as? List<*>)?.isNotEmpty() == true }

        if (hasResults) {
            ExtendedFloatingActionButton(
                onClick = {
                    vm.exportCsv()
                    vm.shareCsv(context)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Share, contentDescription = "Export CSV")
                Spacer(Modifier.width(8.dp))
                Text("Export CSV")
            }
        }

        // ── Error snackbar ────────────────────────────────────────────────
        state.errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { vm.clearError() }) { Text("Dismiss") }
                }
            ) {
                Text(msg)
            }
        }
    }
}

// ─── Filters ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportFilters(state: ReportUiState, vm: ReportViewModel) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        when (state.selectedReport) {
            ReportType.WEEKLY_ATTENDANCE,
            ReportType.WEEKLY_WAGE,
            ReportType.PAYMENT_STATUS -> {
                // Week picker
                WeekPicker(
                    label = "Week starting",
                    selected = state.weekStartDate,
                    weeks = state.availableWeeks,
                    onSelected = { vm.setWeekStartDate(it) },
                )
            }

            ReportType.SITE_ATTENDANCE,
            ReportType.SITE_WAGE_COST -> {
                // Date range
                DateRangeInputs(
                    from = state.dateFrom,
                    to = state.dateTo,
                    onFromChanged = { vm.setDateFrom(it) },
                    onToChanged = { vm.setDateTo(it) },
                )
            }

            ReportType.WORKER_HISTORY -> {
                // Worker picker + date range
                WorkerPicker(
                    workers = state.workers,
                    selectedId = state.selectedWorkerId,
                    onSelected = { vm.setSelectedWorker(it) },
                )
                Spacer(Modifier.height(8.dp))
                DateRangeInputs(
                    from = state.dateFrom,
                    to = state.dateTo,
                    onFromChanged = { vm.setDateFrom(it) },
                    onToChanged = { vm.setDateTo(it) },
                )
            }

            ReportType.OUTSTANDING_ADVANCES,
            ReportType.PENDING_ADJUSTMENTS -> {
                // No filters needed — snapshot reports
                Text(
                    text = "This report shows a live snapshot — no filters needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeekPicker(
    label: String,
    selected: String,
    weeks: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            trailingIcon = {
                Text("▼", modifier = Modifier.clickable { expanded = true })
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            weeks.forEach { week ->
                DropdownMenuItem(
                    text = { Text(week) },
                    onClick = { onSelected(week); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun DateRangeInputs(
    from: String,
    to: String,
    onFromChanged: (String) -> Unit,
    onToChanged: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = from,
            onValueChange = onFromChanged,
            label = { Text("From (yyyy-MM-dd)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = to,
            onValueChange = onToChanged,
            label = { Text("To (yyyy-MM-dd)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
    }
}

@Composable
private fun WorkerPicker(
    workers: List<com.workermanagement.data.entity.Worker>,
    selectedId: String?,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = workers.firstOrNull { it.id == selectedId }?.name ?: "Select worker..."
    Box {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Worker") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            trailingIcon = {
                Text("▼", modifier = Modifier.clickable { expanded = true })
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            workers.forEach { w ->
                DropdownMenuItem(
                    text = { Text("${w.name} (${w.code})") },
                    onClick = { onSelected(w.id); expanded = false },
                )
            }
        }
    }
}

// ─── Section header ───────────────────────────────────────────────────────────

@Composable
private fun ReportSectionHeader(title: String, count: Int) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "$count ${if (count == 1) "row" else "rows"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Report card composables ──────────────────────────────────────────────────

@Composable
private fun ReportCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
private fun WeeklyAttendanceCard(row: WeeklyAttendanceRow) {
    ReportCard {
        Text("${row.workerName} (${row.workerCode})", fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        LabelValue("Days worked", row.daysWorked.toString())
    }
}

@Composable
private fun WeeklyWageCard(row: WeeklyWageRow) {
    ReportCard {
        Text("${row.workerName} (${row.workerCode})", fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        LabelValue("Base", "₹${row.baseEarnings}")
        LabelValue("Overtime", "₹${row.overtimeEarnings}")
        LabelValue("Gross", "₹${row.grossEarnings}")
        LabelValue("Deduction", "₹${row.advanceDeduction}")
        LabelValue("Net payable", "₹${row.netPayable}",
            valueColor = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PaymentStatusCard(row: PaymentStatusRow) {
    val statusColor = when (row.status) {
        "PAID" -> Color(0xFF4CAF50)
        "PARTIALLY_PAID" -> Color(0xFFFFA726)
        else -> Color(0xFFEF5350)
    }
    ReportCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${row.workerName} (${row.workerCode})", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = row.status.replace("_", " "),
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        LabelValue("Net payable", "₹${row.netPayable}")
        LabelValue("Paid", "₹${row.totalPaid}")
        LabelValue("Remaining", "₹${row.remaining}",
            valueColor = if (row.remaining > 0) Color(0xFFEF5350) else Color(0xFF4CAF50))
    }
}

@Composable
private fun OutstandingAdvanceCard(row: OutstandingAdvanceRow) {
    ReportCard {
        Text("${row.workerName} (${row.workerCode})", fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        LabelValue("Outstanding balance", "₹${row.balance}",
            valueColor = Color(0xFFEF5350))
    }
}

@Composable
private fun SiteAttendanceCard(row: SiteAttendanceRow) {
    ReportCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(row.siteName, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(row.date, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        LabelValue("Headcount", row.headcount.toString())
    }
}

@Composable
private fun SiteWageCostCard(row: SiteWageCostRow) {
    ReportCard {
        Text(row.siteName, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        LabelValue("Total wage cost", "₹${row.totalCost}",
            valueColor = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun WorkerHistoryCard(row: WorkerHistoryRow) {
    ReportCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(row.date, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            val attColor = when (row.attendance) {
                "PRESENT" -> Color(0xFF4CAF50)
                "HALF_DAY" -> Color(0xFFFFA726)
                else -> Color(0xFFEF5350)
            }
            Text(
                text = row.attendance.replace("_", " "),
                color = attColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        LabelValue("Site", row.siteName)
        LabelValue("Role", row.roleName)
        LabelValue("Wage", "₹${row.wage}")
        if (row.overtimeAmount > 0) {
            LabelValue("Overtime", "₹${row.overtimeAmount}")
        }
        LabelValue("Day total", "₹${row.dayTotal}",
            valueColor = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PendingAdjustmentCard(row: PendingAdjustmentRow) {
    val amtColor = if (row.amount >= 0) Color(0xFF4CAF50) else Color(0xFFEF5350)
    val sign = if (row.amount >= 0) "+" else ""
    ReportCard {
        Text("${row.workerName} (${row.workerCode})", fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        LabelValue("Week", row.weekStartDate)
        LabelValue("Amount", "$sign₹${row.amount}", valueColor = amtColor)
        Text(
            text = row.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
