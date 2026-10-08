with open("app/src/main/java/com/example/MainActivity.kt", "r", encoding="utf-8") as f:
    content = f.read()

target_clients = """                ClientsScreen(
                    onNavigateToAddClient = { navController.navigate("add_client") },
                    viewModel = viewModel,
                    initialShowAddDialog = openAdd,
                    onNavigateToCreateInvoiceForClient = { clientId -> navController.navigate("create_invoice?clientId=$clientId") },
                    onNavigateToRecordPaymentForClient = { clientId -> navController.navigate("client_statement/$clientId") },
                    onNavigateToClientStatement = { clientId -> navController.navigate("client_statement/$clientId") }
                )"""

replacement_clients = """                ClientsScreen(
                    onNavigateToAddClient = { navController.navigate("add_client") },
                    viewModel = viewModel,
                    initialShowAddDialog = openAdd,
                    onNavigateToCreateInvoiceForClient = { clientId -> navController.navigate("create_invoice?clientId=$clientId") },
                    onNavigateToRecordPaymentForClient = { clientId -> navController.navigate("client_statement/$clientId") },
                    onNavigateToClientStatement = { clientId -> navController.navigate("client_statement/$clientId") },
                    onNavigateToClientProfile = { clientId -> navController.navigate("client_profile/$clientId") }
                )"""

content = content.replace(target_clients, replacement_clients)

target_dest = """            // Client ledger account statement
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
            }"""

replacement_dest = """            // Client ledger account statement
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
            }"""

content = content.replace(target_dest, replacement_dest)

with open("app/src/main/java/com/example/MainActivity.kt", "w", encoding="utf-8") as f:
    f.write(content)

print("MainActivity patched successfully")
