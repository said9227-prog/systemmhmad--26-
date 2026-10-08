package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.database.AppDatabase
import com.example.data.repository.AppRepository
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.AppViewModelFactory

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Room Database and Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = AppRepository(database)

        setContent {
            val reportViewModel: com.example.ui.viewmodel.ReportViewModel = viewModel(
                factory = com.example.ui.viewmodel.ReportViewModelFactory(application, repository)
            )
            val returnViewModel: com.example.ui.viewmodel.ReturnViewModel = viewModel(
                factory = com.example.ui.viewmodel.ReturnViewModelFactory(application, repository)
            )
            val viewModel: AppViewModel = viewModel(
                factory = AppViewModelFactory(application, repository)
            )
            val isDarkMode by viewModel.isDarkMode.collectAsState()
    val storeSettings by viewModel.storeSettings.collectAsState()

            MyApplicationTheme(darkTheme = isDarkMode) {
                val appLocked by viewModel.appLocked.collectAsState()

                // Force Right-to-Left (RTL) direction as the entire application is in Arabic
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    if (appLocked) {
                        SecurityScreen(
                            viewModel = viewModel,
                            onUnlocked = { viewModel.unlockApp(viewModel.securityPin.value ?: "") }
                        )
                    } else {
                        MainAppContent(viewModel = viewModel, reportViewModel = reportViewModel, returnViewModel = returnViewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: AppViewModel, reportViewModel: com.example.ui.viewmodel.ReportViewModel, returnViewModel: com.example.ui.viewmodel.ReturnViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val baseRoute = currentRoute?.substringBefore('?')
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val storeSettings by viewModel.storeSettings.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val paymentError by viewModel.paymentError.collectAsState()

    LaunchedEffect(paymentError) {
        paymentError?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearPaymentError()
        }
    }

    // Define top level navigation destinations
    val navigationItems = listOf(
        NavigationItem(
            route = "dashboard",
            title = storeSettings.storeName,
            selectedIcon = Icons.Default.Dashboard,
            unselectedIcon = Icons.Outlined.Dashboard,
            testTag = "nav_dashboard"
        ),
        NavigationItem(
            route = "clients",
            title = "العملاء",
            selectedIcon = Icons.Default.People,
            unselectedIcon = Icons.Outlined.People,
            testTag = "nav_clients"
        ),
        NavigationItem(
            route = "items",
            title = "الأصناف",
            selectedIcon = Icons.Default.Inventory2,
            unselectedIcon = Icons.Outlined.Inventory2,
            testTag = "nav_items"
        ),

        NavigationItem(
            route = "invoices",
            title = "الفواتير",
            selectedIcon = Icons.Default.ReceiptLong,
            unselectedIcon = Icons.Outlined.ReceiptLong,
            testTag = "nav_invoices"
        ),
        NavigationItem(
            route = "reports",
            title = "التقارير",
            selectedIcon = Icons.Default.BarChart,
            unselectedIcon = Icons.Outlined.BarChart,
            testTag = "nav_reports"
        ),
        NavigationItem(
            route = "settings",
            title = "الإعدادات",
            selectedIcon = Icons.Default.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            testTag = "nav_settings"
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val currentNavItem = navigationItems.find { it.route == baseRoute }
            if (currentNavItem != null && baseRoute != "clients") {
                TopAppBar(
                    title = {
                        Text(
                            text = currentNavItem.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleDarkMode() },
                            modifier = Modifier.testTag("theme_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.WbSunny else Icons.Default.NightsStay,
                                contentDescription = if (isDarkMode) "تفعيل الوضع النهاري" else "تفعيل الوضع الليلي",
                                tint = if (isDarkMode) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            // Only show bottom navigation on top-level routes
            val shouldShowBottomBar = navigationItems.any { it.route == baseRoute }
            if (shouldShowBottomBar) {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    navigationItems.forEach { item ->
                        val selected = baseRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (baseRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo("dashboard") {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) },
            exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) },
            popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) },
            popExitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) }
        ) {
            // Dashboard destination
            composable("dashboard") {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToCreateInvoice = { navController.navigate("create_invoice") },
                    onNavigateToAddClient = { navController.navigate("add_client") },
                    onNavigateToAddItem = { navController.navigate("add_item") },
                    onNavigateToItems = { navController.navigate("items") },
                    onNavigateToInvoices = { navController.navigate("invoices") },
                    onNavigateToClients = { navController.navigate("clients") },
                    onNavigateToInstallments = { navController.navigate("installments") },
                    onNavigateToClientProfile = { clientId -> navController.navigate("client_profile/$clientId") },
                    onNavigateToCreateReturn = { type -> navController.navigate("create_return/$type") },
                    onNavigateToReturnsList = { navController.navigate("returns_list") },
                    onNavigateToTopMovingItems = { itemId, itemName ->
                        val route = if (itemId != null && itemId > 0) {
                            "top_moving_items?itemId=$itemId"
                        } else if (!itemName.isNullOrBlank()) {
                            "top_moving_items?itemName=${java.net.URLEncoder.encode(itemName, "UTF-8")}"
                        } else {
                            "top_moving_items"
                        }
                        navController.navigate(route)
                    }
                )
            }

            // Clients portfolio list destination
            composable("add_client") {
                com.example.ui.screens.AddClientScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Add Item destination
            composable("add_item") {
                com.example.ui.screens.AddItemScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(
                    route = "clients?openAdd={openAdd}",
                arguments = listOf(navArgument("openAdd") {
                    type = NavType.BoolType
                    defaultValue = false
                })
            ) { backStackEntry ->
                val openAdd = backStackEntry.arguments?.getBoolean("openAdd") ?: false
                ClientsScreen(
                    onNavigateToAddClient = { navController.navigate("add_client") },
                    viewModel = viewModel,
                    initialShowAddDialog = openAdd,
                    onNavigateToCreateInvoiceForClient = { clientId -> navController.navigate("create_invoice?clientId=$clientId") },
                    onNavigateToRecordPaymentForClient = { clientId -> navController.navigate("client_statement/$clientId") },
                    onNavigateToClientStatement = { clientId -> navController.navigate("client_statement/$clientId") },
                    onNavigateToClientProfile = { clientId -> navController.navigate("client_profile/$clientId") }
                )
            }

            // Items / Products inventory destination
            composable(
                route = "items?openAdd={openAdd}",
                arguments = listOf(navArgument("openAdd") {
                    type = NavType.BoolType
                    defaultValue = false
                })
            ) { backStackEntry ->
                val openAdd = backStackEntry.arguments?.getBoolean("openAdd") ?: false
                ItemScreen(
                    viewModel = viewModel,
                    initialShowAddDialog = openAdd,
                    onNavigateToAddItem = { navController.navigate("add_item") },
                    onNavigateToSupplier = { sup -> navController.navigate("supplier_items/${android.net.Uri.encode(sup)}") },
                    onNavigateToTopMovingItems = { navController.navigate("top_moving_items") }
                )
            }
            
            composable(
                "supplier_items/{supplierName}",
                arguments = listOf(navArgument("supplierName") { type = NavType.StringType })
            ) { backStackEntry ->
                val supplierName = backStackEntry.arguments?.getString("supplierName") ?: ""
                SupplierItemsScreen(
                    supplierName = java.net.URLDecoder.decode(supplierName, "UTF-8"),
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Invoices index listing
            composable("invoices") {
                InvoicesScreen(
                    viewModel = viewModel,
                    onNavigateToInvoiceDetails = { invoiceId -> navController.navigate("invoice_details/$invoiceId") }
                )
            }

            // Reports screen
            composable("reports") {
                ReportsScreen(
                    reportViewModel = reportViewModel,
                    onNavigateToTopMovingItems = { navController.navigate("top_moving_items") }
                )
            }

            // Shop settings
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToAlarmSettings = { navController.navigate("installment_alarm_settings") },
                    onNavigateToBackupRestore = { navController.navigate("backup_restore") },
                    onNavigateToAuditManagement = { navController.navigate("audit_management") }
                )
            }

            // Backup, Restore & Export center
            composable("backup_restore") {
                BackupRestoreScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            // Audit Log Management & Retention center
            composable("audit_management") {
                com.example.ui.screens.AuditLogManagementScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Create Invoice screen (Detailed or Quick)
            composable(
                route = "create_invoice?clientId={clientId}",
                arguments = listOf(navArgument("clientId") {
                    type = NavType.IntType
                    defaultValue = -1
                })
            ) { backStackEntry ->
                val clientIdArg = backStackEntry.arguments?.getInt("clientId")
                val clientId = if (clientIdArg == -1) null else clientIdArg
                CreateInvoiceScreen(
                    viewModel = viewModel,
                    initialClientId = clientId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInvoiceDetails = { invoiceId ->
                        navController.navigate("invoice_details/$invoiceId") {
                            popUpTo("dashboard") { inclusive = false }
                        }
                    },
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }

            // Bill invoice details receipt viewer
            composable(
                route = "invoice_details/{invoiceId}",
                arguments = listOf(navArgument("invoiceId") { type = NavType.IntType })
            ) { backStackEntry ->
                val invoiceId = backStackEntry.arguments?.getInt("invoiceId") ?: 0
                InvoiceDetailsScreen(
                    viewModel = viewModel,
                    invoiceId = invoiceId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Client ledger account statement
            composable(
                route = "client_statement/{clientId}",
                arguments = listOf(navArgument("clientId") { type = NavType.IntType })
            ) { backStackEntry ->
                val clientId = backStackEntry.arguments?.getInt("clientId") ?: 0
                ClientStatementScreen(
                    viewModel = viewModel,
                    clientId = clientId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Customer Profile detailed accounting screen
            composable(
                route = "client_profile/{clientId}",
                arguments = listOf(navArgument("clientId") { type = NavType.IntType })
            ) { backStackEntry ->
                val clientId = backStackEntry.arguments?.getInt("clientId") ?: 0
                CustomerProfileScreen(
                    viewModel = viewModel,
                    clientId = clientId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCreateInvoice = { id -> navController.navigate("create_invoice?clientId=$id") },
                    onNavigateToStatement = { id -> navController.navigate("client_statement/$id") }
                )
            }

            // Installments and Dual-Type Reminders Management
            composable("installments") {
                com.example.ui.screens.InstallmentsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToClient = { clientId -> navController.navigate("client_profile/$clientId") },
                    onNavigateToAlarmSettings = { navController.navigate("installment_alarm_settings") }
                )
            }

            // Dedicated Installment Alarm Profile & Settings
            composable("installment_alarm_settings") {
                com.example.ui.screens.InstallmentAlarmSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("returns_list") {
                com.example.ui.screens.returns.ReturnListScreen(
                    navController = navController,
                    viewModel = returnViewModel
                )
            }

            composable(
                route = "create_return/{type}",
                arguments = listOf(navArgument("type") { type = NavType.StringType })
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: "CUSTOMER"
                com.example.ui.screens.returns.CreateReturnScreen(
                    navController = navController,
                    appViewModel = viewModel,
                    returnViewModel = returnViewModel,
                    returnType = type
                )
            }

            composable(
                route = "return_details/{returnId}",
                arguments = listOf(navArgument("returnId") { type = NavType.IntType })
            ) { backStackEntry ->
                val returnId = backStackEntry.arguments?.getInt("returnId") ?: 0
                com.example.ui.screens.returns.ReturnDetailsScreen(
                    navController = navController,
                    viewModel = returnViewModel,
                    returnId = returnId
                )
            }

            // Most Active Items (الأصناف الأكثر حركة وسحب العملاء)
            composable(
                route = "top_moving_items?itemId={itemId}&itemName={itemName}",
                arguments = listOf(
                    navArgument("itemId") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                    navArgument("itemName") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getInt("itemId")?.let { if (it > 0) it else null }
                val rawName = backStackEntry.arguments?.getString("itemName") ?: ""
                val itemName = if (rawName.isNotBlank()) {
                    try { java.net.URLDecoder.decode(rawName, "UTF-8") } catch (e: Exception) { rawName }
                } else null

                com.example.ui.screens.TopMovingItemsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInvoiceDetails = { invoiceId -> navController.navigate("invoice_details/$invoiceId") },
                    onNavigateToClientProfile = { clientId -> navController.navigate("client_profile/$clientId") },
                    initialSelectedItemId = itemId,
                    initialSelectedItemName = itemName
                )
            }
        }
    }
}

data class NavigationItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)
