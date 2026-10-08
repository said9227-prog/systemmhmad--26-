import re

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

pattern = re.compile(r"""            // Check if item already exists: first by name \+ company if company specified, otherwise by name
            var existing: Item\? = null
            if \(supplierType == "شركة" && finalCompanyName\.isNotBlank\(\)\) \{
                existing = repository\.getItemByNameAndCompany\(name\.trim\(\), finalCompanyName\)
            \}
            if \(existing == null\) \{
                existing = repository\.getItemByName\(name\.trim\(\)\)
            \}""")

replacement = """            // Check if item already exists: STRICT MATCH by Name AND Supplier
            val allItems = repository.getAllItems()
            val existing = allItems.find { 
                it.name.equals(name.trim(), ignoreCase = true) &&
                it.supplierType == supplierType &&
                if (supplierType == "شركة") {
                    it.supplierCompanyName.equals(finalCompanyName, ignoreCase = true)
                } else {
                    it.individualSupplierName.equals(individualSupplierName.trim(), ignoreCase = true)
                }
            }"""

content = re.sub(pattern, replacement, content)

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w") as f:
    f.write(content)
