package com.workermanagement.app.ui.payment

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.workermanagement.data.entity.PaymentMethod
import kotlinx.coroutines.launch
import wages.SettlementStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ─── Colour palette (matches Settlement screen) ───────────────────────────────

private val GradientTop    = Color(0xFF1A2B6D)
private val GradientBottom = Color(0xFF0F111A)
private val GreenAccent    = Color(0xFF4CAF50)
private val AmberAccent    = Color(0xFFFFB300)
private val RedAccent      = Color(0xFFF44336)
private val BlueAccent     = Color(0xFF42A5F5)
private val CardBg         = Color(0xFF1E2035)
private val SurfaceBg      = Color(0xFF252840)

// ─── Payment Screen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(vm: PaymentViewModel) {
    val paymentState    by vm.paymentUiState.collectAsState()
    val adjustmentState by vm.adjustmentUiState.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val scope    = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(paymentState.errorMessage) {
        paymentState.errorMessage?.let {
            scope.launch { snackbar.showSnackbar(it) }
            vm.clearError()
        }
    }
    LaunchedEffect(adjustmentState.errorMessage) {
        adjustmentState.errorMessage?.let {
            scope.launch { snackbar.showSnackbar(it) }
            vm.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Gradient header ──────────────────────────────────────────────
            PaymentHeader()

            // ── Tabs ─────────────────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    text     = { Text("Payments") },
                    icon     = { Icon(Icons.Default.MonetizationOn, null, Modifier.size(18.dp)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    text     = { Text("Adjustments") },
                    icon     = { Icon(Icons.Default.Tune, null, Modifier.size(18.dp)) },
                )
            }

            when (selectedTab) {
                0 -> PaymentsTab(vm, paymentState)
                1 -> AdjustmentsTab(vm, adjustmentState)
            }
        }
    }

    // ── Dialogs (rendered outside the scaffold column so they overlay properly) ──

    // Record-payment dialog
    if (paymentState.recordPaymentState is RecordPaymentState.Active ||
        paymentState.recordPaymentState is RecordPaymentState.Saving) {
        RecordPaymentDialog(vm, paymentState.recordPaymentState)
    }

    // Void-payment dialog
    if (paymentState.voidDialogState is VoidDialogState.Shown) {
        VoidPaymentDialog(vm, paymentState.voidDialogState as VoidDialogState.Shown)
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun PaymentHeader() {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(GradientTop, GradientBottom)))
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Payments",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
            )
            Text(
                "Record & manage payments",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  PAYMENTS TAB
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PaymentsTab(vm: PaymentViewModel, state: PaymentUiState) {
    val selected = state.selectedSettlement

    if (selected == null) {
        SettlementList(vm, state)
    } else {
        SettlementDetail(vm, state, selected)
    }
}

// ── Settlement list ───────────────────────────────────────────────────────────

@Composable
private fun SettlementList(vm: PaymentViewModel, state: PaymentUiState) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val grouped = state.settlements.groupBy { it.status }
    val order   = listOf(SettlementStatus.PENDING, SettlementStatus.PARTIALLY_PAID, SettlementStatus.PAID)

    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (state.settlements.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.MonetizationOn, null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "No settlements yet.\nFinalize a week first.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        order.forEach { status ->
            val items = grouped[status] ?: return@forEach
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(SurfaceBg)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusChip(status, small = true)
                    Text(
                        statusLabel(status),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        "(${items.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items, key = { it.settlement.id }) { item ->
                SettlementRow(item) { vm.selectSettlement(item) }
            }
        }
    }
}

@Composable
private fun SettlementRow(item: SettlementListItem, onClick: () -> Unit) {
    val net   = item.settlement.netPayable
    val paid  = item.totalNonVoidPayments
    val left  = net - paid
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.workerName, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "Week of ${formatWeek(item.settlement.weekStartDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmountLabel("Net", "₹$net")
                    AmountLabel("Paid", "₹$paid", color = GreenAccent)
                    if (left > 0) AmountLabel("Due", "₹$left", color = AmberAccent)
                }
            }
            StatusChip(item.status)
        }
    }
}

// ── Settlement detail ─────────────────────────────────────────────────────────

@Composable
private fun SettlementDetail(
    vm: PaymentViewModel,
    state: PaymentUiState,
    selected: SettlementListItem,
) {
    val s   = selected.settlement
    val net = s.netPayable
    val paid = selected.totalNonVoidPayments

    Scaffold(
        floatingActionButton = {
            // Only show FAB if there's remaining balance (P-4: can't pay more than net)
            if (paid < net) {
                ExtendedFloatingActionButton(
                    onClick = { vm.openRecordPayment() },
                    icon    = { Icon(Icons.Default.Add, null) },
                    text    = { Text("Record Payment") },
                    containerColor = GreenAccent,
                    contentColor   = Color.White,
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // Back nav
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.clearSelectedSettlement() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("All settlements", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Settlement summary card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column {
                                Text(selected.workerName, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                Text("Week of ${formatWeek(s.weekStartDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusChip(selected.status)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        SummaryRow("Gross earnings", "₹${s.grossEarnings}")
                        SummaryRow("Advance deduction", "₹${s.advanceDeduction}")
                        SummaryRow("Net payable", "₹$net",
                            valueColor = GreenAccent, bold = true)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        SummaryRow("Total paid", "₹$paid",
                            valueColor = if (paid >= net) GreenAccent else AmberAccent)
                        SummaryRow("Remaining", "₹${net - paid}",
                            valueColor = if (net - paid <= 0) GreenAccent else RedAccent,
                            bold = net - paid > 0)
                    }
                }
            }

            // Payment history header
            item {
                Row(
                    Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.History, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "PAYMENT HISTORY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp,
                    )
                }
            }

            if (state.paymentHistory.isEmpty()) {
                item {
                    Text(
                        "No payments recorded yet.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                items(state.paymentHistory, key = { it.payment.id }) { item ->
                    PaymentHistoryRow(item, onVoidClick = {
                        vm.openVoidDialog(item.payment.id, item.payment.amount)
                    })
                }
            }
        }
    }
}

@Composable
private fun PaymentHistoryRow(item: PaymentHistoryItem, onVoidClick: () -> Unit) {
    val p       = item.payment
    val isVoid  = p.isVoid
    val rowAlpha = if (isVoid) 0.5f else 1f

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isVoid) SurfaceBg.copy(alpha = 0.5f)
                else CardBg
            )
            .border(
                1.dp,
                if (isVoid) RedAccent.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(10.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                if (isVoid) Icons.Default.Block else Icons.Default.CheckCircle,
                null,
                tint = if (isVoid) RedAccent.copy(alpha = rowAlpha) else GreenAccent,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    "₹${p.amount}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isVoid) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onBackground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        shortDate(p.paidOn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        p.method.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isVoid) {
                    Text(
                        "VOIDED",
                        style = MaterialTheme.typography.labelSmall,
                        color = RedAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                }
                p.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Void button — only on non-void rows
        if (item.isVoidable) {
            TextButton(
                onClick = onVoidClick,
                colors  = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = RedAccent,
                )
            ) {
                Text("Void", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ── Record-payment dialog ─────────────────────────────────────────────────────

@Composable
private fun RecordPaymentDialog(vm: PaymentViewModel, state: RecordPaymentState) {
    val active = state as? RecordPaymentState.Active ?: return
    var methodMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { vm.closeRecordPayment() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.MonetizationOn, null, tint = GreenAccent)
                Text("Record Payment")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Remaining balance info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (active.remaining > 0)
                            Color(0xFF1B2E1B) else Color(0xFF2B1A1A)
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Net payable", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₹${active.netPayable}", style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Remaining", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₹${active.remaining}", style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (active.remaining > 0) AmberAccent else GreenAccent)
                    }
                }

                // Amount
                OutlinedTextField(
                    value         = active.amountInput,
                    onValueChange = vm::onPaymentAmountChanged,
                    label         = { Text("Amount (₹)") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier      = Modifier.fillMaxWidth(),
                    isError       = active.amountError != null,
                    supportingText = active.amountError?.let {
                        { Text(it, color = RedAccent) }
                    },
                )

                // Date
                OutlinedTextField(
                    value         = active.date,
                    onValueChange = vm::onPaymentDateChanged,
                    label         = { Text("Date (yyyy-MM-dd)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )

                // Method
                Box {
                    OutlinedTextField(
                        value         = active.method.name,
                        onValueChange = {},
                        label         = { Text("Method") },
                        readOnly      = true,
                        modifier      = Modifier.fillMaxWidth().clickable {
                            methodMenuExpanded = true
                        },
                        trailingIcon  = {
                            Icon(Icons.Default.Tune, null,
                                modifier = Modifier.clickable { methodMenuExpanded = true })
                        },
                    )
                    DropdownMenu(
                        expanded        = methodMenuExpanded,
                        onDismissRequest = { methodMenuExpanded = false },
                    ) {
                        PaymentMethod.entries.forEach { m ->
                            DropdownMenuItem(
                                text    = { Text(m.name) },
                                onClick = {
                                    vm.onPaymentMethodChanged(m)
                                    methodMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                // Note
                OutlinedTextField(
                    value         = active.note,
                    onValueChange = vm::onPaymentNoteChanged,
                    label         = { Text("Note (optional)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { vm.recordPayment() },
                enabled  = active.amountInput.isNotBlank()
                        && active.amountError == null
                        && (active.amountInput.trim().toIntOrNull() ?: 0) > 0,
                colors   = ButtonDefaults.buttonColors(containerColor = GreenAccent),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { vm.closeRecordPayment() }) { Text("Cancel") }
        },
    )
}

// ── Void-payment dialog ───────────────────────────────────────────────────────

@Composable
private fun VoidPaymentDialog(vm: PaymentViewModel, state: VoidDialogState.Shown) {
    AlertDialog(
        onDismissRequest = { vm.closeVoidDialog() },
        icon = { Icon(Icons.Default.Block, null, tint = RedAccent) },
        title = { Text("Void payment of ₹${state.amount}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This cannot be undone. The voided payment stays visible in history. " +
                    "Record a new payment afterwards if needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value         = state.reason,
                    onValueChange = vm::onVoidReasonChanged,
                    label         = { Text("Void reason (required)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    isError       = state.reason.isBlank(),
                    supportingText = if (state.reason.isBlank()) {
                        { Text("Reason is required to void a payment", color = RedAccent) }
                    } else null,
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { vm.confirmVoidPayment() },
                enabled  = state.reason.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = RedAccent),
            ) { Text("Void Payment") }
        },
        dismissButton = {
            TextButton(onClick = { vm.closeVoidDialog() }) { Text("Cancel") }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ADJUSTMENTS TAB
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AdjustmentsTab(vm: PaymentViewModel, state: AdjustmentUiState) {
    when (val cs = state.correctionState) {
        is CorrectionState.Idle   -> AdjustmentIdleView(vm, state)
        is CorrectionState.Editing -> CorrectionEditView(vm, cs)
        is CorrectionState.Saving -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Applying correction…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is CorrectionState.Saved -> CorrectionSavedView(cs, onDone = { vm.resetCorrectionState() })
    }
}

// ── Adjustment idle view ──────────────────────────────────────────────────────

@Composable
private fun AdjustmentIdleView(vm: PaymentViewModel, state: AdjustmentUiState) {
    // Worker picker state for the "correct a record" flow
    var showWorkerPicker by remember { mutableStateOf(false) }
    var selectedWorker   by remember { mutableStateOf<com.workermanagement.data.entity.Worker?>(null) }
    var showRecordPicker by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        // Pending adjustments section
        item {
            Row(
                Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Pending, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "PENDING ADJUSTMENTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp,
                )
            }
        }

        if (state.pendingAdjustments.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, null,
                            modifier = Modifier.size(40.dp), tint = GreenAccent)
                        Text("No pending adjustments",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(state.pendingAdjustments, key = { it.id }) { adj ->
                PendingAdjustmentRow(adj)
            }
        }

        // "Correct a record" entry point
        item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
        item {
            Row(
                Modifier.padding(start = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Edit, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "CORRECT A FINALIZED RECORD",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp,
                )
            }
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clickable { showWorkerPicker = true },
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Lock, null, tint = AmberAccent)
                    Column {
                        Text("Select a worker to correct",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                        Text("An adjustment entry will be created to account for the change",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Worker picker dialog
    if (showWorkerPicker) {
        AlertDialog(
            onDismissRequest = { showWorkerPicker = false },
            title = { Text("Select worker") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.workers.filter { it.isActive }.forEach { w ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedWorker   = w
                                    showWorkerPicker = false
                                    showRecordPicker = true
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.Groups, null, Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(w.name, style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium)
                                Text(w.code, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWorkerPicker = false }) { Text("Cancel") }
            },
        )
    }

    // Locked record picker — given the ViewModel loads by ID, we collect locked records here
    if (showRecordPicker && selectedWorker != null) {
        LockedRecordPickerDialog(
            worker    = selectedWorker!!,
            onSelect  = { recordId ->
                showRecordPicker = false
                vm.loadLockedRecord(recordId)
            },
            onDismiss = { showRecordPicker = false },
        )
    }
}

@Composable
private fun PendingAdjustmentRow(adj: com.workermanagement.data.entity.Adjustment) {
    val sign   = if (adj.amount >= 0) "+" else ""
    val color  = if (adj.amount >= 0) GreenAccent else AmberAccent
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(adj.reason, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                Text(shortDate(adj.createdAt.take(10)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${sign}₹${kotlin.math.abs(adj.amount)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// ── Locked record picker ──────────────────────────────────────────────────────

@Composable
private fun LockedRecordPickerDialog(
    worker: com.workermanagement.data.entity.Worker,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var recordId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select locked record for ${worker.name}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter the record ID of the locked daily record to correct.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = recordId,
                    onValueChange = { recordId = it },
                    label = { Text("Record ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (recordId.isNotBlank()) onSelect(recordId.trim()) },
                enabled  = recordId.isNotBlank(),
            ) { Text("Load Record") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Correction editor ─────────────────────────────────────────────────────────

@Composable
private fun CorrectionEditView(vm: PaymentViewModel, state: CorrectionState.Editing) {
    val record = state.target.record
    var attMenuExpanded by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
        item {
            // Locked record context card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1A0A)),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Lock, null, tint = AmberAccent,
                        modifier = Modifier.size(20.dp))
                    Column {
                        Text("Correcting locked record — ${state.target.workerName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold)
                        Text("${shortDate(record.workDate)} · Original: ${record.attendance.name} ₹${record.wage} OT:₹${record.overtimeAmount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Settlement gross: ₹${state.target.settlement.grossEarnings}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Editable fields
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Edit record", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    // Attendance
                    Box {
                        OutlinedTextField(
                            value = state.newAttendance.name,
                            onValueChange = {},
                            label    = { Text("Attendance") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().clickable { attMenuExpanded = true },
                            trailingIcon = {
                                Icon(Icons.Default.Tune, null,
                                    modifier = Modifier.clickable { attMenuExpanded = true })
                            },
                        )
                        DropdownMenu(
                            expanded = attMenuExpanded,
                            onDismissRequest = { attMenuExpanded = false },
                        ) {
                            Attendance.entries.forEach { a ->
                                DropdownMenuItem(
                                    text = { Text(a.name) },
                                    onClick = {
                                        vm.onCorrectionAttendanceChanged(a)
                                        attMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    // Wage
                    OutlinedTextField(
                        value = state.newWage,
                        onValueChange = vm::onCorrectionWageChanged,
                        label  = { Text("Wage (₹/day)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Overtime
                    OutlinedTextField(
                        value = state.newOT,
                        onValueChange = vm::onCorrectionOTChanged,
                        label  = { Text("Overtime amount (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        item {
            Button(
                onClick  = { vm.requestCorrectionConfirm() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(52.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
            ) {
                Icon(Icons.Default.Warning, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Preview adjustment", fontWeight = FontWeight.Bold)
            }
        }
        item {
            TextButton(
                onClick  = { vm.resetCorrectionState() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancel") }
        }
    }

    // Confirmation dialog
    if (state.showConfirm) {
        CorrectionConfirmDialog(vm, state)
    }
}

// ── Correction confirm dialog (J-1) ──────────────────────────────────────────

@Composable
private fun CorrectionConfirmDialog(vm: PaymentViewModel, state: CorrectionState.Editing) {
    val previewAmount = state.previewAdjustmentAmount
    val previewSign   = state.previewSign

    AlertDialog(
        onDismissRequest = { vm.dismissCorrectionConfirm() },
        icon = { Icon(Icons.Default.Warning, null, tint = AmberAccent) },
        title = { Text("Confirm correction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Editing a finalized record will create an adjustment entry. " +
                    "The original settlement and payment records will not be changed. " +
                    "This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Adjustment preview
                if (previewAmount != null && previewSign != null) {
                    val signStr  = if (previewSign >= 0) "+" else "-"
                    val color    = if (previewSign >= 0) GreenAccent else AmberAccent
                    val meaning  = if (previewSign > 0) "worker is owed more"
                                   else if (previewSign < 0) "worker was overpaid"
                                   else "no change"
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                        shape  = RoundedCornerShape(8.dp),
                    ) {
                        Column(Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Adjustment preview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${signStr}₹$previewAmount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = color)
                            Text(meaning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Mandatory reason field
                OutlinedTextField(
                    value         = state.reason,
                    onValueChange = vm::onCorrectionReasonChanged,
                    label         = { Text("Reason for correction (required)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    isError       = state.reason.isBlank(),
                    supportingText = if (state.reason.isBlank()) {
                        { Text("A reason is required", color = RedAccent) }
                    } else null,
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { vm.applyCorrection() },
                enabled  = state.reason.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = AmberAccent),
            ) { Text("Apply Correction") }
        },
        dismissButton = {
            TextButton(onClick = { vm.dismissCorrectionConfirm() }) { Text("Cancel") }
        },
    )
}

// ── Correction saved confirmation ─────────────────────────────────────────────

@Composable
private fun CorrectionSavedView(state: CorrectionState.Saved, onDone: () -> Unit) {
    val signStr = if (state.sign >= 0) "+" else "-"
    val color   = if (state.sign >= 0) GreenAccent else AmberAccent
    val meaning = if (state.sign > 0) "Worker is owed more — will appear in next settlement"
                  else if (state.sign < 0) "Worker was overpaid — will appear in next settlement"
                  else "No change to gross earnings"

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(Icons.Default.CheckCircle, null,
                modifier = Modifier.size(64.dp), tint = GreenAccent)
            Text("Correction applied", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape  = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Adjustment created",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${signStr}₹${state.adjustmentAmount}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold, color = color)
                    Text(meaning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
            ) { Text("Done", fontWeight = FontWeight.Bold) }
        }
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun StatusChip(status: SettlementStatus, small: Boolean = false) {
    val (bg, fg, label) = when (status) {
        SettlementStatus.PENDING        -> Triple(Color(0xFF2B1A0A), AmberAccent, "PENDING")
        SettlementStatus.PARTIALLY_PAID -> Triple(Color(0xFF1A1F2B), BlueAccent,  "PARTIAL")
        SettlementStatus.PAID           -> Triple(Color(0xFF1B2E1B), GreenAccent, "PAID")
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(if (small) 4.dp else 8.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.5f), RoundedCornerShape(if (small) 4.dp else 8.dp))
            .padding(horizontal = if (small) 6.dp else 10.dp, vertical = if (small) 2.dp else 4.dp),
    ) {
        Text(label,
            style = if (small) MaterialTheme.typography.labelSmall
                    else MaterialTheme.typography.labelMedium,
            color = fg, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun AmountLabel(label: String, value: String, color: Color = MaterialTheme.colorScheme.onBackground) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun SummaryRow(
    label: String, value: String,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
    bold: Boolean = false,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = valueColor)
    }
}

private fun statusLabel(status: SettlementStatus) = when (status) {
    SettlementStatus.PENDING        -> "PENDING"
    SettlementStatus.PARTIALLY_PAID -> "PARTIALLY PAID"
    SettlementStatus.PAID           -> "PAID"
}

private fun formatWeek(weekStartDate: String): String = try {
    val d   = LocalDate.parse(weekStartDate)
    val end = d.plusDays(6)
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    "${d.format(fmt)} – ${end.format(fmt)}"
} catch (_: Exception) { weekStartDate }

private fun shortDate(date: String): String = try {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE d MMM"))
} catch (_: Exception) { date }
