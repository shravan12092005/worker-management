# Business Rules

These rules are **normative**. Each is written to be directly testable. Where this document
and `docs/spec.md` appear to disagree, this document wins.

Conventions used below:

- All money is **integer rupees**. Floating-point arithmetic for currency is forbidden
  anywhere in the codebase.
- MUST / MUST NOT are absolute. SHOULD is a strong default that may be overridden by a
  documented decision.
- Rule IDs are stable. Reference them in test names, e.g. `test_C3_half_day_rounds_down`.

---

## G — General

**G-1.** Money is stored and computed as integer rupees. Any intermediate division MUST round
using the rule in C-3; no other rounding is permitted.

**G-2.** No record is ever hard-deleted through the application UI. Entities are deactivated
(`is_active = false`), payments are voided (`is_void = true`), and corrections are expressed
as new rows.

**G-3.** Every change to a wage, attendance value, overtime amount, site, role, advance,
payment or adjustment MUST write an `audit_log` row recording the old value, the new value,
the user and the timestamp.

**G-4.** All IDs are UUIDs generated on the device, so that records created offline require no
renumbering when they sync.

---

## W — Weeks

**W-1.** A week starts on the configured `week_start_day` (default **Monday**) and runs seven
days to the following Sunday inclusive. Every `daily_record` stores the `week_start_date` of
the week containing its `work_date`, computed at insert time.

**W-2.** A week is identified by its `week_start_date` alone. Week numbers, month boundaries
and year boundaries MUST NOT be used to group work into payment periods.

**W-3.** A week that spans two calendar months belongs entirely to one payment period.
*Example:* work on 29 Sep – 5 Oct with `week_start_date = 29 Sep` forms a single settlement;
it MUST NOT be split into a September part and an October part.

**W-4.** Changing `week_start_day` in settings affects only weeks that have no `daily_record`
rows yet. The application MUST refuse the change if any unlocked daily records exist for the
current or a future week.

---

## D — Daily records

**D-1.** A worker MUST have at most one `daily_record` per calendar date. This is enforced by
a unique database index on (`worker_id`, `work_date`), not only in application code.

**D-2.** A `daily_record` stores a **copy** of the wage, role and site that applied on that
day. These values MUST NOT be read from the worker profile at calculation, report or display
time.

**D-3.** When a new `daily_record` is created, `role_id` and `wage` are pre-filled from the
worker's `default_role_id` and `default_wage`, and MAY be changed before saving. Editing them
MUST NOT change the worker profile.

**D-4.** Assignment and attendance are independent. A record may have a `site_id` and
`attendance = ABSENT`. Assigning a worker to a site MUST NOT set attendance to `PRESENT`.

**D-5.** A `daily_record` MUST NOT be created for a `work_date` in the future. Today's date is
permitted.

**D-6.** A `daily_record` MUST NOT be created for a `work_date` before the worker's
`joining_date`.

**D-7.** A `daily_record` MAY be created for an inactive worker or an inactive site only for a
date on which that worker or site was active. Newly created records MUST reference active
entities.

**D-8.** Changing the `site_id` of an existing record is permitted while the record is
unlocked, and is audit-logged. The site on a given day is whatever the record currently says.

**D-9.** V1 does not support a worker working at two sites on one day. If this need arises,
it is implemented by relaxing the D-1 index — not by encoding two sites in one row.

---

## C — Wage calculation

**C-1.** A day's **base amount** is derived from the record's own `wage` and `attendance`:

| `attendance` | Base amount |
|---|---|
| `PRESENT` | `wage` |
| `HALF_DAY` | `wage × half_day_fraction` (default 0.5) |
| `ABSENT` | `0` |

**C-2.** A day's **total** is `base amount + overtime_amount`. Overtime is paid regardless of
`attendance`, since it represents work actually performed; the UI SHOULD warn if overtime is
entered on an `ABSENT` day.

**C-3.** Where the half-day calculation produces a fraction, the result is **rounded to the
nearest rupee, with exact halves rounded up**.
*Example:* wage ₹875, half day → ₹437.50 → **₹438**.

**C-4.** **Base earnings** for a week = the sum of the base amounts of all `daily_record` rows
with that `week_start_date`.

**C-5.** **Overtime earnings** for a week = the sum of `overtime_amount` across those rows.

**C-6.** **Gross earnings** = base earnings + overtime earnings.

**C-7.** Each day is evaluated with its own stored wage. Different wages within one week are
normal and require no special handling.

**C-8.** A week with no `daily_record` rows has gross earnings of ₹0. This is a valid week,
not an error.

**C-9.** A worker who joins mid-week is paid only for the days that have records. Absence of a
record is not absence from work; it produces no earnings and no `ABSENT` marker.

---

## R — Roles and wages

**R-1.** `worker.default_wage` and `worker.default_role_id` are templates used only to
pre-fill new daily records.

**R-2.** Changing `worker.default_wage` MUST NOT alter any existing `daily_record`, settlement
or payment. It takes effect only for records created afterwards.

**R-3.** A permanent wage change is simply an edit to `worker.default_wage`, audit-logged. No
separate "wage history" table is required — history lives in the daily records.

**R-4.** A wage change for one day is an edit to that day's `daily_record.wage`.

**R-5.** A wage change for one week is applied to that week's daily records. A bulk action
("apply wage ₹X to this worker for this week") MAY be provided; it MUST write each day's
record individually and MUST NOT create any period-scoped wage entity.

**R-6.** A temporary role change is an edit to `daily_record.role_id` and MUST NOT change
`worker.default_role_id`.

**R-7.** Role and wage are edited independently. Changing the role MAY offer to update the
wage, but MUST NOT change it automatically.

---

## A — Advances

**A-1.** The outstanding advance balance is always **derived**, never stored:

```
balance = SUM(ADVANCE_GIVEN) − SUM(DEDUCTION) − SUM(WRITE_OFF)
```

A stored balance column MUST NOT exist on `worker` or anywhere else.

**A-2.** A worker with no advance transactions has a balance of ₹0 and MUST NOT appear in the
outstanding-advances report.

**A-3.** Multiple advances may be given in one week; they accumulate.
*Example:* ₹2,000 on Monday + ₹1,000 on Thursday → balance ₹3,000.

**A-4.** The deduction applied in a given week is entered manually by the contractor and
defaults to ₹0. There is no automatic recovery schedule in V1.

**A-5.** The deduction MUST NOT exceed the outstanding balance.

**A-6.** The deduction MUST NOT exceed the week's gross earnings. If the contractor enters a
larger amount, the application blocks the finalization, shows the shortfall, and offers to cap
the deduction at gross earnings. **Net payable is never negative.**

**A-7.** Every deduction creates an `advance_txn` of type `DEDUCTION` linked to the settlement
that produced it. Reversing a settlement reverses its deduction transaction.

**A-8.** When the balance reaches ₹0, the worker stops appearing in the outstanding report.
The transaction history remains.

**A-9.** An advance may be forgiven via a `WRITE_OFF` transaction, which requires a note.

---

## S — Settlement and finalization

**S-1.** Finalizing a week for a worker creates exactly one `weekly_settlement` row, which
**snapshots** `base_earnings`, `overtime_earnings`, `gross_earnings`, `advance_deduction` and
`net_payable` as of that moment.

**S-2.** A worker may have at most one settlement per week (unique index on `worker_id`,
`week_start_date`).

**S-3.** Finalization sets `is_locked = true` on every `daily_record` in that week for that
worker.

**S-4.** Snapshot values on a `weekly_settlement` MUST NOT be recalculated or overwritten
after finalization, for any reason.

**S-5.** `net_payable = gross_earnings − advance_deduction`, and is never negative (see A-6).

**S-6.** A settlement may be **reversed** before any payment is recorded against it. Reversal
deletes the settlement, reverses its deduction transaction, and unlocks the daily records.
Once any non-void payment exists, reversal is forbidden and the adjustment path (§J) applies.

---

## P — Payments

**P-1.** Payments are recorded against a `weekly_settlement`, never against a week directly.

**P-2.** Multiple payments may be recorded against one settlement. The amount paid is the sum
of non-void payments.

**P-3.** Settlement status is derived, not stored independently:

| Condition | Status |
|---|---|
| `paid = 0` | `PENDING` |
| `0 < paid < net_payable` | `PARTIALLY_PAID` |
| `paid ≥ net_payable` | `PAID` |

**P-4.** Total non-void payments MUST NOT exceed `net_payable` plus any settled adjustments.
Overpayment is blocked with a clear message.

**P-5.** A payment amount is **never edited**. A wrong payment is voided (`is_void = true`,
reason required) and a new payment row is created. Both rows remain visible in history.

**P-6.** A payment is permanently associated with the week of its settlement, regardless of
the calendar date on which it was made.

---

## J — Adjustments (corrections after finalization)

**J-1.** Editing a locked `daily_record` is permitted but requires explicit confirmation and a
reason.

**J-2.** After such an edit, the system recomputes what the week's gross earnings would now be
and creates an `adjustment` row:

```
adjustment.amount = recomputed_gross − settlement.gross_earnings
```

Positive means the worker is owed more; negative means the worker was overpaid.

**J-3.** The original `weekly_settlement` and its `payment` rows MUST NOT be modified by this
process.

**J-4.** Multiple edits to the same locked week produce multiple adjustment rows. They are not
merged.

**J-5.** An unsettled adjustment appears in the "Pending adjustments" report and is offered for
inclusion in the worker's next settlement. When included, `settled_in_settlement_id` is set.

**J-6.** Worked example:

```
Originally finalized:  gross ₹5,000   deduction ₹0   net ₹5,000   paid ₹5,000
Correction: Thursday changed from ABSENT to PRESENT at ₹900 → recomputed gross ₹5,900
Adjustment created:    +₹900, reason "Thursday attendance corrected"
Next week's settlement: gross ₹5,400 + adjustment ₹900 = ₹6,300 payable
```

---

## M — Workers and sites

**M-1.** A returning worker reuses their existing `worker` row. The application MUST warn on
any new worker whose name closely matches, or whose phone exactly matches, an existing worker
— including inactive ones.

**M-2.** `phone` is optional. A worker with no phone number MUST be creatable.

**M-3.** Deactivating a worker or a site MUST NOT delete or alter any historical record.

**M-4.** An inactive worker does not appear in attendance lists by default but remains fully
visible in reports and history.

**M-5.** `worker.code` is unique and immutable once created.

**M-6.** A site with no workers on a given day shows a headcount of 0. This is not an error
state and does not imply the site is inactive.

---

## N — Offline and sync

**N-1.** Viewing workers, viewing sites, marking attendance, editing role/wage for a day, and
recording overtime MUST all work with no network connection.

**N-2.** A write is acknowledged to the user only after it is committed to the local database.
An app killed mid-edit leaves either a complete record or no record — never a partial one.

**N-3.** Sync status displayed to the user MUST reflect reality. A record that has not reached
the server MUST NOT be shown as synced.

**N-4.** Sync failure is surfaced visibly, and a persistent warning appears if no successful
sync has occurred in 24 hours.

**N-5.** Conflicts resolve by last-write-wins on `updated_at`, and every conflict is logged
with both versions.

---

## Test vectors

These must appear as automated tests against the calculation engine.

### T-1 — Mixed sites, roles and wages (spec §10 example)

| Day | Site | Role | Wage | Attendance | OT |
|---|---|---|---|---|---|
| Mon | A | Mason | 900 | PRESENT | 0 |
| Tue | A | Mason | 900 | PRESENT | 0 |
| Wed | B | Mason | 900 | PRESENT | 0 |
| Thu | B | Mason | 900 | PRESENT | 300 |
| Fri | C | Helper | 700 | PRESENT | 0 |
| Sat | C | Helper | 700 | PRESENT | 0 |

Expected: base **5,000**, overtime **300**, gross **5,300**.
With deduction 1,000 against a balance of 3,000: net **4,300**, remaining balance **2,000**.

### T-2 — Half day

Wage 900, `HALF_DAY` → 450. Weekly attendance P P HD P A P → 4.5 days.

### T-3 — Half-day rounding (C-3)

Wage 875, `HALF_DAY` → **438**, not 437.

### T-4 — Different wages in one week (C-7)

800, 800, 900, 900, 900, all PRESENT → base **4,300**.

### T-5 — Zero days

No daily records for the week → gross **0**, not an error.

### T-6 — Deduction exceeds gross (A-6)

Balance 10,000, gross 4,000, contractor enters deduction 10,000 → finalization blocked;
capped deduction 4,000 → net **0**, remaining balance **6,000**. Net is never negative.

### T-7 — Deduction exceeds balance (A-5)

Balance 500, contractor enters 1,000 → rejected.

### T-8 — Month-crossing week (W-3)

Records on 29 Sep – 5 Oct with `week_start_date = 29 Sep` → one settlement covering all seven
days.

### T-9 — Default wage change does not affect history (R-2)

Daily records exist at 800. `worker.default_wage` changed to 900. Recomputing the old week
still yields 800 per day.

### T-10 — Adjustment after payment (J-6)

As in J-6: original settlement unchanged, one adjustment row of +900, next settlement payable
6,300.

### T-11 — Overpayment blocked (P-4)

Net payable 5,000, payments of 3,000 then 2,500 → second payment rejected.

### T-12 — Void and re-record (P-5)

Payment of 5,000 voided, payment of 3,000 recorded → amount paid **3,000**, two rows in
history, status `PARTIALLY_PAID`.

### T-13 — Assigned but absent (D-4)

Record with `site_id = A`, `attendance = ABSENT` → contributes 0 to base earnings, and the
worker still appears in Site A's assignment list for that day.

### T-14 — Mid-week joiner (C-9)

Joins Wednesday; records exist Wed–Sat at 900, all PRESENT → base **3,600**. No absent days
are generated for Mon–Tue.

### T-15 — Duplicate daily record (D-1)

Second insert for the same (worker, date) → rejected at the database level.
