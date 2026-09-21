package com.workermanagement.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.workermanagement.app.db.DatabaseProvider
import com.workermanagement.app.db.DatabaseSeeder
import com.workermanagement.app.ui.HomeScreen
import com.workermanagement.data.entity.Site
import com.workermanagement.data.entity.Worker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var sites by mutableStateOf<List<Site>>(emptyList())
    private var workers by mutableStateOf<List<Worker>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = DatabaseProvider.get(this)

        lifecycleScope.launch(Dispatchers.IO) {
            // Idempotent: inserts only what's missing (roles, settings, user)
            DatabaseSeeder.seedIfEmpty(db)
            val s = db.siteDao().getActive()
            val w = db.workerDao().getActive()
            withContext(Dispatchers.Main) {
                sites = s
                workers = w
            }
        }

        setContent {
            MaterialTheme {
                Surface {
                    HomeScreen(sites = sites, workers = workers)
                }
            }
        }
    }
}
