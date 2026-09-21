package com.workermanagement.data.sync

/**
 * Result of a [SyncManager.syncNow] call.
 *
 * - [Success]: all 10 phases completed; [rowsSynced] is the total number of
 *   rows marked synced across all tables.
 * - [Failure]: a phase failed; [phase] names the table being pushed when the
 *   error occurred; [error] is the underlying exception. No rows beyond those
 *   already synced in earlier phases are marked synced.
 */
sealed class SyncResult {
    data class Success(val rowsSynced: Int) : SyncResult()
    data class Failure(val phase: String, val error: Exception) : SyncResult()
}
