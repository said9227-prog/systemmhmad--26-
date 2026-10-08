import re

with open("app/src/main/java/com/example/ui/screens/ItemScreen.kt", "r") as f:
    content = f.read()

# Update signature
old_sig = """@Composable
fun ItemScreen(
    viewModel: AppViewModel,
    initialBarcodeToSearch: String = "",
    initialShowAddDialog: Boolean = false,
    onNavigateToAddItem: () -> Unit = {}
) {"""

new_sig = """@Composable
fun ItemScreen(
    viewModel: AppViewModel,
    initialBarcodeToSearch: String = "",
    initialShowAddDialog: Boolean = false,
    onNavigateToAddItem: () -> Unit = {},
    onNavigateToSupplier: (String) -> Unit = {}
) {"""

content = content.replace(old_sig, new_sig)

# Find the supplier row and modify onClick behavior
# The current implementation says:
# .clickable { selectedSupplierFilter = sup }

pattern = re.compile(r"\.clickable \{ selectedSupplierFilter = sup \}")
# I want: if (sup != "الكل") onNavigateToSupplier(sup) else selectedSupplierFilter = sup
replacement = """.clickable { 
                                if (sup != "الكل") onNavigateToSupplier(sup) 
                                else selectedSupplierFilter = sup 
                            }"""

content = re.sub(pattern, replacement, content)

with open("app/src/main/java/com/example/ui/screens/ItemScreen.kt", "w") as f:
    f.write(content)
