package com.workermanagement.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.workermanagement.app.db.DatabaseProvider
import com.workermanagement.app.db.DatabaseSeeder
import com.workermanagement.app.ui.attendance.AttendanceScreen
import com.workermanagement.app.ui.attendance.AttendanceViewModel
import com.workermanagement.app.ui.navigation.BottomNavBar
import com.workermanagement.app.ui.navigation.Screen
import com.workermanagement.app.ui.payment.PaymentScreen
import com.workermanagement.app.ui.payment.PaymentViewModel
import com.workermanagement.app.ui.role.RoleListScreen
import com.workermanagement.app.ui.role.RoleViewModel
import com.workermanagement.app.ui.settlement.SettlementScreen
import com.workermanagement.app.ui.settlement.SettlementViewModel
import com.workermanagement.app.ui.site.SiteListScreen
import com.workermanagement.app.ui.site.SiteViewModel
import com.workermanagement.app.ui.theme.AppTheme
import com.workermanagement.app.ui.worker.AddEditWorkerScreen
import com.workermanagement.app.ui.worker.WorkerDetailScreen
import com.workermanagement.app.ui.worker.WorkerListScreen
import com.workermanagement.app.ui.worker.WorkerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = DatabaseProvider.get(this)
        CoroutineScope(Dispatchers.IO).launch { DatabaseSeeder.seedIfEmpty(db) }

        setContent {
            AppTheme {
                val navController = rememberNavController()
                // Single DB reference passed to all ViewModelFactories
                val dbRef = remember { db }

                Scaffold(
                    bottomBar = { BottomNavBar(navController) }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Attendance.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        // ── Attendance ───────────────────────────────────────
                        composable(Screen.Attendance.route) {
                            AttendanceScreen(
                                vm = viewModel(factory = AttendanceViewModel.Factory(dbRef))
                            )
                        }

                        // ── Home (kept for back-compat, redirects to attendance) ─
                        composable(Screen.Home.route) {
                            AttendanceScreen(
                                vm = viewModel(factory = AttendanceViewModel.Factory(dbRef))
                            )
                        }

                        // ── Settlement ───────────────────────────────────────
                        composable(Screen.Settlement.route) {
                            SettlementScreen(
                                vm = viewModel(factory = SettlementViewModel.Factory(dbRef))
                            )
                        }

                        // ── Payments ─────────────────────────────────────────
                        composable(Screen.Payments.route) {
                            PaymentScreen(
                                vm = viewModel(factory = PaymentViewModel.Factory(dbRef))
                            )
                        }

                        composable(Screen.Workers.route) {
                            val workerVm: WorkerViewModel =
                                viewModel(factory = WorkerViewModel.Factory(dbRef))
                            WorkerListScreen(
                                vm = workerVm,
                                onAddWorker = { navController.navigate(Screen.AddWorker.route) },
                                onWorkerClick = { w -> navController.navigate("workers/${w.id}") }
                            )
                        }

                        composable(Screen.AddWorker.route) {
                            val workerVm: WorkerViewModel =
                                viewModel(factory = WorkerViewModel.Factory(dbRef))
                            AddEditWorkerScreen(
                                vm = workerVm,
                                onNavigateUp = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.EditWorker.route,
                            arguments = listOf(navArgument("workerId") { type = NavType.StringType })
                        ) { backStack ->
                            val workerId = backStack.arguments?.getString("workerId") ?: return@composable
                            val workerVm: WorkerViewModel =
                                viewModel(factory = WorkerViewModel.Factory(dbRef))
                            val editingWorker = workerVm.workers.value.firstOrNull { it.id == workerId }

                            if (editingWorker != null) {
                                AddEditWorkerScreen(
                                    vm = workerVm,
                                    existingWorker = editingWorker,
                                    onNavigateUp = { navController.popBackStack() }
                                )
                            } else {
                                // Fallback: show detail (worker will load)
                                WorkerDetailScreen(
                                    vm = workerVm,
                                    workerId = workerId,
                                    onNavigateUp = { navController.popBackStack() },
                                    onEditWorker = { navController.navigate("workers/${workerId}/edit") }
                                )
                            }
                        }

                        // Worker detail (named differently to avoid conflict with edit route)
                        composable(
                            route = "workers/{workerId}",
                            arguments = listOf(navArgument("workerId") { type = NavType.StringType })
                        ) { backStack ->
                            val workerId = backStack.arguments?.getString("workerId") ?: return@composable
                            val workerVm: WorkerViewModel =
                                viewModel(factory = WorkerViewModel.Factory(dbRef))
                            WorkerDetailScreen(
                                vm = workerVm,
                                workerId = workerId,
                                onNavigateUp = { navController.popBackStack() },
                                onEditWorker = { w ->
                                    navController.navigate("workers/${w.id}/edit")
                                }
                            )
                        }

                        composable(
                            route = "workers/{workerId}/edit",
                            arguments = listOf(navArgument("workerId") { type = NavType.StringType })
                        ) { backStack ->
                            val workerId = backStack.arguments?.getString("workerId") ?: return@composable
                            val workerVm: WorkerViewModel =
                                viewModel(factory = WorkerViewModel.Factory(dbRef))
                            val editingWorker = workerVm.workers.value.firstOrNull { it.id == workerId }
                            AddEditWorkerScreen(
                                vm = workerVm,
                                existingWorker = editingWorker,
                                onNavigateUp = { navController.popBackStack() }
                            )
                        }

                        // ── Sites ────────────────────────────────────────
                        composable(Screen.Sites.route) {
                            SiteListScreen(vm = viewModel(factory = SiteViewModel.Factory(dbRef)))
                        }

                        // ── Roles ────────────────────────────────────────
                        composable(Screen.Roles.route) {
                            RoleListScreen(vm = viewModel(factory = RoleViewModel.Factory(dbRef)))
                        }
                    }
                }
            }
        }
    }
}
