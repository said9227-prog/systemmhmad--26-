with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

funcs = """
    fun getItemPurchaseHistory(itemId: Int): kotlinx.coroutines.flow.Flow<List<ItemPurchaseHistory>> {
        return repository.getPurchaseHistoryForItem(itemId)
    }
"""
content = content.replace("    // --- Item, Category, Unit, and Company Operations ---", funcs + "\n    // --- Item, Category, Unit, and Company Operations ---")

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w") as f:
    f.write(content)
