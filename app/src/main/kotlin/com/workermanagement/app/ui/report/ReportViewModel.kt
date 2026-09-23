package com.workermanagement.app.ui.report

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.workermanagement.app.ui.settlement.toWageRecord
import com.workermanagement.data.dao.WorkerBalance
import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.Adjustment
import com.workermanagement.data.entity.DailyRecord
import com.workermanagement.data.entity.Site
import com.workermanagement.data.entity.Worker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wages.WageEngine
import java.io.File
import java.time.LocalDate

// ─── Report type enum ─────────────────────────────────────────────────────────

enum class ReportType(val label: String) {
    WEEKLY_ATTENDANCE("Weekly Attendance"),
    WEEKLY_WAGE("Weekly Wage"),
    PAYMENT_STATUS("Payment Status"),
    OUTSTANDING_ADVANCES("Outstanding Advances"),
    SITE_ATTENDANCE("Site-wise Attendance"),
    SITE_WAGE_COST("Site-wise Wage Cost"),
    WORKER_HISTORY("Worker History"),
    PENDING_ADJUSTMENTS("Pending Adjustments"),
}

// ─── Report row data classes ──────────────────────────────────────────────────

data class WeeklyAttendanceRow(
    val workerName: String,
    val workerCode: String,
    val daysWorked: Int,
)

data class WeeklyWageRow(
    val workerName: String,
    val workerCode: String,
    val baseEarnings: Int,
    val overtimeEarnings: Int,
    val grossEarnings: Int,
    val advanceDeduction: Int,
    val netPayable: Int,
)

data class PaymentStatusRow(
    val workerName: String,
    val workerCode: String,
    val netPayable: Int,
    val totalPaid: Int,
    val remaining: Int,
    val status: String,    // derived via WageEngine.settlementStatus
)

data class OutstandingAdvanceRow(
    val workerName: String,
    val workerCode: String,
    val balance: Int,
)

data class SiteAttendanceRow(
    val siteName: String,
    val date: String,
    val headcount: Int,
)

data class SiteWageCostRow(
    val siteName: String,
    val totalCost: Int,
)

data class WorkerHistoryRow(
    val date: String,
    val siteName: String,
    val roleName: String,
    val wage: Int,
    val attendance: String,
    val overtimeAmount: Int,
    val dayTotal: Int,
)

data class PendingAdjustmentRow(
    val workerName: String,
    val workerCode: String,
    val weekStartDate: String,
    val amount: Int,
    val reason: String,
)

// ─── UI state ─────────────────────────────────────────────────────────────────

data class ReportUiState(
    val selectedReport: ReportType = ReportType.WEEKLY_ATTENDANCE,
    // Filter inputs
    val weekStartDate: String = "",
    val dateFrom: String = "",
    val dateTo: String = "",
    val selectedWorkerId: String? = null,
    // Data
    val workers: List<Worker> = emptyList(),
    val availableWeeks: List<String> = emptyList(),
    // Report results — only one is non-null at a time
    val weeklyAttendance: List<WeeklyAttendanceRow>? = null,
    val weeklyWage: List<WeeklyWageRow>? = null,
    val paymentStatus: List<PaymentStatusRow>? = null,
    val outstandingAdvances: List<OutstandingAdvanceRow>? = null,
    val siteAttendance: List<SiteAttendanceRow>? = null,
    val siteWageCost: List<SiteWageCostRow>? = null,
    val workerHistory: List<WorkerHistoryRow>? = null,
    val pendingAdjustments: List<PendingAdjustmentRow>? = null,
    // State flags
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val csvReady: String? = null,     // CSV content string ready for export
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ReportViewModel(
    private val db: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private var workerCache: Map<String, Worker> = emptyMap()
    private var siteCache: Map<String, Site> = emptyMap()
    // role lookup: roleId → roleName
    private var roleCache: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch(ioDispatcher) {
            val workers = db.workerDao().getAll()
            workerCache = workers.associateBy { it.id }
            siteCache = db.siteDao().getAll().associateBy { it.id }
            roleCache = db.roleDao().getAll().associate { it.id to it.name }

            // Load available weeks from daily_record
            val records = workers.flatMap { w ->
                db.dailyRecordDao().getRecordsForWorkerInRange(
                    w.id, "2000-01-01", LocalDate.now().toString()
                )
            }
            val weeks = records.map { it.weekStartDate }.distinct().sortedDescending()

            // Default date range: last 7 days
            val today = LocalDate.now()
            val weekAgo = today.minusDays(6)

            _uiState.value = _uiState.value.copy(
                workers = workers,
                availableWeeks = weeks,
                weekStartDate = weeks.firstOrNull() ?: "",
                dateFrom = weekAgo.toString(),
                dateTo = today.toString(),
            )
        }
    }

    // ── Selection handlers ────────────────────────────────────────────────────

    fun selectReport(type: ReportType) {
        _uiState.value = _uiState.value.copy(
            selectedReport = type,
            // Clear previous results
            weeklyAttendance = null, weeklyWage = null, paymentStatus = null,
            outstandingAdvances = null, siteAttendance = null, siteWageCost = null,
            workerHistory = null, pendingAdjustments = null,
            csvReady = null, errorMessage = null,
        )
    }

    fun setWeekStartDate(date: String) {
        _uiState.value = _uiState.value.copy(weekStartDate = date)
    }

    fun setDateFrom(date: String) {
        _uiState.value = _uiState.value.copy(dateFrom = date)
    }

    fun setDateTo(date: String) {
        _uiState.value = _uiState.value.copy(dateTo = date)
    }

    fun setSelectedWorker(workerId: String?) {
        _uiState.value = _uiState.value.copy(selectedWorkerId = workerId)
    }

    // ── Generate report ───────────────────────────────────────────────────────

    fun generateReport() {
        val s = _uiState.value
        _uiState.value = s.copy(isLoading = true, errorMessage = null, csvReady = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                when (s.selectedReport) {
                    ReportType.WEEKLY_ATTENDANCE    -> loadWeeklyAttendance(s.weekStartDate)
                    ReportType.WEEKLY_WAGE          -> loadWeeklyWage(s.weekStartDate)
                    ReportType.PAYMENT_STATUS       -> loadPaymentStatus(s.weekStartDate)
                    ReportType.OUTSTANDING_ADVANCES -> loadOutstandingAdvances()
                    ReportType.SITE_ATTENDANCE      -> loadSiteAttendance(s.dateFrom, s.dateTo)
                    ReportType.SITE_WAGE_COST       -> loadSiteWageCost(s.dateFrom, s.dateTo)
                    ReportType.WORKER_HISTORY       -> loadWorkerHistory(s.selectedWorkerId, s.dateFrom, s.dateTo)
                    ReportType.PENDING_ADJUSTMENTS  -> loadPendingAdjustments()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to generate report: ${e.message}",
                )
            }
        }
    }

    // ── R1: Weekly attendance ─────────────────────────────────────────────────

    private fun loadWeeklyAttendance(weekStartDate: String) {
        val data = db.dailyRecordDao().getDaysWorkedInWeek(weekStartDate)
        val rows = data.mapNotNull { wdw ->
            val worker = workerCache[wdw.worker_id] ?: return@mapNotNull null
            WeeklyAttendanceRow(
                workerName = worker.name,
                workerCode = worker.code,
                daysWorked = wdw.daysWorked,
            )
        }.sortedBy { it.workerName }

        _uiState.value = _uiState.value.copy(
            weeklyAttendance = rows, isLoading = false,
        )
    }

    // ── R2: Weekly wage ───────────────────────────────────────────────────────

    private fun loadWeeklyWage(weekStartDate: String) {
        val settlements = db.settlementDao().getSettlementsForWeek(weekStartDate)
        val rows = settlements.mapNotNull { s ->
            val worker = workerCache[s.workerId] ?: return@mapNotNull null
            WeeklyWageRow(
                workerName = worker.name,
                workerCode = worker.code,
                baseEarnings = s.baseEarnings,
                overtimeEarnings = s.overtimeEarnings,
                grossEarnings = s.grossEarnings,
                advanceDeduction = s.advanceDeduction,
                netPayable = s.netPayable,
            )
        }.sortedBy { it.workerName }

        _uiState.value = _uiState.value.copy(
            weeklyWage = rows, isLoading = false,
        )
    }

    // ── R3: Payment status — derived live, no stored status column ─────────

    /**
     * Payment status report: who has not been paid for a given week.
     * Status is derived live via WageEngine.settlementStatus() — never read
     * from the stored column (same pattern as PaymentViewModel.loadSettlements).
     */
    private fun loadPaymentStatus(weekStartDate: String) {
        val settlements = db.settlementDao().getSettlementsForWeek(weekStartDate)
        val rows = settlements.mapNotNull { s ->
            val worker = workerCache[s.workerId] ?: return@mapNotNull null
            val totalPaid = db.paymentDao().getTotalNonVoidPayments(s.id)
            // P-3: derive status via WageEngine — same call PaymentViewModel uses
            val status = WageEngine.settlementStatus(s.netPayable, totalPaid)

            PaymentStatusRow(
                workerName = worker.name,
                workerCode = worker.code,
                netPayable = s.netPayable,
                totalPaid = totalPaid,
                remaining = s.netPayable - totalPaid,
                status = status.name,
            )
        }.sortedBy { it.workerName }

        _uiState.value = _uiState.value.copy(
            paymentStatus = rows, isLoading = false,
        )
    }

    // ── R4: Outstanding advances ──────────────────────────────────────────────

    private fun loadOutstandingAdvances() {
        val balances: List<WorkerBalance> = db.advanceTxnDao().getOutstandingBalances()
        val rows = balances.mapNotNull { wb ->
            val worker = workerCache[wb.worker_id] ?: return@mapNotNull null
            OutstandingAdvanceRow(
                workerName = worker.name,
                workerCode = worker.code,
                balance = wb.balance,
            )
        }

        _uiState.value = _uiState.value.copy(
            outstandingAdvances = rows, isLoading = false,
        )
    }

    // ── R5: Site-wise attendance ───────────────────────────────────────────────

    private fun loadSiteAttendance(from: String, to: String) {
        val data = db.dailyRecordDao().getHeadcountBySiteInRange(from, to)
        val rows = data.map { sdh ->
            SiteAttendanceRow(
                siteName = siteCache[sdh.site_id]?.name ?: sdh.site_id,
                date = sdh.work_date,
                headcount = sdh.headcount,
            )
        }

        _uiState.value = _uiState.value.copy(
            siteAttendance = rows, isLoading = false,
        )
    }

    // ── R6: Site-wise wage cost — Kotlin-side WageEngine computation ─────────

    /**
     * Fetch raw daily_record rows for the date range, map through toWageRecord(),
     * compute per-row totals via WageEngine.dayTotal(), group by site_id.
     *
     * This is the corrected approach: no SQL arithmetic (G-1), uses the tested
     * WageEngine path (C-1, C-2, C-3), and each record's own stored wage is
     * used — not any worker's current default (D-2).
     */
    private fun loadSiteWageCost(from: String, to: String) {
        val records = db.dailyRecordDao().getRecordsInDateRange(from, to)

        val rows = records
            .groupBy { it.siteId }
            .map { (siteId, siteRecords) ->
                val totalCost = siteRecords.sumOf { record ->
                    // Same adapter used by SettlementViewModel — one conversion layer.
                    // Uses record.wage (the stored copy from that day, D-2), not
                    // worker.defaultWage. This is inherent in the DailyRecord entity:
                    // toWageRecord() reads record.wage, record.attendance, record.overtimeAmount.
                    val wageRecord = record.toWageRecord()
                    WageEngine.dayTotal(wageRecord).dayTotal
                }
                SiteWageCostRow(
                    siteName = siteCache[siteId]?.name ?: siteId,
                    totalCost = totalCost,
                )
            }
            .sortedByDescending { it.totalCost }

        _uiState.value = _uiState.value.copy(
            siteWageCost = rows, isLoading = false,
        )
    }

    // ── R7: Worker history ────────────────────────────────────────────────────

    private fun loadWorkerHistory(workerId: String?, from: String, to: String) {
        if (workerId == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Please select a worker",
            )
            return
        }
        val records = db.dailyRecordDao().getRecordsForWorkerInRange(workerId, from, to)
        val rows = records.map { r ->
            val wageRecord = r.toWageRecord()
            val dayResult = WageEngine.dayTotal(wageRecord)
            WorkerHistoryRow(
                date = r.workDate,
                siteName = siteCache[r.siteId]?.name ?: r.siteId,
                roleName = roleCache[r.roleId] ?: r.roleId,
                wage = r.wage,
                attendance = r.attendance.name,
                overtimeAmount = r.overtimeAmount,
                dayTotal = dayResult.dayTotal,
            )
        }

        _uiState.value = _uiState.value.copy(
            workerHistory = rows, isLoading = false,
        )
    }

    // ── R8: Pending adjustments ───────────────────────────────────────────────

    private fun loadPendingAdjustments() {
        val adjustments: List<Adjustment> = db.adjustmentDao().getPendingAdjustments()
        val rows = adjustments.mapNotNull { adj ->
            val settlement = db.settlementDao().getById(adj.settlementId) ?: return@mapNotNull null
            val worker = workerCache[settlement.workerId] ?: return@mapNotNull null
            PendingAdjustmentRow(
                workerName = worker.name,
                workerCode = worker.code,
                weekStartDate = settlement.weekStartDate,
                amount = adj.amount,
                reason = adj.reason,
            )
        }

        _uiState.value = _uiState.value.copy(
            pendingAdjustments = rows, isLoading = false,
        )
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    fun exportCsv() {
        val s = _uiState.value
        val csv: String? = when (s.selectedReport) {
            ReportType.WEEKLY_ATTENDANCE -> s.weeklyAttendance?.let {
                CsvExporter.toCsv(
                    listOf("Worker", "Code", "Days Worked"), it
                ) { r -> listOf(r.workerName, r.workerCode, r.daysWorked.toString()) }
            }
            ReportType.WEEKLY_WAGE -> s.weeklyWage?.let {
                CsvExporter.toCsv(
                    listOf("Worker", "Code", "Base", "Overtime", "Gross", "Deduction", "Net"), it
                ) { r -> listOf(r.workerName, r.workerCode, r.baseEarnings.toString(),
                    r.overtimeEarnings.toString(), r.grossEarnings.toString(),
                    r.advanceDeduction.toString(), r.netPayable.toString()) }
            }
            ReportType.PAYMENT_STATUS -> s.paymentStatus?.let {
                CsvExporter.toCsv(
                    listOf("Worker", "Code", "Net Payable", "Paid", "Remaining", "Status"), it
                ) { r -> listOf(r.workerName, r.workerCode, r.netPayable.toString(),
                    r.totalPaid.toString(), r.remaining.toString(), r.status) }
            }
            ReportType.OUTSTANDING_ADVANCES -> s.outstandingAdvances?.let {
                CsvExporter.toCsv(
                    listOf("Worker", "Code", "Balance"), it
                ) { r -> listOf(r.workerName, r.workerCode, r.balance.toString()) }
            }
            ReportType.SITE_ATTENDANCE -> s.siteAttendance?.let {
                CsvExporter.toCsv(
                    listOf("Site", "Date", "Headcount"), it
                ) { r -> listOf(r.siteName, r.date, r.headcount.toString()) }
            }
            ReportType.SITE_WAGE_COST -> s.siteWageCost?.let {
                CsvExporter.toCsv(
                    listOf("Site", "Total Wage Cost"), it
                ) { r -> listOf(r.siteName, r.totalCost.toString()) }
            }
            ReportType.WORKER_HISTORY -> s.workerHistory?.let {
                CsvExporter.toCsv(
                    listOf("Date", "Site", "Role", "Wage", "Attendance", "OT", "Day Total"), it
                ) { r -> listOf(r.date, r.siteName, r.roleName, r.wage.toString(),
                    r.attendance, r.overtimeAmount.toString(), r.dayTotal.toString()) }
            }
            ReportType.PENDING_ADJUSTMENTS -> s.pendingAdjustments?.let {
                CsvExporter.toCsv(
                    listOf("Worker", "Code", "Week", "Amount", "Reason"), it
                ) { r -> listOf(r.workerName, r.workerCode, r.weekStartDate,
                    r.amount.toString(), r.reason) }
            }
        }

        _uiState.value = _uiState.value.copy(csvReady = csv)
    }

    /**
     * Writes CSV to cache dir and launches the Android share sheet.
     */
    fun shareCsv(context: Context) {
        val csv = _uiState.value.csvReady ?: return
        val reportName = _uiState.value.selectedReport.label.replace(" ", "_").lowercase()
        val fileName = "${reportName}_${LocalDate.now()}.csv"

        val dir = File(context.cacheDir, "reports")
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(csv)

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export $reportName"))
    }

    fun clearCsv() {
        _uiState.value = _uiState.value.copy(csvReady = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ─── Factory ──────────────────────────────────────────────────────────────

    class Factory(private val db: AppDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReportViewModel(db) as T
    }
}
