import re

with open("app/src/main/java/com/example/ui/screens/AddItemScreen.kt", "r") as f:
    content = f.read()

pattern = re.compile(r"                        OutlinedTextField\(\n                            value = minQuantityStr.*?\n                        \)", re.DOTALL)

replacement = """                        OutlinedTextField(
                            value = minQuantityStr,
                            onValueChange = { minQuantityStr = it; minQuantityError = null },
                            label = { Text("الحد الأدنى للتنبيه *") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = minQuantityError != null,
                            supportingText = minQuantityError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                    }

                    // Purchase/Sale Totals Display
                    val parsedQty = quantityStr.toIntOrNull() ?: 0
                    val parsedPurchase = purchasePriceStr.toDoubleOrNull() ?: 0.0
                    val parsedSale = sellingPriceStr.toDoubleOrNull() ?: 0.0
                    if (parsedQty > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (parsedPurchase > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("إجمالي تكلفة الشراء:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${FormatUtils.formatAmount(parsedPurchase * parsedQty)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (parsedSale > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("إجمالي قيمة البيع المتوقعة:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${FormatUtils.formatAmount(parsedSale * parsedQty)}", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                    }
                                }
                            }
                        }"""

content = pattern.sub(replacement, content)

with open("app/src/main/java/com/example/ui/screens/AddItemScreen.kt", "w") as f:
    f.write(content)
