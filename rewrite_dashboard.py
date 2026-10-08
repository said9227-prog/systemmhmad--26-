import re

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

# 1. Replace StoreHeader
store_header_pattern = re.compile(r"@Composable\nfun StoreHeader\(.*?\n\n@OptIn", re.DOTALL)
store_header_replacement = """@Composable
fun StoreHeader(
    storeName: String,
    onEditClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "مرحباً بك",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = storeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        IconButton(
            onClick = onAboutClick,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "حول",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn"""
content = store_header_pattern.sub(store_header_replacement, content)


# 2. Replace CurrencyAndPeriodFilterSection
filter_section_pattern = re.compile(r"@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun CurrencyAndPeriodFilterSection\(.*?\n\n@Composable\nfun AllCurrenciesExplainingBanner", re.DOTALL)
filter_section_replacement = """@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyAndPeriodFilterSection(
    selectedCurrencyCode: String,
    availableCurrencies: List<CurrencyMeta>,
    selectedPeriod: DashboardPeriod,
    onSelectCurrency: (String) -> Unit,
    onSelectPeriod: (DashboardPeriod) -> Unit,
    totalRegisteredClients: Int,
    currentSummary: CurrencyFinancialSummary?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Horizontal scrolling Currency Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                val isSelected = selectedCurrencyCode == "ALL"
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCurrency("ALL") },
                    label = { Text("🌎 جميع العملات", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    shape = RoundedCornerShape(16.dp)
                )
            }
            items(availableCurrencies) { curr ->
                val isSelected = selectedCurrencyCode == curr.code
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCurrency(curr.code) },
                    label = { Text("${curr.flag} ${curr.nameAr}", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        // Horizontal scrolling Time Period Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(DashboardPeriod.values()) { period ->
                val isSelected = selectedPeriod == period
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPeriod(period) },
                    label = { Text(period.labelAr, fontSize = 12.sp) },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

@Composable
fun AllCurrenciesExplainingBanner"""
content = filter_section_pattern.sub(filter_section_replacement, content)

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)
