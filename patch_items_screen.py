import re

with open("app/src/main/java/com/example/ui/screens/ItemScreen.kt", "r") as f:
    content = f.read()

# 1. Add supplier state variables
pattern_vars = re.compile(r"    var selectedCategoryFilter by remember \{ mutableStateOf\(\"الكل\"\) \}")
replacement_vars = """    var selectedCategoryFilter by remember { mutableStateOf("الكل") }
    var selectedSupplierFilter by remember { mutableStateOf("الكل") }"""
content = re.sub(pattern_vars, replacement_vars, content)

# 2. Add suppliers list logic
pattern_cats = re.compile(r"""    // Dynamic unique categories
    val categories = remember\(itemsList\) \{
        val list = mutableListOf\("الكل"\)
        list\.addAll\(itemsList\.map \{ it\.category \}\.filter \{ it\.isNotBlank\(\) \}\.distinct\(\)\)
        list
    \}""")
replacement_cats = """    // Dynamic unique categories
    val categories = remember(itemsList) {
        val list = mutableListOf("الكل")
        list.addAll(itemsList.map { it.category }.filter { it.isNotBlank() }.distinct())
        list
    }
    
    // Dynamic unique suppliers
    val suppliers = remember(itemsList) {
        val list = mutableListOf("الكل")
        val supplierNames = itemsList.map { 
            if (it.supplierType == "شركة") it.supplierCompanyName else it.individualSupplierName 
        }.filter { it.isNotBlank() }.distinct().sorted()
        list.addAll(supplierNames)
        list
    }"""
content = re.sub(pattern_cats, replacement_cats, content)

# 3. Update filter logic
pattern_filter = re.compile(r"""    val filteredItems = remember\(itemsList, searchQuery, selectedCategoryFilter, showOnlyLowStock\) \{
        itemsList\.filter \{ item ->
            val matchesSearch = item\.name\.contains\(searchQuery, ignoreCase = true\) \|\|
                                 item\.barcode\.contains\(searchQuery\) \|\|
                                item\.category\.contains\(searchQuery, ignoreCase = true\)
            
            val matchesCat = selectedCategoryFilter == "الكل" \|\|
                              item\.category == selectedCategoryFilter
                              
            val matchesLowStock = !showOnlyLowStock \|\|
                                   item\.quantity <= item\.minQuantityAlert

            matchesSearch && matchesCat && matchesLowStock
        \}
    \}""")
replacement_filter = """    val filteredItems = remember(itemsList, searchQuery, selectedCategoryFilter, selectedSupplierFilter, showOnlyLowStock) {
        itemsList.filter { item ->
            val matchesSearch = item.name.contains(searchQuery, ignoreCase = true) ||
                                 item.barcode.contains(searchQuery) ||
                                item.category.contains(searchQuery, ignoreCase = true)
            
            val matchesCat = selectedCategoryFilter == "الكل" ||
                              item.category == selectedCategoryFilter
                              
            val itemSupplierName = if (item.supplierType == "شركة") item.supplierCompanyName else item.individualSupplierName
            val matchesSupplier = selectedSupplierFilter == "الكل" || itemSupplierName == selectedSupplierFilter
                              
            val matchesLowStock = !showOnlyLowStock ||
                                   item.quantity <= item.minQuantityAlert

            matchesSearch && matchesCat && matchesSupplier && matchesLowStock
        }
    }"""
content = re.sub(pattern_filter, replacement_filter, content)

with open("app/src/main/java/com/example/ui/screens/ItemScreen.kt", "w") as f:
    f.write(content)
