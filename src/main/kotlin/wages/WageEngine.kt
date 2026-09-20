package wages

// ─────────────────────────────────────────────────────────────────────
//  Data types — plain in-memory structures, no persistence
// ─────────────────────────────────────────────────────────────────────

enum class Attendance { PRESENT, HALF_DAY, ABSENT }

/**
 * One row of daily work data. Fields mirror docs/spec.md §5.4 but carry
 * only the values the calculation engine needs — no IDs, timestamps, or
 * locking flags.
 */
data class DailyRecord(
    val wage: Int,              // rupees/day as recorded on this day's record
    val attendance: Attendance,
    val overtimeAmount: Int = 0 // default 0 per spec
)

enum class AdvanceTxnType { ADVANCE_GIVEN, DEDUCTION, WRITE_OFF }

data class AdvanceTxn(
    val type: AdvanceTxnType,
    val amount: Int             // always positive; type carries the sign (A-1)
)

// ─────────────────────────────────────────────────────────────────────
//  Result types
// ─────────────────────────────────────────────────────────────────────

data class DayTotalResult(
    val baseAmount: Int,
    val overtimeAmount: Int,
    val dayTotal: Int
)

/**
 * Result of [WageEngine.validateDeduction]. Sealed so callers get
 * exhaustive `when` checking without casting.
 */
sealed class DeductionValidation {
    data class Ok(val deduction: Int) : DeductionValidation()
    data class Rejected(val reason: String, val maxAllowed: Int) : DeductionValidation()
}

data class SettlementResult(
    val baseEarnings: Int,
    val overtimeEarnings: Int,
    val grossEarnings: Int,
    val advanceDeduction: Int,
    val netPayable: Int
)

enum class SettlementStatus { PENDING, PARTIALLY_PAID, PAID }

data class AdjustmentResult(
    val amount: Int,  // absolute value of the difference
    val sign: Int     // +1 = owed to worker, -1 = owed by worker, 0 = no change
)

// ─────────────────────────────────────────────────────────────────────
//  Wage calculation engine — pure functions, no side effects
// ─────────────────────────────────────────────────────────────────────

object WageEngine {

    // Default half-day fraction numerator / denominator.
    // Using integer fraction to avoid any floating-point arithmetic (G-1).
    // 0.5 = 1/2.
    private const val HALF_DAY_NUMERATOR = 1
    private const val HALF_DAY_DENOMINATOR = 2

    // ── C-1, C-2, C-3 ──────────────────────────────────────────────

    /**
     * Computes a single day's base amount, overtime, and day total.
     *
     * C-1: base amount from wage × attendance.
     * C-2: dayTotal = base + overtime (overtime paid regardless of attendance).
     * C-3: half-day fraction rounded to nearest rupee, exact halves rounded up.
     */
    fun dayTotal(record: DailyRecord): DayTotalResult {
        val baseAmount = when (record.attendance) {
            Attendance.PRESENT  -> record.wage
            Attendance.HALF_DAY -> halfDayWage(record.wage)
            Attendance.ABSENT   -> 0
        }
        val total = baseAmount + record.overtimeAmount
        return DayTotalResult(
            baseAmount = baseAmount,
            overtimeAmount = record.overtimeAmount,
            dayTotal = total
        )
    }

    /**
     * Half-day wage: wage × 1/2, rounded to nearest rupee with exact
     * halves rounded up (C-3).
     *
     * Integer-only arithmetic:
     *   wage * HALF_DAY_NUMERATOR  gives  wage/2  as an integer division
     *   problem. We need to round 0.5 up.
     *
     *   (wage * 1 + 2/2) / 2  using integer division doesn't work for the
     *   "round half up" rule universally. Instead:
     *
     *   result = (wage * HALF_DAY_NUMERATOR + HALF_DAY_DENOMINATOR / 2) / HALF_DAY_DENOMINATOR
     *
     *   For HALF_DAY_DENOMINATOR = 2:
     *     result = (wage * 1 + 1) / 2 = (wage + 1) / 2
     *
     *   wage=900: (900+1)/2 = 450  ✓ (exact, 450.0)
     *   wage=875: (875+1)/2 = 438  ✓ (437.5 rounds up to 438)
     *   wage=877: (877+1)/2 = 439  ✓ (438.5 rounds up to 439)
     *   wage=876: (876+1)/2 = 438  — but 876/2 = 438.0 exactly, so 438 ✓
     *
     * Wait — let me verify more carefully. "Round to nearest, halves up" means:
     *   floor(x + 0.5)  for non-negative x.
     *
     * For wage W with denominator D=2, numerator N=1:
     *   exact = W * N / D = W / 2
     *   rounded = floor(W/2 + 0.5) = floor((W + 1) / 2) = (W + 1) / 2 in integer division
     *
     * This is correct for all non-negative W.
     */
    internal fun halfDayWage(wage: Int): Int {
        // "Round half up" = floor(value + 0.5)
        // value = wage * HALF_DAY_NUMERATOR / HALF_DAY_DENOMINATOR
        // floor(wage * N / D + 0.5) = floor((wage * N * 2 + D) / (D * 2))
        //                           = (wage * N * 2 + D) / (D * 2)   [integer division]
        //
        // For N=1, D=2: (wage * 2 + 2) / 4 = (wage + 1) / 2
        return (wage * HALF_DAY_NUMERATOR * 2 + HALF_DAY_DENOMINATOR) / (HALF_DAY_DENOMINATOR * 2)
    }

    // ── C-4 ─────────────────────────────────────────────────────────

    /**
     * Base earnings for a week = sum of base amounts of all daily records
     * with that week_start_date (C-4).
     *
     * A week with no records yields 0 (C-8).
     */
    fun weeklyBaseEarnings(records: List<DailyRecord>): Int =
        records.sumOf { dayTotal(it).baseAmount }

    // ── C-5 ─────────────────────────────────────────────────────────

    /**
     * Overtime earnings for a week = sum of overtime_amount across those
     * records (C-5).
     */
    fun weeklyOvertimeEarnings(records: List<DailyRecord>): Int =
        records.sumOf { it.overtimeAmount }

    // ── C-6 ─────────────────────────────────────────────────────────

    /**
     * Gross earnings = base earnings + overtime earnings (C-6).
     */
    fun weeklyGrossEarnings(records: List<DailyRecord>): Int =
        weeklyBaseEarnings(records) + weeklyOvertimeEarnings(records)

    // ── A-1 ─────────────────────────────────────────────────────────

    /**
     * Outstanding advance balance, derived from the transaction list (A-1).
     *
     *   balance = SUM(ADVANCE_GIVEN) − SUM(DEDUCTION) − SUM(WRITE_OFF)
     */
    fun advanceBalance(transactions: List<AdvanceTxn>): Int =
        transactions.sumOf { txn ->
            when (txn.type) {
                AdvanceTxnType.ADVANCE_GIVEN -> txn.amount
                AdvanceTxnType.DEDUCTION     -> -txn.amount
                AdvanceTxnType.WRITE_OFF     -> -txn.amount
            }
        }

    // ── A-5, A-6 ────────────────────────────────────────────────────

    /**
     * Validates a proposed advance deduction against balance and gross
     * earnings constraints.
     *
     * A-5: deduction must not exceed outstanding balance.
     * A-6: deduction must not exceed gross earnings (net payable is never negative).
     *
     * Returns [DeductionValidation.Ok] or [DeductionValidation.Rejected].
     */
    fun validateDeduction(
        balance: Int,
        grossEarnings: Int,
        requestedDeduction: Int
    ): DeductionValidation {
        // A-5: deduction must not exceed balance
        if (requestedDeduction > balance) {
            return DeductionValidation.Rejected(
                reason = "Deduction exceeds outstanding advance balance",
                maxAllowed = balance
            )
        }
        // A-6: deduction must not exceed gross earnings (net payable never negative)
        if (requestedDeduction > grossEarnings) {
            return DeductionValidation.Rejected(
                reason = "Deduction exceeds gross earnings; net payable would be negative",
                maxAllowed = grossEarnings
            )
        }
        return DeductionValidation.Ok(deduction = requestedDeduction)
    }

    // ── S-5 ─────────────────────────────────────────────────────────

    /**
     * Computes a settlement snapshot from daily records and a chosen
     * advance deduction.
     *
     * S-5: net_payable = gross_earnings − advance_deduction, never negative.
     *
     * The caller is responsible for having validated the deduction via
     * [validateDeduction] first. This function does not re-validate, but
     * it does enforce the non-negative net payable invariant by clamping.
     */
    fun computeSettlement(
        records: List<DailyRecord>,
        advanceDeduction: Int
    ): SettlementResult {
        val base = weeklyBaseEarnings(records)
        val overtime = weeklyOvertimeEarnings(records)
        val gross = base + overtime
        // S-5: net payable is never negative
        val net = maxOf(0, gross - advanceDeduction)
        return SettlementResult(
            baseEarnings = base,
            overtimeEarnings = overtime,
            grossEarnings = gross,
            advanceDeduction = advanceDeduction,
            netPayable = net
        )
    }

    // ── P-3 ─────────────────────────────────────────────────────────

    /**
     * Derives settlement status from net payable and total non-void
     * payments (P-3).
     *
     * | paid = 0              | PENDING        |
     * | 0 < paid < netPayable | PARTIALLY_PAID |
     * | paid ≥ netPayable     | PAID           |
     */
    fun settlementStatus(netPayable: Int, totalNonVoidPayments: Int): SettlementStatus =
        when {
            totalNonVoidPayments <= 0      -> SettlementStatus.PENDING
            totalNonVoidPayments < netPayable -> SettlementStatus.PARTIALLY_PAID
            else                              -> SettlementStatus.PAID
        }

    // ── J-2 ─────────────────────────────────────────────────────────

    /**
     * Computes an adjustment row after a locked daily record is edited.
     *
     * J-2: adjustment.amount = recomputed_gross − original settlement gross.
     * Positive = worker is owed more, negative = worker was overpaid.
     *
     * Returns [AdjustmentResult] with the absolute amount and sign (+1, -1, or 0).
     */
    fun computeAdjustment(
        originalGrossEarnings: Int,
        recomputedGrossEarnings: Int
    ): AdjustmentResult {
        val diff = recomputedGrossEarnings - originalGrossEarnings
        return AdjustmentResult(
            amount = kotlin.math.abs(diff),
            sign = diff.compareTo(0)  // +1, 0, or -1
        )
    }
}
