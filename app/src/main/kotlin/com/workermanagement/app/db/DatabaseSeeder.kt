package com.workermanagement.app.db

import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.AppUser
import com.workermanagement.data.entity.Attendance
import com.workermanagement.data.entity.DailyRecord
import com.workermanagement.data.entity.Role
import com.workermanagement.data.entity.Settings
import com.workermanagement.data.entity.Site
import com.workermanagement.data.entity.UserRole
import com.workermanagement.data.entity.Worker
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Idempotent first-run seeding.
 *
 * Seeds (using fixed UUIDs so re-running never duplicates):
 *  - 4 roles
 *  - 3 sites
 *  - 6 workers
 *  - Settings (spec §11 defaults)
 *  - 1 contractor user
 *  - 6 days of attendance records for last week (Mon–Sat, all workers)
 *  - Today's attendance records (all workers, so Attendance screen shows
 *    data immediately)
 *
 * MUST be called from a background thread — DAO methods are synchronous.
 */
object DatabaseSeeder {

    // ── Fixed UUIDs ──────────────────────────────────────────────────────────

    private const val ROLE_MASON_UUID       = "00000000-0000-4000-8000-000000000001"
    private const val ROLE_HELPER_UUID      = "00000000-0000-4000-8000-000000000002"
    private const val ROLE_CARPENTER_UUID   = "00000000-0000-4000-8000-000000000003"
    private const val ROLE_ELECTRICIAN_UUID = "00000000-0000-4000-8000-000000000004"

    private const val SITE_ALPHA_UUID  = "00000000-0000-4000-8001-000000000001"
    private const val SITE_BRAVO_UUID  = "00000000-0000-4000-8001-000000000002"
    private const val SITE_CHARLIE_UUID= "00000000-0000-4000-8001-000000000003"

    private const val WORKER_RAVI_UUID    = "00000000-0000-4000-8002-000000000001"
    private const val WORKER_SURESH_UUID  = "00000000-0000-4000-8002-000000000002"
    private const val WORKER_AMIT_UUID    = "00000000-0000-4000-8002-000000000003"
    private const val WORKER_PRIYA_UUID   = "00000000-0000-4000-8002-000000000004"
    private const val WORKER_VIJAY_UUID   = "00000000-0000-4000-8002-000000000005"
    private const val WORKER_DEEPA_UUID   = "00000000-0000-4000-8002-000000000006"

    private const val USER_CONTRACTOR_UUID = "00000000-0000-4000-8000-000000000101"

    private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
    private val TS_FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

    // ── Entry point ──────────────────────────────────────────────────────────

    fun seedIfEmpty(db: AppDatabase) {
        seedRoles(db)
        seedSites(db)
        seedSettings(db)
        seedContractorUser(db)
        seedWorkers(db)
        seedAttendance(db)
    }

    // ── Reference data ────────────────────────────────────────────────────────

    private fun seedRoles(db: AppDatabase) {
        val existing = db.roleDao().getAll().map { it.id }.toSet()
        listOf(
            ROLE_MASON_UUID       to "Mason",
            ROLE_HELPER_UUID      to "Helper",
            ROLE_CARPENTER_UUID   to "Carpenter",
            ROLE_ELECTRICIAN_UUID to "Electrician",
        ).forEach { (id, name) ->
            if (id !in existing) db.roleDao().insert(Role(id = id, name = name))
        }
    }

    private fun seedSites(db: AppDatabase) {
        val now = "2026-01-01T00:00:00Z"
        val existing = db.siteDao().getAll().map { it.id }.toSet()
        listOf(
            Triple(SITE_ALPHA_UUID,   "Alpha Site",   true),
            Triple(SITE_BRAVO_UUID,   "Bravo Site",   true),
            Triple(SITE_CHARLIE_UUID, "Charlie Site", true),
        ).forEach { (id, name, active) ->
            if (id !in existing)
                db.siteDao().insert(Site(id = id, name = name, isActive = active,
                    createdAt = now, updatedAt = now))
        }
    }

    private fun seedSettings(db: AppDatabase) {
        if (db.settingsDao().get() == null) db.settingsDao().upsert(Settings())
    }

    private fun seedContractorUser(db: AppDatabase) {
        val dao = db.appUserDao()
        if (dao.getByUsername("contractor") == null) {
            dao.insert(AppUser(
                id           = USER_CONTRACTOR_UUID,
                name         = "Contractor",
                username     = "contractor",
                passwordHash = "PLACEHOLDER-SET-UP-REAL-HASH",
                role         = UserRole.CONTRACTOR,
            ))
        }
    }

    // ── Workers ───────────────────────────────────────────────────────────────

    private fun seedWorkers(db: AppDatabase) {
        val existing = db.workerDao().getAll().map { it.id }.toSet()
        val now = "2026-01-01T00:00:00Z"
        listOf(
            worker(WORKER_RAVI_UUID,   "W001", "Ravi Kumar",   ROLE_MASON_UUID,       900, "2026-01-01"),
            worker(WORKER_SURESH_UUID, "W002", "Suresh Patil", ROLE_MASON_UUID,       900, "2026-01-01"),
            worker(WORKER_AMIT_UUID,   "W003", "Amit Shah",    ROLE_HELPER_UUID,      700, "2026-01-01"),
            worker(WORKER_PRIYA_UUID,  "W004", "Priya Nair",   ROLE_HELPER_UUID,      700, "2026-03-01"),
            worker(WORKER_VIJAY_UUID,  "W005", "Vijay Rao",    ROLE_CARPENTER_UUID,   850, "2026-06-01"),
            worker(WORKER_DEEPA_UUID,  "W006", "Deepa Menon",  ROLE_ELECTRICIAN_UUID, 950, "2026-06-01"),
        ).forEach { w ->
            if (w.id !in existing) db.workerDao().insert(w)
        }
    }

    private fun worker(
        id: String, code: String, name: String,
        roleId: String, wage: Int, joining: String,
    ): Worker {
        val now = "2026-01-01T00:00:00Z"
        return Worker(
            id            = id,
            code          = code,
            name          = name,
            defaultRoleId = roleId,
            defaultWage   = wage,
            joiningDate   = joining,
            createdAt     = now,
            updatedAt     = now,
        )
    }

    // ── Attendance ────────────────────────────────────────────────────────────

    /**
     * Seeds two weeks of attendance so both the Attendance screen (today's
     * records) and the Settlement screen (last week's complete records) show
     * real data on a fresh install.
     *
     * Last week: Mon–Sat, all 6 workers across the three sites, fully marked.
     *   Ravi   + Suresh → Alpha Site  (Mason,       ₹900)
     *   Amit   + Priya  → Bravo Site  (Helper,      ₹700)
     *   Vijay  + Deepa  → Charlie Site (Carpenter/Electrician, ₹850/₹950)
     *
     * Today: all 6 workers on their same sites — attendance already marked
     *   so the Attendance screen isn't empty.
     */
    private fun seedAttendance(db: AppDatabase) {
        val today = LocalDate.now()
        val lastMonday  = today.with(DayOfWeek.MONDAY).minusWeeks(1)
        val thisMonday  = today.with(DayOfWeek.MONDAY)

        // Seed last week (Mon–Sat, 6 days)
        val lastWeekStart = lastMonday.format(DATE_FMT)
        val lastWeekDays  = (0L..5L).map { lastMonday.plusDays(it) }

        lastWeekDays.forEachIndexed { dayIdx, date ->
            val dateStr = date.format(DATE_FMT)
            val now     = date.atStartOfDay().format(TS_FMT)
            insertIfAbsent(db, dailyRecords(
                dateStr, lastWeekStart, now,
                dayIdx, overtimeOnThursday = true
            ))
        }

        // Seed today (single day — shows workers pre-filled on Attendance screen)
        val todayStr      = today.format(DATE_FMT)
        val thisWeekStart = thisMonday.format(DATE_FMT)
        val nowTs         = today.atStartOfDay().format(TS_FMT)
        val todayDayIdx   = today.dayOfWeek.value - 1  // 0=Mon … 5=Sat
        insertIfAbsent(db, dailyRecords(todayStr, thisWeekStart, nowTs, todayDayIdx))
    }

    /**
     * Produces the 6 worker daily records for a single [date].
     * [dayIndex] 0=Mon … 5=Sat. Thu (index 3) gets ₹300 overtime for Ravi.
     */
    private fun dailyRecords(
        date: String, weekStart: String, ts: String,
        dayIndex: Int, overtimeOnThursday: Boolean = false,
    ): List<DailyRecord> {
        val isThursday = dayIndex == 3
        return listOf(
            rec("$date-ravi",  WORKER_RAVI_UUID,   SITE_ALPHA_UUID,   ROLE_MASON_UUID,
                900, Attendance.PRESENT,   if (isThursday && overtimeOnThursday) 300 else 0,
                date, weekStart, ts),
            rec("$date-sures", WORKER_SURESH_UUID,  SITE_ALPHA_UUID,   ROLE_MASON_UUID,
                900, Attendance.PRESENT,   0, date, weekStart, ts),
            rec("$date-amit",  WORKER_AMIT_UUID,    SITE_BRAVO_UUID,   ROLE_HELPER_UUID,
                700, Attendance.PRESENT,   0, date, weekStart, ts),
            rec("$date-priya", WORKER_PRIYA_UUID,   SITE_BRAVO_UUID,   ROLE_HELPER_UUID,
                700, if (isThursday) Attendance.HALF_DAY else Attendance.PRESENT,
                0, date, weekStart, ts),
            rec("$date-vijay", WORKER_VIJAY_UUID,   SITE_CHARLIE_UUID, ROLE_CARPENTER_UUID,
                850, Attendance.PRESENT,   0, date, weekStart, ts),
            rec("$date-deepa", WORKER_DEEPA_UUID,   SITE_CHARLIE_UUID, ROLE_ELECTRICIAN_UUID,
                950, if (dayIndex == 5) Attendance.ABSENT else Attendance.PRESENT,
                0, date, weekStart, ts),
        )
    }

    private fun rec(
        idSuffix: String, workerId: String, siteId: String, roleId: String,
        wage: Int, attendance: Attendance, overtime: Int,
        date: String, weekStart: String, ts: String,
    ) = DailyRecord(
        id            = "seed-$idSuffix",
        workerId      = workerId,
        workDate      = date,
        weekStartDate = weekStart,
        siteId        = siteId,
        roleId        = roleId,
        wage          = wage,
        attendance    = attendance,
        overtimeAmount = overtime,
        createdAt     = ts,
        updatedAt     = ts,
    )

    private fun insertIfAbsent(db: AppDatabase, records: List<DailyRecord>) {
        records.forEach { rec ->
            try {
                db.dailyRecordDao().insert(rec)
            } catch (_: Exception) {
                // Already exists (unique constraint) — idempotent
            }
        }
    }
}
