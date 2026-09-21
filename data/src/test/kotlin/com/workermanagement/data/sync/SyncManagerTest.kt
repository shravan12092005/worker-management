package com.workermanagement.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.workermanagement.data.BuildConfig
import com.workermanagement.data.db.AppDatabase
import com.workermanagement.data.entity.Role
import com.workermanagement.data.entity.Site
import com.workermanagement.data.entity.Worker
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection

/**
 * SyncManager unit tests using MockWebServer — no real network required.
 *
 * These run as normal Robolectric tests alongside PersistenceTest.
 *
 * For a real round-trip against live Supabase, see SyncManagerIntegrationTest
 * (run with `./gradlew :data:testDebugUnitTest -Pintegration`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncManagerTest {

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .addCallback(AppDatabase.FOREIGN_KEYS_CALLBACK)
            .build()

        server = MockWebServer()
        server.start()

        syncManager = SyncManager(
            db = db,
            httpClient = OkHttpClient(),
            baseUrlOverride = server.url("/").toString()
        )
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────

    private val role = Role(id = "role-1", name = "Mason")
    private val site = Site(
        id = "site-1", name = "Site A",
        createdAt = "2026-09-21T00:00:00", updatedAt = "2026-09-21T00:00:00"
    )
    private val worker = Worker(
        id = "worker-1", code = "W001", name = "Ramu",
        defaultRoleId = "role-1", defaultWage = 900,
        joiningDate = "2026-01-01",
        createdAt = "2026-09-21T00:00:00", updatedAt = "2026-09-21T00:00:00"
    )

    private fun enqueue201() = server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_CREATED))

    // ─── Tests ────────────────────────────────────────────────────────────

    /**
     * When all tables are empty (nothing unsynced), syncNow returns
     * Success(0) and makes no HTTP calls.
     */
    @Test
    fun sync_emptyDatabase_returnsSuccessZero() {
        val result = syncManager.syncNow()
        assertTrue(result is SyncResult.Success)
        assertEquals(0, (result as SyncResult.Success).rowsSynced)
        assertEquals(0, server.requestCount)
    }

    /**
     * Inserts role + worker (both unsynced). syncNow should:
     *  1. POST to /role → 201
     *  2. POST to /worker → 201
     *  3. Mark both synced = true locally
     *  4. Return Success(2)
     */
    @Test
    fun sync_roleAndWorker_postsInOrderAndMarksSynced() {
        db.roleDao().insert(role)
        db.workerDao().insert(worker)
        enqueue201() // role
        enqueue201() // worker

        val result = syncManager.syncNow()

        // Result
        assertTrue(result is SyncResult.Success)
        assertEquals(2, (result as SyncResult.Success).rowsSynced)

        // HTTP requests in dependency order: role before worker
        val req1: RecordedRequest = server.takeRequest()
        assertTrue("First request should target /role", req1.path!!.endsWith("role"))
        val req2: RecordedRequest = server.takeRequest()
        assertTrue("Second request should target /worker", req2.path!!.endsWith("worker"))

        // Request bodies are valid JSON arrays
        val roleBody = JSONArray(req1.body.readUtf8())
        assertEquals("role-1", roleBody.getJSONObject(0).getString("id"))
        val workerBody = JSONArray(req2.body.readUtf8())
        assertEquals("worker-1", workerBody.getJSONObject(0).getString("id"))

        // Local rows are now marked synced
        val updatedRole = db.roleDao().getById("role-1")
        assertTrue("role should be synced", updatedRole!!.synced)
        val updatedWorker = db.workerDao().getById("worker-1")
        assertTrue("worker should be synced", updatedWorker!!.synced)
    }

    /**
     * When the server returns an error for the role push, syncNow returns
     * Failure("role", …) and the role is NOT marked synced.
     */
    @Test
    fun sync_serverError_returnsFailureAndLeavesUnsyncedState() {
        db.roleDao().insert(role)
        server.enqueue(MockResponse().setResponseCode(500).setBody("{\"error\":\"internal\"}"))

        val result = syncManager.syncNow()

        assertTrue(result is SyncResult.Failure)
        assertEquals("role", (result as SyncResult.Failure).phase)

        // Role must still be unsynced
        val updatedRole = db.roleDao().getById("role-1")
        assertFalse("role should remain unsynced after server error", updatedRole!!.synced)
    }

    /**
     * Verifies that the `synced` field itself is NOT included in the JSON
     * posted to Supabase.
     */
    @Test
    fun sync_jsonBody_doesNotContainSyncedField() {
        db.roleDao().insert(role)
        enqueue201()

        syncManager.syncNow()

        val req = server.takeRequest()
        val body = JSONArray(req.body.readUtf8()).getJSONObject(0)
        assertFalse("synced must not appear in the payload sent to Supabase", body.has("synced"))
    }

    /**
     * Verifies that the correct Supabase headers are sent.
     */
    @Test
    fun sync_correctHeadersSent() {
        db.roleDao().insert(role)
        enqueue201()

        syncManager.syncNow()

        val req = server.takeRequest()
        val prefer = req.getHeader("Prefer") ?: ""
        assertTrue(prefer.contains("resolution=merge-duplicates"))
        assertTrue(prefer.contains("return=minimal"))
        assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
    }
}
