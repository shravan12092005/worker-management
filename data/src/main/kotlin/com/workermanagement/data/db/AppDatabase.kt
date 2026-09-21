package com.workermanagement.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.workermanagement.data.dao.AdvanceTxnDao
import com.workermanagement.data.dao.AdjustmentDao
import com.workermanagement.data.dao.AppUserDao
import com.workermanagement.data.dao.AuditLogDao
import com.workermanagement.data.dao.DailyRecordDao
import com.workermanagement.data.dao.PaymentDao
import com.workermanagement.data.dao.RoleDao
import com.workermanagement.data.dao.SettlementDao
import com.workermanagement.data.dao.SettingsDao
import com.workermanagement.data.dao.SiteDao
import com.workermanagement.data.dao.WorkerDao
import com.workermanagement.data.entity.AdvanceTxn
import com.workermanagement.data.entity.Adjustment
import com.workermanagement.data.entity.AppUser
import com.workermanagement.data.entity.AuditLog
import com.workermanagement.data.entity.DailyRecord
import com.workermanagement.data.entity.Payment
import com.workermanagement.data.entity.Role
import com.workermanagement.data.entity.Settings
import com.workermanagement.data.entity.Site
import com.workermanagement.data.entity.WeeklySettlement
import com.workermanagement.data.entity.Worker

@Database(
    entities = [
        Worker::class,
        Site::class,
        Role::class,
        DailyRecord::class,
        AdvanceTxn::class,
        WeeklySettlement::class,
        Payment::class,
        Adjustment::class,
        AuditLog::class,
        AppUser::class,
        Settings::class,
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workerDao(): WorkerDao
    abstract fun siteDao(): SiteDao
    abstract fun roleDao(): RoleDao
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun advanceTxnDao(): AdvanceTxnDao
    abstract fun settlementDao(): SettlementDao
    abstract fun paymentDao(): PaymentDao
    abstract fun adjustmentDao(): AdjustmentDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun appUserDao(): AppUserDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        /**
         * Callback that enables foreign-key enforcement on every new connection.
         *
         * IMPORTANT: This is in onOpen, NOT onCreate.
         * PRAGMA foreign_keys is a per-connection SQLite setting; it resets to OFF
         * on every new connection. onCreate only fires once (on first creation),
         * so it would leave FK enforcement disabled on all subsequent connections.
         */
        val FOREIGN_KEYS_CALLBACK = object : Callback() {
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }
    }
}
