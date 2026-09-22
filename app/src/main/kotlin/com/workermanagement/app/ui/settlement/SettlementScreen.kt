package com.workermanagement.app.ui.settlement

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workermanagement.data.entity.Attendance
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ─── Colour constants ─────────────────────────────────────────────────────────

private val GradientTop    = Color(0xFF1A2B6D)
private val GradientBottom = Color(0xFF0F111A)
private val GreenAccent    = Color(0xFF4CAF50)
private val AmberAccent    = Color(0xFFFFB300)
private val RedAccent      = Color(0xFFF44336)
private val CardBg         = Color(0xFF1E2035)
private val SurfaceBg      = Color(0xFF252840)

// ─── Settlement Screen ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(vm: SettlementViewModel) {
    val uiState      by vm.uiState.collectAsState()
    val workers      by vm.workers.collectAsState()
    val weeks        by vm.availableWeeks.collectAsState()
    val selectedWeek by vm.selectedWeek.collectAsState()
    val selectedWorker by vm.selectedWorker.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val scope    = rememberCoroutineScope()

    var showWeekPicker   by remember { mutableStateOf(false) }
    var showWorkerPicker by remember { mutableStateOf(false) }
    var showFinalizeConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saveError) {
        uiState.saveError?.let { scope.launch { snackbar.showSnackbar(it) }; vm.clearSaveError() }
    }
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            scope.launch { snackbar.showSnackbar("✓ Week finalized") }
            vm.clearSaveSuccess()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // ── Gradient header ──────────────────────────────────────────────
            item {
                SettlementHeader(
                    selectedWeek   = selectedWeek,
                    selectedWorker = selectedWorker?.name,
                    onWeekClick    = { showWeekPicker = true },
                    onWorkerClick  = { showWorkerPicker = true },
                )
            }

            // ── Empty state ──────────────────────────────────────────────────
            if (selectedWeek == null || selectedWorker == null) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (selectedWeek == null)
                                    "Select a week to begin settlement"
                                else
                                    "Select a worker for ${formatWeek(selectedWeek!!)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }
                return@LazyColumn
            }

            // ── Already-finalized banner ─────────────────────────────────────
            if (uiState.existingSettlement != null) {
                item { FinalizedBanner() }
            }

            // ── Day-by-day breakdown ─────────────────────────────────────────
            item {
                SectionHeader("Day-by-day Breakdown")
            }

            if (uiState.dayRows.isEmpty()) {
                item {
                    Text(
                        "No attendance records found for this worker/week.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                item { BreakdownTable(uiState.dayRows) }
            }

            // ── Earnings summary ─────────────────────────────────────────────
            item {
                EarningsSummaryCard(
                    base = uiState.baseEarnings,
                    overtime = uiState.overtimeEarnings,
                    gross = uiState.grossEarnings,
                )
            }

            // ── Advance balance ──────────────────────────────────────────────
            item {
                AdvanceBalanceCard(balance = uiState.advanceBalance)
            }

            // ── Deduction input ──────────────────────────────────────────────
            if (uiState.existingSettlement == null) {
                item {
                    DeductionCard(
                        input = uiState.deductionInput,
                        state = uiState.deductionState,
                        onChanged = vm::onDeductionChanged,
                    )
                }
            } else {
                // Show snapshot values from the locked settlement
                val s = uiState.existingSettlement!!
                item {
                    SnapshotCard(
                        deduction = s.advanceDeduction,
                        net       = s.netPayable,
                        status    = s.status.name,
                    )
                }
            }

            // ── Pending adjustments (J-5) ─────────────────────────────────────
            // Only shown before finalization and when prior unsettled adjustments exist.
            if (uiState.existingSettlement == null && uiState.pendingAdjustments.isNotEmpty()) {
                item {
                    PendingAdjustmentsCard(
                        adjustmentSum     = uiState.adjustmentSum,
                        count             = uiState.pendingAdjustments.size,
                        included          = uiState.includeAdjustments,
                        onToggle          = vm::toggleIncludeAdjustments,
                    )
                }
            }

            // ── Finalize button ───────────────────────────────────────────────
            if (uiState.existingSettlement == null) {
                item {
                    Spacer(Modifier.height(8.dp))
                    FinalizeButton(
                        enabled = uiState.deductionState is DeductionState.Valid
                                && uiState.dayRows.isNotEmpty()
                                && !uiState.isSaving,
                        isSaving = uiState.isSaving,
                        onClick = { showFinalizeConfirm = true },
                    )
                }
            }
        }
    }

    // ── Week picker dialog ────────────────────────────────────────────────────
    if (showWeekPicker) {
        AlertDialog(
            onDismissRequest = { showWeekPicker = false },
            title = { Text("Select week") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (weeks.isEmpty()) {
                        Text("No attendance records exist yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    weeks.forEach { week ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { vm.selectWeek(week); showWeekPicker = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(formatWeek(week), style = MaterialTheme.typography.bodyLarge)
                            if (selectedWeek == week) Icon(Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showWeekPicker = false }) { Text("Cancel") } }
        )
    }

    // ── Worker picker dialog ──────────────────────────────────────────────────
    if (showWorkerPicker) {
        AlertDialog(
            onDismissRequest = { showWorkerPicker = false },
            title = { Text("Select worker") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    workers.filter { it.isActive }.forEach { w ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { vm.selectWorker(w); showWorkerPicker = false }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(w.name, style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium)
                                Text(w.code, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selectedWorker?.id == w.id)
                                Icon(Icons.Default.Check, null,
                                    tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showWorkerPicker = false }) { Text("Cancel") } }
        )
    }

    // ── Finalize confirmation ─────────────────────────────────────────────────
    if (showFinalizeConfirm) {
        val net = (uiState.deductionState as? DeductionState.Valid)?.net ?: 0
        AlertDialog(
            onDismissRequest = { showFinalizeConfirm = false },
            icon = { Icon(Icons.Default.Lock, null, tint = AmberAccent) },
            title = { Text("Finalize week?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This will lock all daily records for this week. Editing will require the correction flow.")
                    HorizontalDivider()
                    LabelValue("Gross earnings", "₹${uiState.grossEarnings}")
                    LabelValue("Advance deduction", "₹${uiState.deductionInput.trim().toIntOrNull() ?: 0}")
                    LabelValue("Net payable", "₹$net")
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFinalizeConfirm = false; vm.finalize() },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent)
                ) { Text("Finalize") }
            },
            dismissButton = {
                TextButton(onClick = { showFinalizeConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun SettlementHeader(
    selectedWeek: String?,
    selectedWorker: String?,
    onWeekClick: () -> Unit,
    onWorkerClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(GradientTop, GradientBottom)))
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Settlement",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp)
            // Week selector
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onWeekClick)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.CalendarMonth, null,
                    tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Text(
                    selectedWeek?.let { "Week of ${formatWeek(it)}" } ?: "Select a week",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Icon(Icons.Default.ExpandMore, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            // Worker selector
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onWorkerClick)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Groups, null,
                    tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                Text(
                    selectedWorker ?: "Select worker",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

// ─── Section header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
    )
}

// ─── Day-by-day table ─────────────────────────────────────────────────────────

@Composable
private fun BreakdownTable(dayRows: List<DayRow>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column {
            // Header row
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("Date", "Site", "Role", "Wage", "Att", "OT", "Total").forEach { h ->
                    Text(h, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(if (h == "Date") 1.6f else 1f))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            dayRows.forEachIndexed { idx, row ->
                if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                DayRowItem(row)
            }
        }
    }
}

@Composable
private fun DayRowItem(row: DayRow) {
    val attColor = when (row.record.attendance) {
        Attendance.PRESENT  -> GreenAccent
        Attendance.HALF_DAY -> AmberAccent
        Attendance.ABSENT   -> RedAccent
    }
    val attLabel = when (row.record.attendance) {
        Attendance.PRESENT  -> "P"
        Attendance.HALF_DAY -> "HD"
        Attendance.ABSENT   -> "A"
    }
    val locked = row.record.isLocked

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (locked) SurfaceBg.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Date
        Column(Modifier.weight(1.6f)) {
            Text(shortDate(row.record.workDate),
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            if (locked) Icon(Icons.Default.Lock, null,
                modifier = Modifier.size(10.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(row.site?.name?.take(6) ?: "—",
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(row.role?.name?.take(6) ?: "—",
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text("₹${row.record.wage}",
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(attLabel, Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = attColor, fontWeight = FontWeight.Bold)
        Text(if (row.record.overtimeAmount > 0) "+${row.record.overtimeAmount}" else "—",
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = if (row.record.overtimeAmount > 0) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("₹${row.dayTotal}",
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold)
    }
}

// ─── Earnings summary card ────────────────────────────────────────────────────

@Composable
private fun EarningsSummaryCard(base: Int, overtime: Int, gross: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Earnings", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            EarningsRow("Base earnings", "₹$base")
            EarningsRow("Overtime earnings", "₹$overtime",
                color = MaterialTheme.colorScheme.tertiary)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            EarningsRow("Gross earnings", "₹$gross",
                color = GreenAccent, bold = true)
        }
    }
}

@Composable
private fun EarningsRow(
    label: String, value: String,
    color: Color = MaterialTheme.colorScheme.onBackground, bold: Boolean = false
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (bold) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}

// ─── Advance balance card ─────────────────────────────────────────────────────

@Composable
private fun AdvanceBalanceCard(balance: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (balance > 0) Color(0xFF2B1A1A) else CardBg
        ),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (balance > 0) Icon(Icons.Default.Warning, null,
                    tint = AmberAccent, modifier = Modifier.size(18.dp))
                Text("Outstanding advance balance",
                    style = MaterialTheme.typography.bodyMedium)
            }
            Text("₹$balance",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (balance > 0) AmberAccent else GreenAccent)
        }
    }
}

// ─── Deduction input card ─────────────────────────────────────────────────────

@Composable
private fun DeductionCard(
    input: String,
    state: DeductionState,
    onChanged: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Advance deduction", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = input,
                onValueChange = onChanged,
                label = { Text("Deduction amount (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    when (state) {
                        is DeductionState.Rejected ->
                            Text("${state.reason}. Max allowed: ₹${state.maxAllowed}",
                                color = RedAccent)
                        is DeductionState.Valid ->
                            Text("Net payable: ₹${state.net}", color = GreenAccent)
                        is DeductionState.None -> {}
                    }
                },
                isError = state is DeductionState.Rejected,
            )

            // Net payable summary line
            if (state is DeductionState.Valid) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Net payable",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold)
                    Text("₹${state.net}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold, color = GreenAccent)
                }
            }
        }
    }
}

// ─── Snapshot card (post-finalize) ───────────────────────────────────────────

@Composable
private fun SnapshotCard(deduction: Int, net: Int, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Settlement summary", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            EarningsRow("Advance deduction", "₹$deduction")
            EarningsRow("Net payable", "₹$net", color = GreenAccent, bold = true)
            EarningsRow("Status", status)
        }
    }
}

// ─── Finalized banner ─────────────────────────────────────────────────────────

@Composable
private fun FinalizedBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1B2E1B))
            .border(1.dp, GreenAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = GreenAccent, modifier = Modifier.size(20.dp))
        Column {
            Text("Week finalized", style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, color = GreenAccent)
            Text("Daily records are locked. Use correction flow to edit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Finalize button ──────────────────────────────────────────────────────────

@Composable
private fun FinalizeButton(enabled: Boolean, isSaving: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenAccent,
                contentColor   = Color.White,
            )
        ) {
            if (isSaving) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Finalize Week", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatWeek(weekStartDate: String): String = try {
    val d = LocalDate.parse(weekStartDate)
    val end = d.plusDays(6)
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    "${d.format(fmt)} – ${end.format(fmt)}"
} catch (_: Exception) { weekStartDate }

private fun shortDate(date: String): String = try {
    LocalDate.parse(date)
        .format(DateTimeFormatter.ofPattern("EEE d MMM"))
} catch (_: Exception) { date }

// ─── Pending adjustments card (J-5) ──────────────────────────────────────────

@Composable
private fun PendingAdjustmentsCard(
    adjustmentSum: Int,
    count: Int,
    included: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val sign   = if (adjustmentSum >= 0) "+" else ""
    val color  = if (adjustmentSum >= 0) GreenAccent else AmberAccent
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Pending adjustments",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Text(
                "$count prior-week correction(s) totalling ${sign}₹${kotlin.math.abs(adjustmentSum)} are unsettled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggle(!included) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked  = included,
                    onCheckedChange = onToggle,
                )
                Column {
                    Text(
                        "Include in this settlement",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Net payable will be adjusted by ${sign}₹${kotlin.math.abs(adjustmentSum)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                    )
                }
            }
        }
    }
}
