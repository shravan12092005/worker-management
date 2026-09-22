package com.workermanagement.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.workermanagement.data.entity.Site
import com.workermanagement.data.entity.Worker

/**
 * Minimal home screen proving the Room round-trip works:
 * lists active sites and active workers from the local database.
 */
@Composable
fun HomeScreen(sites: List<Site>, workers: List<Worker>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Sites (${sites.size})", style = MaterialTheme.typography.titleMedium)
        sites.forEach { site ->
            Card { Text(site.name, modifier = Modifier.padding(12.dp)) }
        }

        Text("Workers (${workers.size})", style = MaterialTheme.typography.titleMedium)
        workers.forEach { worker ->
            Card { Text("${worker.name}  ·  ₹${worker.defaultWage}/day", modifier = Modifier.padding(12.dp)) }
        }
    }
}
