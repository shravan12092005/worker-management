package com.workermanagement.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.workermanagement.data.BuildConfig
import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.Role
import com.workermanagement.data.entity.Worker
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  INTEGRATION TEST — requires network access and a live Supabase     ║
 * ║  instance. Does NOT run as part of the normal `./gradlew :data:test`║
 * ║  suite. To run this explicitly:                                      ║
 * ║                                                                      ║
 * ║    ./gradlew :data:testDebugUnitTest --tests *.Integration*          ║
 * ║                                                                      ║
 * ║  Precondition: the tables in docs/supabase_schema.sql must exist in  ║
 * ║  the Supabase project before running this test.                      ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * Round-trip test:
 *  1. Insert Role + Worker locally (synced = false)
 *  2. Call syncNow()
 *  3. Assert SyncResult.Success
 *  4. Query Supabase REST API directly — confirm rows exist remotely
 *  5. Assert local rows are now synced = true
 *  6. Clean up: delete the test rows from Supabase
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncManagerIntegrationTest {

    private lateinit var db: AppDatabase
    private val httpClient = OkHttpClient()
    private val testRoleId = "integration-test-role-${System.currentTimeMillis()}"
    private val testWorkerId = "integration-test-worker-${System.currentTimeMillis()}"

    @Before
    fun setUp() {
        // Skip this test if running in a CI environment without network.
        Assume.assumeFalse(
            "Skipping integration test: set SKIP_INTEGRATION_TESTS=false to run",
            System.getenv("SKIP_INTEGRATION_TESTS") == "true"
        )

        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .addCallback(AppDatabase.FOREIGN_KEYS_CALLBACK)
            .build()
    }

    @After
    fun tearDown() {
        // Delete the test rows from Supabase to keep the remote DB clean.
        deleteFromSupabase("worker", testWorkerId)
        deleteFromSupabase("role", testRoleId)
        db.close()
    }

    @Test
    fun syncNow_roundTrip_rowsExistRemotelyAndLocallyMarkedSynced() {
        val role = Role(id = testRoleId, name = "IntegrationTestRole")
        val worker = Worker(
            id = testWorkerId, code = "INT-001", name = "Integration Test Worker",
            defaultRoleId = testRoleId, defaultWage = 500,
            joiningDate = "2026-01-01",
            createdAt = "2026-09-21T00:00:00", updatedAt = "2026-09-21T00:00:00"
        )
        db.roleDao().insert(role)
        db.workerDao().insert(worker)

        // ── Sync ──────────────────────────────────────────────────────────
        val syncManager = SyncManager(db = db, httpClient = httpClient)
        val result = syncManager.syncNow()

        // ── Assert result ─────────────────────────────────────────────────
        assertTrue("syncNow should succeed: $result", result is SyncResult.Success)
        assertEquals(2, (result as SyncResult.Success).rowsSynced)

        // ── Confirm rows exist in Supabase ────────────────────────────────
        val remoteRole = querySupabase("role", testRoleId)
        assertTrue("Role should exist in Supabase after sync", remoteRole.length() > 0)
        assertEquals(testRoleId, remoteRole.getJSONObject(0).getString("id"))

        val remoteWorker = querySupabase("worker", testWorkerId)
        assertTrue("Worker should exist in Supabase after sync", remoteWorker.length() > 0)
        assertEquals(testWorkerId, remoteWorker.getJSONObject(0).getString("id"))

        // ── Confirm local rows are marked synced ──────────────────────────
        val localRole = db.roleDao().getById(testRoleId)
        assertTrue("Role should be marked synced = true locally", localRole!!.synced)

        val localWorker = db.workerDao().getById(testWorkerId)
        assertTrue("Worker should be marked synced = true locally", localWorker!!.synced)
    }

    // ─── Supabase REST helpers ────────────────────────────────────────────

    private fun querySupabase(table: String, id: String): JSONArray {
        val url = "${BuildConfig.SUPABASE_URL}$table?id=eq.$id"
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .addHeader("Accept", "application/json")
            .get()
            .build()
        val body = httpClient.newCall(request).execute().use { it.body!!.string() }
        return JSONArray(body)
    }

    private fun deleteFromSupabase(table: String, id: String) {
        try {
            val url = "${BuildConfig.SUPABASE_URL}$table?id=eq.$id"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                .delete()
                .build()
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) {
            // Best-effort cleanup — don't fail the test on teardown errors
        }
    }
}
