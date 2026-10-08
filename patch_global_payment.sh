#!/bin/bash
cat << 'INNER_EOF' >> app/src/main/java/com/example/ui/screens/DashboardScreen.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalPaymentDialog(
    clients: List<com.example.data.model.Client>,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (clientId: Int, amount: Double, method: String, notes: String, currency: String) -> Unit
) {
    var selectedClient by remember { mutableStateOf<com.example.data.model.Client?>(null) }
    var expandedClient by remember { mutableStateOf(false) }
    
    var amountStr by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf(defaultCurrency.ifBlank { "الريال اليمني" }) }
    var expandedCurrency by remember { mutableStateOf(false) }
    
    var method by remember { mutableStateOf("نقدي") }
    var expandedMethod by remember { mutableStateOf(false) }
    
    var notes by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val currencies = listOf("الريال اليمني", "الريال السعودي", "الدولار الأمريكي")
    val methods = listOf("نقدي", "إيداع", "تحويل", "شيك")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Payments,
                    contentDescription = null,
                    tint = Color(0xFF10B981)
                )
                Text("تسجيل دفعة سداد جديدة", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Client Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedClient,
                    onExpandedChange = { expandedClient = !expandedClient }
                ) {
                    OutlinedTextField(
                        value = selectedClient?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("العميل *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClient) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        isError = isError && selectedClient == null
                    )
                    ExposedDropdownMenu(
                        expanded = expandedClient,
                        onDismissRequest = { expandedClient = false }
                    ) {
                        clients.forEach { client ->
                            DropdownMenuItem(
                                text = { Text(client.name) },
                                onClick = {
                                    selectedClient = client
                                    expandedClient = false
                                }
                            )
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; isError = false },
                    label = { Text("المبلغ *") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = isError && amountStr.toDoubleOrNull() == null
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Currency Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedCurrency,
                        onExpandedChange = { expandedCurrency = !expandedCurrency },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedCurrency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("العملة") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCurrency) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCurrency,
                            onDismissRequest = { expandedCurrency = false }
                        ) {
                            currencies.forEach { curr ->
                                DropdownMenuItem(
                                    text = { Text(curr) },
                                    onClick = {
                                        selectedCurrency = curr
                                        expandedCurrency = false
                                    }
                                )
                            }
                        }
                    }

                    // Method Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedMethod,
                        onExpandedChange = { expandedMethod = !expandedMethod },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = method,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("طريقة الدفع") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMethod) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedMethod,
                            onDismissRequest = { expandedMethod = false }
                        ) {
                            methods.forEach { met ->
                                DropdownMenuItem(
                                    text = { Text(met) },
                                    onClick = {
                                        method = met
                                        expandedMethod = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (amt != null && amt > 0 && selectedClient != null) {
                        onSave(selectedClient!!.id, amt, method, notes, selectedCurrency)
                    } else {
                        isError = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("حفظ وتسجيل الدفعة", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إلغاء", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
INNER_EOF
