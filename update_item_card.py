import re

with open("app/src/main/java/com/example/ui/screens/ItemScreen.kt", "r") as f:
    content = f.read()

import_history = "import com.example.data.model.ItemPurchaseHistory"
if import_history not in content:
    content = content.replace("import com.example.data.model.Item", "import com.example.data.model.Item\n" + import_history)
if "import com.example.util.DateTimeUtils" not in content:
    content = content.replace("import com.example.util.FormatUtils", "import com.example.util.FormatUtils\nimport com.example.util.DateTimeUtils")

# Modify LazyColumn items block
lazy_pattern = re.compile(r"""                    items\(filteredItems, key = \{ it\.id \}\) \{ item ->
                        ItemCard\(
                            item = item,
                            currency = settings\.currency,
                            onEdit = \{ itemToEdit = item \},
                            onDelete = \{ viewModel\.deleteItem\(item\) \}
                        \)
                    \}""")

lazy_replacement = """                    items(filteredItems, key = { it.id }) { item ->
                        val history by viewModel.getItemPurchaseHistory(item.id).collectAsState(initial = emptyList())
                        ItemCard(
                            item = item,
                            history = history,
                            currency = settings.currency,
                            onEdit = { itemToEdit = item },
                            onDelete = { viewModel.deleteItem(item) }
                        )
                    }"""

content = re.sub(lazy_pattern, lazy_replacement, content)

with open("app/src/main/java/com/example/ui/screens/ItemScreen.kt", "w") as f:
    f.write(content)
