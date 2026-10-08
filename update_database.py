import re

with open("app/src/main/java/com/example/data/database/AppDatabase.kt", "r") as f:
    content = f.read()

# Update version from 13 to 14
content = re.sub(r"version\s*=\s*13", "version = 14", content)

# Add Entity class to the array
if "ItemPurchaseHistory::class" not in content:
    content = content.replace("ItemUnit::class,", "ItemUnit::class,\n        ItemPurchaseHistory::class,")

# Add DAO function
if "itemPurchaseHistoryDao" not in content:
    dao_func = """    abstract fun itemUnitDao(): ItemUnitDao
    abstract fun itemPurchaseHistoryDao(): ItemPurchaseHistoryDao"""
    content = content.replace("    abstract fun itemUnitDao(): ItemUnitDao", dao_func)

# Add Migration MIGRATION_13_14
migration_code = """        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(\"\"\"
                    CREATE TABLE IF NOT EXISTS `item_purchase_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemId` INTEGER NOT NULL,
                        `supplierCompanyId` INTEGER,
                        `supplierCompanyName` TEXT NOT NULL,
                        `supplierType` TEXT NOT NULL,
                        `purchaseDate` INTEGER NOT NULL,
                        `purchasePrice` REAL NOT NULL,
                        `quantity` INTEGER NOT NULL
                    )
                \"\"\".trimIndent())
            }
        }
        
        @Volatile"""
content = content.replace("@Volatile", migration_code)

# Add Migration to builder
content = content.replace("MIGRATION_11_12, MIGRATION_12_13)", "MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)")

with open("app/src/main/java/com/example/data/database/AppDatabase.kt", "w") as f:
    f.write(content)
