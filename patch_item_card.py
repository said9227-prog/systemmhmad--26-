import re

with open("app/src/main/java/com/example/ui/screens/ItemScreen.kt", "r") as f:
    content = f.read()

# Modify ItemCard signature
old_sig = """@Composable
fun ItemCard(
    item: Item,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {"""

new_sig = """@Composable
fun ItemCard(
    item: Item,
    history: List<ItemPurchaseHistory>,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit = {}
) {"""

content = content.replace(old_sig, new_sig)

# Add clickable to Card
content = content.replace("            .shadow(1.dp, RoundedCornerShape(10.dp)),", "            .shadow(1.dp, RoundedCornerShape(10.dp))\n            .clickable { onClick() },")

# Re-design the card content as user requested
# The user wants:
# الصنف
# المورد
# آخر شراء
# سعر الشراء
# السعر السابق
# التغير

# Let's completely replace the Card's content inside ItemCard!
