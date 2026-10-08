with open("app/src/main/java/com/example/ui/screens/CustomerProfileScreen.kt", "r", encoding="utf-8") as f:
    code = f.read()

# Insert the Loyalty Card right above Quick Actions Bar
target_metrics = """            // ⚡ 3. QUICK ACTIONS BAR
            QuickActionsBar("""

loyalty_section = """            // ⭐ LOYALTY & PAYMENT BEHAVIOR CARD
            LoyaltyBehaviorCard(
                loyaltyProfile = loyaltyProfile,
                onSendReminder = {
                    if (loyaltyProfile.isOverdue) {
                        WhatsAppHelper.sendWhatsAppMessage(
                            context = context,
                            phone = client.phone,
                            messageText = loyaltyProfile.overdueNoticeMessage
                        )
                    } else if (loyaltyProfile.isLoyal) {
                        WhatsAppHelper.sendWhatsAppMessage(
                            context = context,
                            phone = client.phone,
                            messageText = loyaltyProfile.loyaltyAppreciationMessage
                        )
                    }
                }
            )

            // ⚡ 3. QUICK ACTIONS BAR
            QuickActionsBar("""

code = code.replace(target_metrics, loyalty_section)

# Add LoyaltyBehaviorCard composable at bottom of CustomerProfileScreen.kt
card_composable = """
// -------------------------------------------------------------
// COMPONENT: LOYALTY & PAYMENT BEHAVIOR CARD
// -------------------------------------------------------------
@Composable
fun LoyaltyBehaviorCard(
    loyaltyProfile: com.example.util.ClientLoyaltyProfile,
    onSendReminder: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = loyaltyProfile.primaryContainerColor.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, loyaltyProfile.primaryColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = loyaltyProfile.primaryBadge.iconEmoji,
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = loyaltyProfile.primaryBadge.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = loyaltyProfile.primaryColor
                        )
                        Text(
                            text = loyaltyProfile.primaryBadge.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = loyaltyProfile.primaryColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "نقاط التقييم: ${loyaltyProfile.loyaltyScore}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = loyaltyProfile.primaryColor
                    )
                }
            }

            HorizontalDivider(color = loyaltyProfile.primaryColor.copy(alpha = 0.2f))

            // Behavioral metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("نسبة الالتزام بالسداد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${(loyaltyProfile.paymentCommitmentRate * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (loyaltyProfile.paymentCommitmentRate >= 0.75) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("متوسط سرعة السداد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (loyaltyProfile.averageSettlementDays > 0) "${loyaltyProfile.averageSettlementDays.toInt()} يوم" else "فوري",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("حالة السداد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (loyaltyProfile.isOverdue) "متأخر (${loyaltyProfile.overdueDays} يوم)" else "ملتزم ومحدث",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (loyaltyProfile.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // If Overdue or Loyal appreciation button
            if (loyaltyProfile.isOverdue) {
                OutlinedButton(
                    onClick = onSendReminder,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إرسال إشعار تذكير بالسداد عبر الواتساب", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else if (loyaltyProfile.isLoyal) {
                OutlinedButton(
                    onClick = onSendReminder,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إرسال رسالة شكر وتقدير عبر الواتساب", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
"""

code = code + card_composable

if "import androidx.compose.foundation.BorderStroke" not in code:
    code = code.replace("import androidx.compose.animation.*", "import androidx.compose.animation.*\nimport androidx.compose.foundation.BorderStroke")

with open("app/src/main/java/com/example/ui/screens/CustomerProfileScreen.kt", "w", encoding="utf-8") as f:
    f.write(code)

print("Patched CustomerProfileScreen with Loyalty")
