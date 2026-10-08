import re

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

# Add import
if "import com.example.data.model.ItemPurchaseHistory" not in content:
    content = content.replace("import com.example.data.model.ItemUnit", "import com.example.data.model.ItemUnit\nimport com.example.data.model.ItemPurchaseHistory")

# Find the block where item is updated and inserted.
pattern = re.compile(r"""                repository\.updateItem\(updatedItem\)
                repository\.insertLog\("تحديث صنف", "المخزون", "تمت إضافة كمية \(\$quantity\) للصنف \$\{existing\.name\}\. الكمية الإجمالية الجديدة: \$newTotalQty"\)
                triggerAutoDriveBackup\(\)
                onSuccess\(true, newTotalQty\)
            \} else \{
                val item = Item\(""")

replacement = """                repository.updateItem(updatedItem)
                
                // Record purchase history
                if (quantity > 0 || purchasePrice > 0) {
                    val finalSupplierName = if (supplierType == "شركة") finalCompanyName else individualSupplierName.trim()
                    repository.insertItemPurchaseHistory(ItemPurchaseHistory(
                        itemId = existing.id,
                        supplierCompanyId = finalCompanyId,
                        supplierCompanyName = finalSupplierName,
                        supplierType = supplierType,
                        purchaseDate = purchaseDate,
                        purchasePrice = purchasePrice,
                        quantity = quantity
                    ))
                }
                
                repository.insertLog("تحديث صنف", "المخزون", "تمت إضافة كمية ($quantity) للصنف ${existing.name}. الكمية الإجمالية الجديدة: $newTotalQty")
                triggerAutoDriveBackup()
                onSuccess(true, newTotalQty)
            } else {
                val item = Item("""
content = content.replace("""                repository.updateItem(updatedItem)
                repository.insertLog("تحديث صنف", "المخزون", "تمت إضافة كمية ($quantity) للصنف ${existing.name}. الكمية الإجمالية الجديدة: $newTotalQty")
                triggerAutoDriveBackup()
                onSuccess(true, newTotalQty)
            } else {
                val item = Item(""", replacement)

# Now for the insert block
pattern2 = re.compile(r"""                val id = repository\.insertItem\(item\)\.toInt\(\)
                repository\.insertLog\("إضافة صنف", "المخزون", "تمت إضافة الصنف \$\{item\.name\} بكمية \$quantity"\)
                triggerAutoDriveBackup\(\)
                onSuccess\(false, quantity\)
            \}""")

replacement2 = """                val id = repository.insertItem(item).toInt()
                
                // Record purchase history
                if (quantity > 0 || purchasePrice > 0) {
                    val finalSupplierName = if (supplierType == "شركة") finalCompanyName else individualSupplierName.trim()
                    repository.insertItemPurchaseHistory(ItemPurchaseHistory(
                        itemId = id,
                        supplierCompanyId = finalCompanyId,
                        supplierCompanyName = finalSupplierName,
                        supplierType = supplierType,
                        purchaseDate = purchaseDate,
                        purchasePrice = purchasePrice,
                        quantity = quantity
                    ))
                }
                
                repository.insertLog("إضافة صنف", "المخزون", "تمت إضافة الصنف ${item.name} بكمية $quantity")
                triggerAutoDriveBackup()
                onSuccess(false, quantity)
            }"""

content = re.sub(pattern2, replacement2, content)

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w") as f:
    f.write(content)
