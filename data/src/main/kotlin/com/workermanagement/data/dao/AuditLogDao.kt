package com.workermanagement.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workermanagement.data.entity.AuditLog

@Dao
interface AuditLogDao {

    /** Audit log is append-only — inserts only, never updates or deletes (G-2, G-3). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(log: AuditLog)

    @Query("""
        SELECT * FROM audit_log
        WHERE entity_type = :entityType AND entity_id = :entityId
        ORDER BY changed_at ASC
    """)
    fun getForEntity(entityType: String, entityId: String): List<AuditLog>

    @Query("SELECT * FROM audit_log WHERE changed_by = :userId ORDER BY changed_at DESC")
    fun getForUser(userId: String): List<AuditLog>
}
