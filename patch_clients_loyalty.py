with open("app/src/main/java/com/example/ui/screens/ClientsScreen.kt", "r", encoding="utf-8") as f:
    code = f.read()

# 1. Update CustomerAccountingCard call in LazyColumn
old_call = """                        CustomerAccountingCard(
                            client = client,
                            currency = settings.currency,
                            lastActivityTimestamp = lastDate,"""

new_call = """                        CustomerAccountingCard(
                            client = client,
                            currency = settings.currency,
                            lastActivityTimestamp = lastDate,
                            loyaltyProfile = profile,"""

code = code.replace(old_call, new_call)

# 2. Update CustomerAccountingCard definition
old_def = """fun CustomerAccountingCard(
    client: Client,
    currency: String,
    lastActivityTimestamp: Long,
    onCardClick: () -> Unit,"""

new_def = """fun CustomerAccountingCard(
    client: Client,
    currency: String,
    lastActivityTimestamp: Long,
    loyaltyProfile: com.example.util.ClientLoyaltyProfile? = null,
    onCardClick: () -> Unit,"""

code = code.replace(old_def, new_def)

# 3. Add loyalty badges in Card header next to Deal type badge
old_badge_section = """                // Top Right: Deal Type Badge & More Menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {"""

new_badge_section = """                // Top Right: Loyalty Badge, Deal Type Badge & More Menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (loyaltyProfile != null && loyaltyProfile.primaryBadge != com.example.util.LoyaltyBadgeType.NORMAL) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = loyaltyProfile.primaryContainerColor
                        ) {
                            Text(
                                text = "${loyaltyProfile.primaryBadge.iconEmoji} ${loyaltyProfile.primaryBadge.title}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = loyaltyProfile.primaryColor
                            )
                        }
                    }"""

code = code.replace(old_badge_section, new_badge_section)

# 4. If overdue, add overdue warning strip right under financial status card
old_status_surface = """                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeBgColor
                        ) {
                            Text(
                                text = statusBadgeText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }"""

new_status_surface = """                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeBgColor
                        ) {
                            Text(
                                text = statusBadgeText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ⚠️ OVERDUE ALERT STRIP
            if (loyaltyProfile != null && loyaltyProfile.isOverdue) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "تأخر في السداد منذ ${loyaltyProfile.overdueDays} يوم - التزام السداد: ${(loyaltyProfile.paymentCommitmentRate * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }"""

code = code.replace(old_status_surface, new_status_surface)

with open("app/src/main/java/com/example/ui/screens/ClientsScreen.kt", "w", encoding="utf-8") as f:
    f.write(code)

print("Patched ClientsScreen with Loyalty features")
