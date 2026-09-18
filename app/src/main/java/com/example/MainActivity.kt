package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.activity.PaymentActivityScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.invoices.PendingInvoicesScreen
import com.example.ui.navigation.Screen
import com.example.ui.settings.SettingsScreen
import com.example.ui.setup.SetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.transactions.TransactionHistoryScreen
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.PayLinkViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PayLinkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            MyApplicationTheme(darkTheme = isDarkMode) {
                // Enforce RTL for Persian layout
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PayLinkMainApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun PayLinkMainApp(viewModel: PayLinkViewModel) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // React to auth changes
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Connected -> {
                if (currentRoute == Screen.Setup.route || currentRoute == null) {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is AuthState.NeedsSetup -> {
                if (currentRoute != Screen.Setup.route) {
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is AuthState.AccountDisabled -> {
                // Keep user in Dashboard to show the disabled status banner
            }
        }
    }

    val showBottomBar = authState is AuthState.Connected && currentRoute != Screen.Setup.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.testTag("main_bottom_nav")
                ) {
                    val items = listOf(
                        Triple(Screen.Dashboard, Icons.Default.Dashboard, "پیشخوان"),
                        Triple(Screen.PendingInvoices, Icons.Default.HourglassEmpty, "در انتظار"),
                        Triple(Screen.TransactionHistory, Icons.Default.History, "تراکنش‌ها"),
                        Triple(Screen.PaymentActivity, Icons.Default.Sms, "پیامک‌ها"),
                        Triple(Screen.Settings, Icons.Default.Settings, "تنظیمات")
                    )

                    items.forEach { (screen, icon, label) ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (authState is AuthState.Connected) Screen.Dashboard.route else Screen.Setup.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Setup.route) {
                SetupScreen(viewModel = viewModel)
            }

            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToPending = { navController.navigate(Screen.PendingInvoices.route) },
                    onNavigateToTransactions = { navController.navigate(Screen.TransactionHistory.route) },
                    onNavigateToActivity = { navController.navigate(Screen.PaymentActivity.route) }
                )
            }

            composable(Screen.PendingInvoices.route) {
                PendingInvoicesScreen(
                    viewModel = viewModel,
                    onBack = { navController.navigateUp() }
                )
            }

            composable(Screen.TransactionHistory.route) {
                TransactionHistoryScreen(
                    viewModel = viewModel,
                    onBack = { navController.navigateUp() }
                )
            }

            composable(Screen.PaymentActivity.route) {
                PaymentActivityScreen(
                    viewModel = viewModel,
                    onBack = { navController.navigateUp() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.navigateUp() }
                )
            }
        }
    }
}
