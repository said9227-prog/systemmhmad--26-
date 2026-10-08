import re

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

# 1. Replace CurrencyOverviewCard (All currencies mode)
card_pattern = re.compile(r"@Composable\nfun CurrencyOverviewCard\(.*?\n\n@Composable\nfun CurrencyCustomerCard", re.DOTALL)
card_replacement = """@Composable
fun CurrencyOverviewCard(
    summary: CurrencyFinancialSummary,
    onSelectCurrency: () -> Unit,
    onNavigateToInstallments: () -> Unit
) {
    val curr = summary.currency

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectCurrency() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = curr.flag, fontSize = 24.sp)
                    Text(
                        text = curr.nameAr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(Icons.Default.ChevronLeft, contentDescription = "عرض التفاصيل", tint = MaterialTheme.colorScheme.primary)
            }

            // Main Metric: Net Balance
            val netColor = when {
                summary.netBalance > 0 -> Color(0xFFEF4444)
                summary.netBalance < 0 -> Color(0xFF10B981)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column {
                Text("صافي الرصيد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${com.example.util.FormatUtils.formatAmount(summary.netBalance)} ${curr.symbol}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = netColor
                )
            }

            // Secondary Metrics Row (Sales, Receipts, Debts)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "مبيعات", amount = summary.totalSales, symbol = curr.symbol, color = Color(0xFF4F46E5))
                MetricItem(label = "مقبوضات", amount = summary.totalReceipts, symbol = curr.symbol, color = Color(0xFF059669))
                MetricItem(label = "ديون", amount = summary.totalDebts, symbol = curr.symbol, color = Color(0xFFDC2626))
            }

            // Installment alerts
            if (summary.installmentsOverdueCount > 0 || summary.installmentsDueTodayCount > 0) {
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToInstallments() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                        Text(
                            text = "أقساط مستحقة/متأخرة (${summary.installmentsDueTodayCount + summary.installmentsOverdueCount})",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricItem(label: String, amount: Double, symbol: String, color: Color) {
    Column {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "${com.example.util.FormatUtils.formatAmount(amount)} $symbol",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun CurrencyCustomerCard"""
content = card_pattern.sub(card_replacement, content)


# 2. Replace SingleCurrencyFinancialCardsGrid (Single currency mode)
grid_pattern = re.compile(r"fun SingleCurrencyFinancialCardsGrid\(.*?\n\n@Composable\nfun CollectionEfficiencyCard", re.DOTALL)
grid_replacement = """fun SingleCurrencyFinancialCardsGrid(summary: CurrencyFinancialSummary) {
    val curr = summary.currency
    
    // Net Balance Hero
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("صافي الرصيد", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val netColor = when {
                summary.netBalance > 0 -> Color(0xFFEF4444)
                summary.netBalance < 0 -> Color(0xFF10B981)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = "${com.example.util.FormatUtils.formatAmount(summary.netBalance)} ${curr.symbol}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = netColor
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 3 Mini Cards Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sales
        Surface(
            modifier = Modifier.weight(1f),
            color = Color(0xFF6366F1).copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("المبيعات", fontSize = 11.sp, color = Color(0xFF4F46E5))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = com.example.util.FormatUtils.formatAmount(summary.totalSales),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4F46E5)
                )
            }
        }
        
        // Receipts
        Surface(
            modifier = Modifier.weight(1f),
            color = Color(0xFF10B981).copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("المقبوضات", fontSize = 11.sp, color = Color(0xFF059669))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = com.example.util.FormatUtils.formatAmount(summary.totalReceipts),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF059669)
                )
            }
        }
        
        // Debts
        Surface(
            modifier = Modifier.weight(1f),
            color = Color(0xFFEF4444).copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("الديون", fontSize = 11.sp, color = Color(0xFFDC2626))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = com.example.util.FormatUtils.formatAmount(summary.totalDebts),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626)
                )
            }
        }
    }
}

@Composable
fun CollectionEfficiencyCard"""
content = grid_pattern.sub(grid_replacement, content)

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)
