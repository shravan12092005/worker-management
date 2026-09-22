package com.workermanagement.app.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.Adjustment
import com.workermanagement.data.entity.Attendance as RoomAttendance
import com.workermanagement.data.entity.DailyRecord
import com.workermanagement.data.entity.Payment
import com.workermanagement.data.entity.PaymentMethod
import com.workermanagement.data.entity.WeeklySettlement
import com.workermanagement.data.entity.Worker
import com.workermanagement.app.ui.settlement.toWageAttendance
import com.workermanagement.app.ui.settlement.toWageRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wages.WageEngine
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ─── UI models ────────────────────────────────────────────────────────────────

/**
 * One row in the settlement list.
 * status is always derived via WageEngine.settlementStatus — never stored (P-3).
 */
data class SettlementListItem(
    val settlement: WeeklySettlement,
    val workerName: String,
    val totalNonVoidPayments: Int,
    val status: wages.SettlementStatus,         // derived, not from settlement.status column
)

data class PaymentHistoryItem(
    val payment: Payment,
    val isVoidable: Boolean,
)

// Sealed state for the record-payment form
sealed class RecordPaymentState {
    data object Idle : RecordPaymentState()
    data class Active(
        val settlementId: String,
        val netPayable: Int,
        val totalPaid: Int,
        val remaining: Int,           // = netPayable - totalPaid
        val amountInput: String = "",
        val date: String = LocalDate.now().toString(),
        val method: PaymentMethod = PaymentMethod.CASH,
        val note: String = "",
        val amountError: String? = null,
    ) : RecordPaymentState()
    data object Saving : RecordPaymentState()
}

// Sealed state for the void-payment dialog
sealed class VoidDialogState {
    data object Hidden : VoidDialogState()
    data class Shown(val paymentId: String, val amount: Int, val reason: String = "") :
        VoidDialogState()
}

// ─── Adjustment correction UI models ─────────────────────────────────────────

data class CorrectionTarget(
    val record: DailyRecord,
    val workerName: String,
    val settlement: WeeklySettlement,
)

sealed class CorrectionState {
    data object Idle : CorrectionState()
    data class Editing(
        val target: CorrectionTarget,
        val newAttendance: RoomAttendance,
        val newWage: String,
        val newOT: String,
        // Confirmation dialog state
        val showConfirm: Boolean = false,
        val reason: String = "",
        // Preview after engine computes
        val previewAdjustmentAmount: Int? = null,  // null = not yet previewed
        val previewSign: Int? = null,
    ) : CorrectionState()
    class Saving : CorrectionState()
    data class Saved(val adjustmentAmount: Int, val sign: Int) : CorrectionState()
}

data class PaymentUiState(
    val settlements: List<SettlementListItem> = emptyList(),
    val selectedSettlement: SettlementListItem? = null,
    val paymentHistory: List<PaymentHistoryItem> = emptyList(),
    val recordPaymentState: RecordPaymentState = RecordPaymentState.Idle,
    val voidDialogState: VoidDialogState = VoidDialogState.Hidden,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class AdjustmentUiState(
    val pendingAdjustments: List<Adjustment> = emptyList(),
    val workers: List<Worker> = emptyList(),
    val correctionState: CorrectionState = CorrectionState.Idle,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class PaymentViewModel(private val db: AppDatabase) : ViewModel() {

    private val _paymentUiState = MutableStateFlow(PaymentUiState())
    val paymentUiState: StateFlow<PaymentUiState> = _paymentUiState.asStateFlow()

    private val _adjustmentUiState = MutableStateFlow(AdjustmentUiState())
    val adjustmentUiState: StateFlow<AdjustmentUiState> = _adjustmentUiState.asStateFlow()

    init {
        loadSettlements()
        loadPendingAdjustments()
        viewModelScope.launch(Dispatchers.IO) {
            _adjustmentUiState.value = _adjustmentUiState.value.copy(
                workers = db.workerDao().getAll()
            )
        }
    }

    // ── Payments: settlement list ─────────────────────────────────────────────

    fun loadSettlements() {
        viewModelScope.launch(Dispatchers.IO) {
            _paymentUiState.value = _paymentUiState.value.copy(isLoading = true)
            val workerCache = db.workerDao().getAll().associateBy { it.id }
            val items = db.settlementDao().getAllSettlements().map { s ->
                val totalPaid = db.paymentDao().getTotalNonVoidPayments(s.id)
                // P-3: status is always derived via WageEngine, never read from the stored column
                val status = WageEngine.settlementStatus(s.netPayable, totalPaid)
                SettlementListItem(
                    settlement            = s,
                    workerName            = workerCache[s.workerId]?.name ?: s.workerId,
                    totalNonVoidPayments  = totalPaid,
                    status                = status,
                )
            }
            _paymentUiState.value = _paymentUiState.value.copy(
                settlements = items,
                isLoading   = false,
            )
        }
    }

    // ── Payments: select settlement → load history ────────────────────────────

    fun selectSettlement(item: SettlementListItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val history = db.paymentDao().getForSettlement(item.settlement.id).map { p ->
                // A non-void payment is voidable only if the settlement is not yet PAID,
                // or if voiding it would not produce negative state. We allow void at any time
                // (P-5 says wrong payments are voided — no restriction on when).
                PaymentHistoryItem(payment = p, isVoidable = !p.isVoid)
            }
            _paymentUiState.value = _paymentUiState.value.copy(
                selectedSettlement = item,
                paymentHistory     = history,
                recordPaymentState = RecordPaymentState.Idle,
                voidDialogState    = VoidDialogState.Hidden,
            )
        }
    }

    fun clearSelectedSettlement() {
        _paymentUiState.value = _paymentUiState.value.copy(
            selectedSettlement = null,
            paymentHistory     = emptyList(),
            recordPaymentState = RecordPaymentState.Idle,
        )
    }

    // ── Payments: open record-payment form ───────────────────────────────────

    fun openRecordPayment() {
        val selected = _paymentUiState.value.selectedSettlement ?: return
        val remaining = selected.settlement.netPayable - selected.totalNonVoidPayments
        _paymentUiState.value = _paymentUiState.value.copy(
            recordPaymentState = RecordPaymentState.Active(
                settlementId = selected.settlement.id,
                netPayable   = selected.settlement.netPayable,
                totalPaid    = selected.totalNonVoidPayments,
                remaining    = remaining,
            )
        )
    }

    fun closeRecordPayment() {
        _paymentUiState.value = _paymentUiState.value.copy(
            recordPaymentState = RecordPaymentState.Idle
        )
    }

    fun onPaymentAmountChanged(raw: String) {
        val s = _paymentUiState.value.recordPaymentState as? RecordPaymentState.Active ?: return
        val amount = raw.trim().toIntOrNull()
        val error = when {
            amount == null && raw.isNotEmpty() -> "Enter a valid number"
            amount != null && amount <= 0      -> "Amount must be greater than zero"
            amount != null && amount > s.remaining ->
                "Amount ₹$amount exceeds the remaining balance of ₹${s.remaining}"
            else -> null
        }
        _paymentUiState.value = _paymentUiState.value.copy(
            recordPaymentState = s.copy(amountInput = raw, amountError = error)
        )
    }

    fun onPaymentDateChanged(date: String) {
        val s = _paymentUiState.value.recordPaymentState as? RecordPaymentState.Active ?: return
        _paymentUiState.value = _paymentUiState.value.copy(
            recordPaymentState = s.copy(date = date)
        )
    }

    fun onPaymentMethodChanged(method: PaymentMethod) {
        val s = _paymentUiState.value.recordPaymentState as? RecordPaymentState.Active ?: return
        _paymentUiState.value = _paymentUiState.value.copy(
            recordPaymentState = s.copy(method = method)
        )
    }

    fun onPaymentNoteChanged(note: String) {
        val s = _paymentUiState.value.recordPaymentState as? RecordPaymentState.Active ?: return
        _paymentUiState.value = _paymentUiState.value.copy(
            recordPaymentState = s.copy(note = note)
        )
    }

    /**
     * Records a payment against the selected settlement.
     *
     * P-4: re-queries total non-void payments at save time (not trusting in-memory cache)
     * to guard against any race. Blocks if amount + currentTotal > netPayable.
     */
    fun recordPayment() {
        val s = _paymentUiState.value.recordPaymentState as? RecordPaymentState.Active ?: return
        val amount = s.amountInput.trim().toIntOrNull() ?: return
        if (amount <= 0 || s.amountError != null) return

        val settlementId = s.settlementId
        val netPayable   = s.netPayable

        _paymentUiState.value = _paymentUiState.value.copy(
            recordPaymentState = RecordPaymentState.Saving
        )

        viewModelScope.launch(Dispatchers.IO) {
            // P-4: re-query at save time — never trust the in-memory cached total
            val currentTotal = db.paymentDao().getTotalNonVoidPayments(settlementId)
            if (currentTotal + amount > netPayable) {
                _paymentUiState.value = _paymentUiState.value.copy(
                    recordPaymentState = s.copy(
                        amountError = "Payment would exceed net payable ₹$netPayable. " +
                                "Remaining: ₹${netPayable - currentTotal}"
                    )
                )
                return@launch
            }

            try {
                db.paymentDao().insert(
                    Payment(
                        id           = UUID.randomUUID().toString(),
                        settlementId = settlementId,
                        paidOn       = s.date,
                        amount       = amount,
                        method       = s.method,
                        note         = s.note.takeIf { it.isNotBlank() },
                        isVoid       = false,
                        createdAt    = Instant.now().toString(),
                    )
                )
                // Refresh settlement list and re-select with updated history
                loadSettlementsAndReselect(settlementId)
                _paymentUiState.value = _paymentUiState.value.copy(
                    recordPaymentState = RecordPaymentState.Idle
                )
            } catch (e: Exception) {
                _paymentUiState.value = _paymentUiState.value.copy(
                    recordPaymentState = s.copy(amountError = "Save failed: ${e.message}")
                )
            }
        }
    }

    // ── Payments: void ────────────────────────────────────────────────────────

    fun openVoidDialog(paymentId: String, amount: Int) {
        _paymentUiState.value = _paymentUiState.value.copy(
            voidDialogState = VoidDialogState.Shown(paymentId, amount)
        )
    }

    fun onVoidReasonChanged(reason: String) {
        val s = _paymentUiState.value.voidDialogState as? VoidDialogState.Shown ?: return
        _paymentUiState.value = _paymentUiState.value.copy(
            voidDialogState = s.copy(reason = reason)
        )
    }

    fun closeVoidDialog() {
        _paymentUiState.value = _paymentUiState.value.copy(
            voidDialogState = VoidDialogState.Hidden
        )
    }

    /**
     * Voids a payment (P-5).
     * The amount is never changed — only is_void is set to true.
     * Both the voided row and any replacement row remain visible in history.
     */
    fun confirmVoidPayment() {
        val dialog = _paymentUiState.value.voidDialogState as? VoidDialogState.Shown ?: return
        if (dialog.reason.isBlank()) return     // reason is mandatory

        val selectedId = _paymentUiState.value.selectedSettlement?.settlement?.id ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.paymentDao().voidPayment(dialog.paymentId)
                loadSettlementsAndReselect(selectedId)
                _paymentUiState.value = _paymentUiState.value.copy(
                    voidDialogState = VoidDialogState.Hidden
                )
            } catch (e: Exception) {
                _paymentUiState.value = _paymentUiState.value.copy(
                    errorMessage = "Void failed: ${e.message}"
                )
            }
        }
    }

    // ── Adjustments: pending list ─────────────────────────────────────────────

    fun loadPendingAdjustments() {
        viewModelScope.launch(Dispatchers.IO) {
            _adjustmentUiState.value = _adjustmentUiState.value.copy(isLoading = true)
            val pending = db.adjustmentDao().getPendingAdjustments()
            _adjustmentUiState.value = _adjustmentUiState.value.copy(
                pendingAdjustments = pending,
                isLoading          = false,
            )
        }
    }

    // ── Adjustments: load a locked record for correction ─────────────────────

    fun loadLockedRecord(recordId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = db.dailyRecordDao().getById(recordId) ?: return@launch
            if (!record.isLocked) return@launch  // only locked records go through correction flow
            val settlement = db.settlementDao()
                .getByWorkerAndWeek(record.workerId, record.weekStartDate) ?: return@launch
            val workerName = db.workerDao().getById(record.workerId)?.name ?: ""

            _adjustmentUiState.value = _adjustmentUiState.value.copy(
                correctionState = CorrectionState.Editing(
                    target        = CorrectionTarget(record, workerName, settlement),
                    newAttendance = record.attendance,
                    newWage       = record.wage.toString(),
                    newOT         = record.overtimeAmount.toString(),
                )
            )
        }
    }

    fun onCorrectionAttendanceChanged(att: RoomAttendance) {
        val s = _adjustmentUiState.value.correctionState as? CorrectionState.Editing ?: return
        _adjustmentUiState.value = _adjustmentUiState.value.copy(
            correctionState = s.copy(newAttendance = att)
        )
    }

    fun onCorrectionWageChanged(raw: String) {
        val s = _adjustmentUiState.value.correctionState as? CorrectionState.Editing ?: return
        _adjustmentUiState.value = _adjustmentUiState.value.copy(
            correctionState = s.copy(newWage = raw)
        )
    }

    fun onCorrectionOTChanged(raw: String) {
        val s = _adjustmentUiState.value.correctionState as? CorrectionState.Editing ?: return
        _adjustmentUiState.value = _adjustmentUiState.value.copy(
            correctionState = s.copy(newOT = raw)
        )
    }

    fun onCorrectionReasonChanged(reason: String) {
        val s = _adjustmentUiState.value.correctionState as? CorrectionState.Editing ?: return
        _adjustmentUiState.value = _adjustmentUiState.value.copy(
            correctionState = s.copy(reason = reason)
        )
    }

    /** Opens confirmation dialog — preview the adjustment amount before committing. */
    fun requestCorrectionConfirm() {
        val s = _adjustmentUiState.value.correctionState as? CorrectionState.Editing ?: return
        val newWage = s.newWage.trim().toIntOrNull() ?: s.target.record.wage
        val newOT   = s.newOT.trim().toIntOrNull() ?: s.target.record.overtimeAmount

        viewModelScope.launch(Dispatchers.IO) {
            // Re-read all records for the week, then apply the proposed edit in-memory
            val allRecords = db.dailyRecordDao()
                .getRecordsForWorkerInWeek(s.target.record.workerId, s.target.record.weekStartDate)

            val proposedRecords = allRecords.map { r ->
                if (r.id == s.target.record.id) {
                    r.copy(attendance = s.newAttendance, wage = newWage, overtimeAmount = newOT)
                } else r
            }

            // Use imported adapter — same function SettlementViewModel uses, not a copy
            val wageRecords = proposedRecords.map { it.toWageRecord() }
            val recomputedGross = WageEngine.weeklyGrossEarnings(wageRecords)
            val adj = WageEngine.computeAdjustment(
                originalGrossEarnings  = s.target.settlement.grossEarnings,
                recomputedGrossEarnings = recomputedGross,
            )

            _adjustmentUiState.value = _adjustmentUiState.value.copy(
                correctionState = s.copy(
                    showConfirm             = true,
                    previewAdjustmentAmount = adj.amount,
                    previewSign             = adj.sign,
                )
            )
        }
    }

    fun dismissCorrectionConfirm() {
        val s = _adjustmentUiState.value.correctionState as? CorrectionState.Editing ?: return
        _adjustmentUiState.value = _adjustmentUiState.value.copy(
            correctionState = s.copy(showConfirm = false)
        )
    }

    /**
     * Applies the correction (J-1, J-2, J-3):
     *
     *  1. Re-reads all records from DB — not from in-memory snapshot.
     *  2. Applies the edit in-memory only to recompute gross.
     *  3. Calls WageEngine.computeAdjustment — no inline arithmetic.
     *  4. db.withTransaction { update(editedRecord) + insert(adjustment) } — atomic (Fix 3).
     *     The editedRecord has synced = false so the change reaches Supabase on next sync.
     *  5. The original weekly_settlement and payment rows are NEVER touched (J-3).
     */
    fun applyCorrection() {
        val editing = _adjustmentUiState.value.correctionState as? CorrectionState.Editing ?: return
        if (editing.reason.isBlank()) return   // mandatory per J-1

        val newWage = editing.newWage.trim().toIntOrNull() ?: editing.target.record.wage
        val newOT   = editing.newOT.trim().toIntOrNull() ?: editing.target.record.overtimeAmount

        _adjustmentUiState.value = _adjustmentUiState.value.copy(
            correctionState = CorrectionState.Saving()
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = Instant.now().toString()
                val original   = editing.target.record
                val settlement = editing.target.settlement

                // 1. Re-read all week's records fresh from the DB
                val allRecords = db.dailyRecordDao()
                    .getRecordsForWorkerInWeek(original.workerId, original.weekStartDate)

                // 2. Apply the proposed edit in-memory to recompute gross
                val proposedRecords = allRecords.map { r ->
                    if (r.id == original.id) {
                        r.copy(attendance = editing.newAttendance, wage = newWage, overtimeAmount = newOT)
                    } else r
                }

                // 3. Recompute using the real WageEngine + imported adapter (no inline arithmetic)
                val wageRecords     = proposedRecords.map { it.toWageRecord() }
                val recomputedGross = WageEngine.weeklyGrossEarnings(wageRecords)
                val adj = WageEngine.computeAdjustment(
                    originalGrossEarnings   = settlement.grossEarnings,
                    recomputedGrossEarnings = recomputedGross,
                )

                // Construct the edited record:
                //   synced = false — marks this record dirty for next sync pass (same pattern
                //   as the attendance screen's upsert path; required so the correction reaches
                //   Supabase on next sync)
                val editedRecord = original.copy(
                    attendance     = editing.newAttendance,
                    wage           = newWage,
                    overtimeAmount = newOT,
                    updatedAt      = now,
                    synced         = false,   // ← dirty flag; sync queue will pick this up
                )

                val adjustmentRow = Adjustment(
                    id           = UUID.randomUUID().toString(),
                    settlementId = settlement.id,
                    // Signed amount: positive = owed to worker, negative = worker overpaid (J-2)
                    amount       = adj.amount * adj.sign,
                    reason       = editing.reason,
                    createdAt    = now,
                )

                // 4. Atomic write: update record + insert adjustment in a single transaction.
                //    db.withTransaction (room-ktx) — no sequential DAO calls (Fix 3).
                //    J-3: settlement and payment rows are not touched here.
                db.withTransaction {
                    db.dailyRecordDao().update(editedRecord)
                    db.adjustmentDao().insert(adjustmentRow)
                }

                // Refresh pending adjustments list
                loadPendingAdjustments()

                _adjustmentUiState.value = _adjustmentUiState.value.copy(
                    correctionState = CorrectionState.Saved(adj.amount, adj.sign)
                )

            } catch (e: Exception) {
                _adjustmentUiState.value = _adjustmentUiState.value.copy(
                    correctionState = CorrectionState.Editing(
                        target        = editing.target,
                        newAttendance = editing.newAttendance,
                        newWage       = editing.newWage,
                        newOT         = editing.newOT,
                        reason        = editing.reason,
                    ),
                    errorMessage = "Correction failed: ${e.message}"
                )
            }
        }
    }

    fun resetCorrectionState() {
        _adjustmentUiState.value = _adjustmentUiState.value.copy(
            correctionState = CorrectionState.Idle
        )
    }

    fun clearError() {
        _paymentUiState.value = _paymentUiState.value.copy(errorMessage = null)
        _adjustmentUiState.value = _adjustmentUiState.value.copy(errorMessage = null)
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private suspend fun loadSettlementsAndReselect(settlementId: String) {
        val workerCache = db.workerDao().getAll().associateBy { it.id }
        val items = db.settlementDao().getAllSettlements().map { s ->
            val totalPaid = db.paymentDao().getTotalNonVoidPayments(s.id)
            val status    = WageEngine.settlementStatus(s.netPayable, totalPaid)
            SettlementListItem(
                settlement           = s,
                workerName           = workerCache[s.workerId]?.name ?: s.workerId,
                totalNonVoidPayments = totalPaid,
                status               = status,
            )
        }
        val history = db.paymentDao().getForSettlement(settlementId).map { p ->
            PaymentHistoryItem(payment = p, isVoidable = !p.isVoid)
        }
        val selected = items.find { it.settlement.id == settlementId }
        _paymentUiState.value = _paymentUiState.value.copy(
            settlements        = items,
            selectedSettlement = selected,
            paymentHistory     = history,
        )
    }

    // ─── Factory ──────────────────────────────────────────────────────────────

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PaymentViewModel(db) as T
    }
}
