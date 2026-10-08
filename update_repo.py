import re

with open("app/src/main/java/com/example/data/repository/AppRepository.kt", "r") as f:
    content = f.read()

repo_funcs = """
    // --- Item Purchase History ---
    suspend fun insertItemPurchaseHistory(history: ItemPurchaseHistory): Long = db.itemPurchaseHistoryDao().insertPurchaseHistory(history)
    fun getPurchaseHistoryForItem(itemId: Int) = db.itemPurchaseHistoryDao().getPurchaseHistoryForItem(itemId)
    suspend fun getPurchaseHistoryForItemSync(itemId: Int) = db.itemPurchaseHistoryDao().getPurchaseHistoryForItemSync(itemId)
    suspend fun deletePurchaseHistoryForItem(itemId: Int) = db.itemPurchaseHistoryDao().deletePurchaseHistoryForItem(itemId)
"""

if "insertItemPurchaseHistory" not in content:
    content = content.replace("    // --- Audit Logs ---", repo_funcs + "\n    // --- Audit Logs ---")
    
    # Also need to import ItemPurchaseHistory
    content = content.replace("import com.example.data.model.ItemUnit", "import com.example.data.model.ItemUnit\nimport com.example.data.model.ItemPurchaseHistory")

    with open("app/src/main/java/com/example/data/repository/AppRepository.kt", "w") as f:
        f.write(content)

