import re

with open("app/src/main/java/com/example/ui/screens/CustomerProfileScreen.kt", "r") as f:
    content = f.read()

pattern = re.compile(r"@Composable\nfun ProfileSectionCard\(.*?\n        }\n    }\n}", re.DOTALL)
replacement = """@Composable
fun ProfileSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}"""

content = pattern.sub(replacement, content)

with open("app/src/main/java/com/example/ui/screens/CustomerProfileScreen.kt", "w") as f:
    f.write(content)
