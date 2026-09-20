# Workforce, Attendance & Wage Management — System Specification

**Version:** 1.0 (V1 scope)
**Platform:** Android (mobile-first, private distribution — not Play Store)
**Audience for this document:** developers and AI coding agents working on this repo.

> Read `docs/rules.md` alongside this file. This document describes *what the system is*.
> `rules.md` describes *how it must behave*, as testable assertions. Where the two appear to
> disagree, `rules.md` wins.

---

## 1. Purpose

A contractor manages approximately 300–400 construction workers across multiple active work
sites. Workers move between sites during the same week, may perform different roles on
different days, may be paid different rates on different days, may earn overtime, and take
cash advances that are recovered from their weekly wages.

This system replaces physical registers and spreadsheets with an Android application that:

1. Records daily work assignment and attendance at the work site, on a phone.
2. Calculates weekly wages from the actual daily records.
3. Tracks advances and their recovery.
4. Records weekly payments.
5. Preserves complete history — nothing is overwritten or deleted.

## 2. Users

| User | Count | Capability in V1 |
|---|---|---|
| Contractor (owner) | 1 | Everything |
| Site manager | 1–2 | Everything |

Roles are stored in the data model from day one but **not enforced differently in V1** — all
authenticated users have the same capability. This allows restricting site managers later
(e.g. no access to payments) without a migration.

Authentication is required. See §10.

## 3. Core design principle

> **The daily work record is the single source of truth.**

A worker's profile holds *defaults only* (default role, default wage). It never determines
what a worker is paid. Every day a worker works produces one `daily_record` row that stores a
**copy** of the site, role and wage that applied on that day.

Consequences, which the implementation must respect:

- Changing a worker's default wage **never** changes any existing `daily_record`.
- Weekly totals are always computed from `daily_record` rows, never from the worker profile.
- Historical reports are correct by construction; no "wage history" table is needed.
- A worker is never "assigned to a site" as a property of the worker. Their site on a given
  day is a field on that day's record.

## 4. V1 scope

**In scope**

- Worker management (add, edit, search, activate/deactivate)
- Site management (add, edit, activate/deactivate)
- Daily work assignment + attendance capture, one screen per site per date
- Overtime / extra payment as a rupee amount per day
- Weekly wage calculation
- Advance ledger (advances given, deductions applied, running balance)
- Weekly payment recording, with finalization and adjustments
- Reports (§9)
- Offline operation with background sync
- Local backup / export

**Explicitly out of scope for V1** — do not build these, but do not make them impossible:

| Out of scope | Why | Keep the door open by |
|---|---|---|
| Two sites for one worker on one day | Rare; doubles UI complexity | `daily_record` has its own PK; the (worker, date) uniqueness is an index, not a structural assumption |
| Two roles on one day | Same | Same |
| Hourly overtime rates | Contractor pays flat amounts | Store overtime as an amount; add `hours` + `rate` columns later if needed |
| Multiple payments within one week | Adds reconciliation complexity | `payment` is already a separate table with many rows possible per week |
| Per-worker photos / biometrics | Not required | — |
| Play Store distribution, multi-tenancy | Single business, private install | — |

## 5. Data model

Money is stored as **integer rupees**. Never use floating-point for currency anywhere in the
codebase. Attendance fractions use a fixed enum, not arbitrary decimals.

### 5.1 `worker`

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (text) | Generated on device; stable across sync |
| `code` | text | Human-readable worker ID shown in the UI, unique |
| `name` | text | Required |
| `phone` | text, nullable | **Optional** — many workers have no phone |
| `address` | text, nullable | |
| `default_role_id` | FK → `role` | Used only to pre-fill new daily records |
| `default_wage` | int | Rupees/day. Used only to pre-fill new daily records |
| `joining_date` | date | |
| `is_active` | bool | Soft state; never delete a worker |
| `created_at`, `updated_at` | timestamp | |

Re-joining workers reuse their existing row (set `is_active = true`). Never create a second
row for the same person.

### 5.2 `site`

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `name` | text | |
| `location` | text, nullable | |
| `is_active` | bool | Closed sites are deactivated, never deleted |
| `created_at`, `updated_at` | timestamp | |

### 5.3 `role`

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `name` | text | Mason, Helper, Carpenter, Electrician, … |
| `is_active` | bool | |

Roles are a lookup table so they can be renamed centrally. A `daily_record` stores the
`role_id`; role *names* may be edited without affecting history.

### 5.4 `daily_record` — the central table

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `worker_id` | FK → `worker` | |
| `work_date` | date | |
| `week_start_date` | date | **Denormalised**, computed on insert. See Rule W-1 |
| `site_id` | FK → `site` | The site for this day |
| `role_id` | FK → `role` | The role *actually performed* this day |
| `wage` | int | Rupees/day **applied on this day** — a copy, not a reference |
| `attendance` | enum | `PRESENT` \| `ABSENT` \| `HALF_DAY` |
| `overtime_amount` | int | Default 0 |
| `note` | text, nullable | Free text, e.g. reason for extra payment |
| `is_locked` | bool | True once the week has been finalized |
| `created_at`, `updated_at` | timestamp | |

Unique index on (`worker_id`, `work_date`).
Index on (`week_start_date`, `worker_id`) — drives every weekly query.
Index on (`site_id`, `work_date`) — drives the site attendance screen.

Assignment and attendance are separate concepts: a record may exist with a `site_id` and
`attendance = ABSENT`.

### 5.5 `advance_txn` — advance ledger

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `worker_id` | FK → `worker` | |
| `txn_date` | date | |
| `type` | enum | `ADVANCE_GIVEN` \| `DEDUCTION` \| `WRITE_OFF` |
| `amount` | int | Always positive; `type` carries the sign |
| `payment_id` | FK → `payment`, nullable | Set when a deduction is part of a weekly payment |
| `note` | text, nullable | |
| `created_at` | timestamp | |

The outstanding balance is **derived**: `SUM(ADVANCE_GIVEN) − SUM(DEDUCTION) − SUM(WRITE_OFF)`.
There is no stored balance column anywhere. See Rule A-1.

### 5.6 `weekly_settlement`

One row per worker per week, created when the contractor finalizes that week for that worker.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `worker_id` | FK → `worker` | |
| `week_start_date` | date | |
| `base_earnings` | int | Snapshot at finalization |
| `overtime_earnings` | int | Snapshot |
| `gross_earnings` | int | `base + overtime` |
| `advance_deduction` | int | Amount the contractor chose to recover |
| `net_payable` | int | `gross − deduction`, never negative |
| `status` | enum | `PENDING` \| `PARTIALLY_PAID` \| `PAID` |
| `finalized_at` | timestamp | |

Unique index on (`worker_id`, `week_start_date`).

### 5.7 `payment`

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `settlement_id` | FK → `weekly_settlement` | |
| `paid_on` | date | |
| `amount` | int | |
| `method` | enum | `CASH` \| `OTHER` |
| `note` | text, nullable | |
| `is_void` | bool | Corrections void the row; they never edit the amount |
| `created_at` | timestamp | |

### 5.8 `adjustment`

Created when a finalized week's underlying data changes. Never modifies the original
settlement.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `settlement_id` | FK → `weekly_settlement` | The week being corrected |
| `amount` | int | Signed: positive = owed to worker, negative = owed by worker |
| `reason` | text | Required |
| `created_at` | timestamp | |
| `settled_in_settlement_id` | FK, nullable | The later week in which it was paid out |

### 5.9 `audit_log`

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `entity_type`, `entity_id` | text | |
| `field` | text | |
| `old_value`, `new_value` | text | |
| `changed_by` | FK → `app_user` | |
| `changed_at` | timestamp | |

Written for every change to: `daily_record.wage`, `daily_record.attendance`,
`daily_record.overtime_amount`, `daily_record.site_id`, `daily_record.role_id`,
`worker.default_wage`, and every row in `advance_txn`, `payment`, `adjustment`.

### 5.10 `app_user`

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `name` | text | |
| `username` | text, unique | |
| `password_hash` | text | Argon2id or bcrypt. Never store plaintext |
| `role` | enum | `CONTRACTOR` \| `SITE_MANAGER` |
| `is_active` | bool | |

### 5.11 `settings`

Single-row table holding the business decisions from §11 (`week_start_day`,
`half_day_fraction`, …), so they can be changed without a rebuild.

## 6. Primary workflows

### 6.1 Daily attendance capture (the most-used screen)

```
Open app → (already logged in)
   ↓
Select site        [remembers last used]
   ↓
Select date        [defaults to today]
   ↓
Worker list for that site/date appears, pre-filled from yesterday's
assignment at this site
   ↓
For each worker: tap P / A / HD          ← one tap, no typing
   ↓
Optional per worker: tap row to expand → change role, change wage,
                     add overtime amount, add note
   ↓
Save  → written locally, queued for sync
```

Design requirements:

- Marking a full site of 40 workers present must take under 30 seconds.
- A "Mark all present" action at the top, with per-worker corrections afterwards.
- No mandatory fields beyond attendance. Role and wage default from the worker profile.
- The screen must work with no network connection, with no visible difference except a
  sync indicator.
- Workers not present at this site today can be added to the list via search.

### 6.2 Weekly settlement

```
Select week → select worker (or "all workers with unsettled weeks")
   ↓
System shows day-by-day breakdown: date | site | role | wage | attendance | OT | day total
   ↓
Base earnings and overtime totals shown
   ↓
Outstanding advance balance shown
   ↓
Contractor enters advance deduction for this week  [defaults to 0]
   ↓
Net payable shown
   ↓
"Finalize week" → creates weekly_settlement, creates DEDUCTION advance_txn,
                  sets is_locked = true on that week's daily_records
   ↓
Record payment(s) against the settlement
```

### 6.3 Correcting a finalized week

```
Open a locked daily_record → "Correct" → warning shown
   ↓
Edit is applied to the daily_record (audit-logged)
   ↓
System recomputes what the week *should* have been
   ↓
Creates an `adjustment` row for the difference
   ↓
The adjustment appears on the worker's next settlement, and in the
"Pending adjustments" report until paid
```

The original `weekly_settlement` and `payment` rows are never modified.

## 7. Screens

1. **Login**
2. **Home / today** — site cards with today's headcount, quick links, sync status
3. **Attendance** — §6.1
4. **Workers** — searchable list; filters: active/inactive, role, site today, has outstanding advance, unpaid this week
5. **Worker detail** — profile, current advance balance, tabs for daily history / advances / payments
6. **Sites** — list, add, edit, activate/deactivate; site detail shows workers by date
7. **Weekly settlement** — §6.2
8. **Advances** — give advance, list of outstanding balances
9. **Payments** — pending vs paid this week, record payment
10. **Reports** — §9
11. **Settings** — business rules (§11), users, backup/export

## 8. Search and filtering

Worker search must match on name, worker code, and phone, with partial and
case-insensitive matching, and must stay responsive with 400 workers on a mid-range phone
(search the local database, not a network call).

## 9. Reports

Every report is filtered by a date range or a week, and every report must be exportable as
CSV.

- Weekly attendance — days worked per worker
- Weekly wage — base, overtime, gross, deduction, net per worker
- Payment status — who has not been paid for a given week
- Outstanding advances — balance per worker, descending
- Site-wise attendance — headcount per site per day
- Site-wise wage cost — total wages generated per site for a period
- Worker history — every daily record for a worker over a period
- Pending adjustments

## 10. Offline, sync and storage

- **Local database is primary.** All reads and writes go to the on-device database. The UI
  never blocks on the network.
- **IDs are generated on the device** (UUIDs), so records created offline need no
  renumbering on sync.
- **Sync is a background queue.** Each local change is enqueued; the queue drains when
  connectivity returns; failures are retried with backoff.
- **Conflict policy: last-write-wins by `updated_at`.** With 2–3 users who do not edit the
  same worker-day, this is sufficient. Conflicts are logged, not silently dropped.
- **Sync status must be honest.** The UI shows pending / syncing / synced / failed. It must
  never display a record as synced when it is not. A persistent banner appears if sync has
  failed or has not succeeded for more than 24 hours.
- **Unsaved input is not data.** If the app is killed mid-edit, the change must either have
  been committed to the local database or be absent entirely. No partial writes.
- **Device replacement:** after login on a new device, the full dataset is restored from the
  server.

## 11. Business decisions — confirm before coding

These are implemented as values in the `settings` table. The defaults below are placeholders;
confirm each one with the contractor and update this table.

| Decision | Default assumed | Confirmed? |
|---|---|---|
| Week start day | Monday | ☐ |
| Week end day | Sunday | ☐ |
| Payment day | Monday (following) | ☐ |
| Half-day value | 50% of that day's wage | ☐ |
| Absent value | ₹0 | ☐ |
| Overtime entry | Flat rupee amount per day | ☐ |
| Two sites in one day | Not supported in V1 | ☐ |
| Two roles in one day | Not supported in V1 | ☐ |
| Partial payments | Supported | ☐ |
| Multiple payments per week | Supported | ☐ |
| Advance deduction | Manual amount each week, default ₹0 | ☐ |
| Deduction > gross earnings | Blocked, with warning; deduction capped at gross | ☐ |
| Offline mode | Required | ☐ |
| Sunday work | Treated as a normal working day if recorded | ☐ |

## 12. Security

- Username + password login; password hashed with Argon2id (or bcrypt), never stored or
  logged in plaintext.
- Session persists on the device so the app does not demand a login at every site visit;
  optional device PIN / biometric unlock to reopen.
- The local database is encrypted at rest (SQLCipher or equivalent).
- No record is ever hard-deleted from the UI. Deactivate or void instead.
- All financial mutations are written to `audit_log` (§5.9).

## 13. Backup

- Automatic local export (CSV or JSON) of all tables on a schedule, written to device storage.
- Manual "Export all data" action in Settings, shareable via the Android share sheet.
- Server-side database backups, retained for at least 90 days, with a documented restore
  procedure that has actually been tested at least once.

**Set up backup and export before entering any real data.**

## 14. Build order

Build and verify in this sequence. Do not scaffold all screens first.

1. Schema + migrations
2. Wage calculation engine as **pure functions** with no UI and no database dependency
3. Unit tests for the calculation engine, covering every example in `docs/rules.md`
4. Local database layer + repositories
5. Worker, site and role CRUD
6. Attendance screen (§6.1)
7. Weekly settlement + finalization + locking
8. Advances
9. Payments + adjustments
10. Reports + CSV export
11. Auth
12. Sync
13. Backup

## 15. Glossary

| Term | Meaning |
|---|---|
| Daily record | One row in `daily_record`; one worker, one date |
| Applicable wage | The `wage` value stored on that day's record |
| Base earnings | Sum of attendance-weighted applicable wages for a week |
| Gross earnings | Base earnings + overtime |
| Net payable | Gross earnings − advance deduction |
| Finalize | Snapshot a week into a `weekly_settlement` and lock its daily records |
| Adjustment | A signed correction to an already-finalized week |
