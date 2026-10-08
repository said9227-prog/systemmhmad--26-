import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# 1. Update ItemScreen composable to add onNavigateToSupplier
item_screen_pattern = re.compile(r"""                ItemScreen\(
                    viewModel = viewModel,
                    initialShowAddDialog = openAdd,
                    onNavigateToAddItem = \{ navController\.navigate\("add_item"\) \}
                \)""")

item_screen_replacement = """                ItemScreen(
                    viewModel = viewModel,
                    initialShowAddDialog = openAdd,
                    onNavigateToAddItem = { navController.navigate("add_item") },
                    onNavigateToSupplier = { sup -> navController.navigate("supplier_items/${android.net.Uri.encode(sup)}") }
                )"""

content = re.sub(item_screen_pattern, item_screen_replacement, content)

# 2. Add SupplierItemsScreen composable route right after it
supplier_route = """            }
            
            composable(
                "supplier_items/{supplierName}",
                arguments = listOf(navArgument("supplierName") { type = NavType.StringType })
            ) { backStackEntry ->
                val supplierName = backStackEntry.arguments?.getString("supplierName") ?: ""
                SupplierItemsScreen(
                    supplierName = java.net.URLDecoder.decode(supplierName, "UTF-8"),
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )"""

content = content.replace("            }\n\n            // Invoices index listing", supplier_route + "\n            }\n\n            // Invoices index listing")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
