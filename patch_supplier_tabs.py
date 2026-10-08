import re

with open("app/src/main/java/com/example/ui/screens/ItemScreen.kt", "r") as f:
    content = f.read()

supplier_ui = """            }
            
            // Suppliers horizontal slider
            ScrollableTabRow(
                selectedTabIndex = suppliers.indexOf(selectedSupplierFilter).coerceAtLeast(0),
                edgePadding = 0.dp,
                divider = {},
                indicator = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                suppliers.forEachIndexed { _, sup ->
                    val isSelected = selectedSupplierFilter == sup
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { selectedSupplierFilter = sup }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (sup != "الكل") {
                                Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(12.dp), tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = if (sup == "الكل") "كل الموردين" else sup,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                         else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))"""

pattern = re.compile(r"            \}\n            Spacer\(modifier = Modifier\.height\(8\.dp\)\)\n\s*// Inventory Items List")

content = re.sub(pattern, supplier_ui + "\n            // Inventory Items List", content)

with open("app/src/main/java/com/example/ui/screens/ItemScreen.kt", "w") as f:
    f.write(content)
