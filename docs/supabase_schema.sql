-- ============================================================
-- Worker Management — Supabase Postgres Schema
-- Run this in the Supabase SQL editor (project dashboard → SQL editor).
-- ============================================================
-- IMPORTANT: RLS is NOT enabled. All tables are accessible to the
-- anon key. RLS policies must be added as a follow-up once auth
-- (app_user sync) is implemented. This is a deliberate deferral.
-- ============================================================

-- ─── Role ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS role (
    id              TEXT        PRIMARY KEY,
    name            TEXT        NOT NULL,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE
);

-- ─── Site ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS site (
    id              TEXT        PRIMARY KEY,
    name            TEXT        NOT NULL,
    location        TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TEXT        NOT NULL,
    updated_at      TEXT        NOT NULL
);

-- ─── Worker ─────────────────────────────────────────────────
-- UNIQUE constraint on code matches Room unique index (spec §5.1).
CREATE TABLE IF NOT EXISTS worker (
    id              TEXT        PRIMARY KEY,
    code            TEXT        NOT NULL,
    name            TEXT        NOT NULL,
    phone           TEXT,
    address         TEXT,
    default_role_id TEXT        NOT NULL REFERENCES role(id),
    default_wage    INTEGER     NOT NULL,
    joining_date    TEXT        NOT NULL,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TEXT        NOT NULL,
    updated_at      TEXT        NOT NULL,
    UNIQUE (code)
);

-- ─── Daily Record ────────────────────────────────────────────
-- UNIQUE(worker_id, work_date) matches Room D-1 constraint.
CREATE TABLE IF NOT EXISTS daily_record (
    id              TEXT        PRIMARY KEY,
    worker_id       TEXT        NOT NULL REFERENCES worker(id),
    work_date       TEXT        NOT NULL,
    week_start_date TEXT        NOT NULL,
    site_id         TEXT        NOT NULL REFERENCES site(id),
    role_id         TEXT        NOT NULL REFERENCES role(id),
    wage            INTEGER     NOT NULL,
    attendance      TEXT        NOT NULL,   -- PRESENT | HALF_DAY | ABSENT
    overtime_amount INTEGER     NOT NULL DEFAULT 0,
    note            TEXT,
    is_locked       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TEXT        NOT NULL,
    updated_at      TEXT        NOT NULL,
    UNIQUE (worker_id, work_date)
);
CREATE INDEX IF NOT EXISTS daily_record_week_worker
    ON daily_record (week_start_date, worker_id);
CREATE INDEX IF NOT EXISTS daily_record_site_date
    ON daily_record (site_id, work_date);

-- ─── Weekly Settlement ───────────────────────────────────────
-- UNIQUE(worker_id, week_start_date) matches Room S-2 constraint.
CREATE TABLE IF NOT EXISTS weekly_settlement (
    id                  TEXT        PRIMARY KEY,
    worker_id           TEXT        NOT NULL REFERENCES worker(id),
    week_start_date     TEXT        NOT NULL,
    base_earnings       INTEGER     NOT NULL,
    overtime_earnings   INTEGER     NOT NULL,
    gross_earnings      INTEGER     NOT NULL,
    advance_deduction   INTEGER     NOT NULL,
    net_payable         INTEGER     NOT NULL,
    status              TEXT        NOT NULL,   -- PENDING | PARTIALLY_PAID | PAID
    finalized_at        TEXT        NOT NULL,
    UNIQUE (worker_id, week_start_date)
);

-- ─── Advance Transaction ─────────────────────────────────────
-- settlement_id is nullable — set when deduction is linked to a weekly
-- settlement (rule A-7). payment_id is NOT used (see plan §5.5 correction).
CREATE TABLE IF NOT EXISTS advance_txn (
    id              TEXT        PRIMARY KEY,
    worker_id       TEXT        NOT NULL REFERENCES worker(id),
    txn_date        TEXT        NOT NULL,
    type            TEXT        NOT NULL,   -- ADVANCE_GIVEN | DEDUCTION | WRITE_OFF
    amount          INTEGER     NOT NULL,
    settlement_id   TEXT        REFERENCES weekly_settlement(id),
    note            TEXT,
    created_at      TEXT        NOT NULL
);

-- ─── Payment ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payment (
    id              TEXT        PRIMARY KEY,
    settlement_id   TEXT        NOT NULL REFERENCES weekly_settlement(id),
    paid_on         TEXT        NOT NULL,
    amount          INTEGER     NOT NULL,
    method          TEXT        NOT NULL,   -- CASH | OTHER
    note            TEXT,
    is_void         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TEXT        NOT NULL
);

-- ─── Adjustment ──────────────────────────────────────────────
-- amount is SIGNED: positive = owed to worker, negative = worker overpaid.
CREATE TABLE IF NOT EXISTS adjustment (
    id                          TEXT        PRIMARY KEY,
    settlement_id               TEXT        NOT NULL REFERENCES weekly_settlement(id),
    amount                      INTEGER     NOT NULL,
    reason                      TEXT        NOT NULL,
    created_at                  TEXT        NOT NULL,
    settled_in_settlement_id    TEXT        REFERENCES weekly_settlement(id)
);

-- ─── App User ────────────────────────────────────────────────
-- Local-only this task. Table created for FK completeness.
-- NOT synced via SyncManager yet.
CREATE TABLE IF NOT EXISTS app_user (
    id              TEXT        PRIMARY KEY,
    name            TEXT        NOT NULL,
    username        TEXT        NOT NULL,
    password_hash   TEXT        NOT NULL,
    role            TEXT        NOT NULL,   -- CONTRACTOR | SITE_MANAGER
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (username)
);

-- ─── Audit Log ───────────────────────────────────────────────
-- changed_by is TEXT with NO FK to app_user. FK constraint will be
-- added as a follow-up migration once app_user sync is implemented.
CREATE TABLE IF NOT EXISTS audit_log (
    id              TEXT        PRIMARY KEY,
    entity_type     TEXT        NOT NULL,
    entity_id       TEXT        NOT NULL,
    field           TEXT        NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    changed_by      TEXT,       -- intentionally no FK (app_user not yet synced)
    changed_at      TEXT        NOT NULL
);
CREATE INDEX IF NOT EXISTS audit_log_entity
    ON audit_log (entity_type, entity_id);

-- ─── Settings ────────────────────────────────────────────────
-- Singleton row (singleton_id = 1 always). Synced so all devices
-- compute wages from the same settings.
CREATE TABLE IF NOT EXISTS settings (
    singleton_id            INTEGER     PRIMARY KEY DEFAULT 1,
    week_start_day          TEXT        NOT NULL DEFAULT 'MONDAY',
    week_end_day            TEXT        NOT NULL DEFAULT 'SUNDAY',
    payment_day             TEXT        NOT NULL DEFAULT 'MONDAY',
    half_day_fraction_pct   INTEGER     NOT NULL DEFAULT 50,
    absent_value            INTEGER     NOT NULL DEFAULT 0,
    overtime_entry          TEXT        NOT NULL DEFAULT 'FLAT_AMOUNT',
    allow_two_sites_one_day BOOLEAN     NOT NULL DEFAULT FALSE,
    allow_two_roles_one_day BOOLEAN     NOT NULL DEFAULT FALSE,
    CHECK (singleton_id = 1)            -- enforces single-row invariant
);

-- ─── Follow-up items ─────────────────────────────────────────
-- 1. Enable RLS on all tables once auth is implemented.
-- 2. Add FK: audit_log.changed_by REFERENCES app_user(id)
--    once app_user sync lands.
-- 3. Add row-level policies scoped to the authenticated user's
--    organisation once multi-tenancy is considered.
