import re

with open("app/src/main/java/com/example/data/model/Entities.kt", "r") as f:
    content = f.read()

# Append new entity
new_entity = """
@JsonClass(generateAdapter = true)
@Entity(tableName = "item_purchase_history")
data class ItemPurchaseHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemId: Int,
    val supplierCompanyId: Int?,
    val supplierCompanyName: String,
    val supplierType: String,
    val purchaseDate: Long,
    val purchasePrice: Double,
    val quantity: Int
)
"""

if "item_purchase_history" not in content:
    content += new_entity

with open("app/src/main/java/com/example/data/model/Entities.kt", "w") as f:
    f.write(content)

