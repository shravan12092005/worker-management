package com.workermanagement.app.db

import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.AppUser
import com.workermanagement.data.entity.Role
import com.workermanagement.data.entity.Settings
import com.workermanagement.data.entity.UserRole

/**
 * Idempotent first-run seeding:
 *  - default roles (fixed UUIDs so re-running never duplicates)
 *  - the single Settings row (spec §11 defaults)
 *  - one placeholder contractor user (username: contractor)
 *
 * MUST be called from a background thread — DAO methods are synchronous.
 */
object DatabaseSeeder {

    // Fixed UUIDs — identical on every device, so sync never sees conflicts
    private const val ROLE_MASON_UUID       = "00000000-0000-4000-8000-000000000001"
    private const val ROLE_HELPER_UUID      = "00000000-0000-4000-8000-000000000002"
    private const val ROLE_CARPENTER_UUID   = "00000000-0000-4000-8000-000000000003"
    private const val ROLE_ELECTRICIAN_UUID = "00000000-0000-4000-8000-000000000004"
    private const val USER_CONTRACTOR_UUID  = "00000000-0000-4000-8000-000000000101"

    fun seedIfEmpty(db: AppDatabase) {
        seedRoles(db)
        seedSettings(db)
        seedContractorUser(db)
    }

    private fun seedRoles(db: AppDatabase) {
        val existing = db.roleDao().getAll().map { it.id }.toSet()

        val defaults = listOf(
            ROLE_MASON_UUID to "Mason",
            ROLE_HELPER_UUID to "Helper",
            ROLE_CARPENTER_UUID to "Carpenter",
            ROLE_ELECTRICIAN_UUID to "Electrician"
        )
        for ((id, name) in defaults) {
            if (id !in existing) {
                db.roleDao().insert(Role(id = id, name = name))
            }
        }
    }

    private fun seedSettings(db: AppDatabase) {
        if (db.settingsDao().get() == null) {
            // All values are the documented spec §11 / rules.md defaults.
            db.settingsDao().upsert(Settings())
        }
    }

    private fun seedContractorUser(db: AppDatabase) {
        val dao = db.appUserDao()
        if (dao.getByUsername("contractor") == null) {
            dao.insert(
                AppUser(
                    id = USER_CONTRACTOR_UUID,
                    name = "Contractor",
                    username = "contractor",
                    // PLACEHOLDER — spec §12 requires Argon2id/bcrypt hashing.
                    // Replaced by the real auth-gate implementation; login is
                    // not enforced in the skeleton.
                    passwordHash = "PLACEHOLDER-SET-UP-REAL-HASH",
                    role = UserRole.CONTRACTOR
                )
            )
        }
    }
}
