import re

with open("app/src/main/java/com/example/ui/screens/AddItemScreen.kt", "r") as f:
    content = f.read()

pattern = re.compile(r"        if \(expanded && filteredOptions\.isNotEmpty\(\)\) \{.*?\n        \}", re.DOTALL)
replacement = """        if (expanded) {
            val showAddOption = value.isNotEmpty() && !options.any { it.equals(value, ignoreCase = true) }
            if (filteredOptions.isNotEmpty() || showAddOption) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    filteredOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            }
                        )
                    }
                    if (showAddOption) {
                        DropdownMenuItem(
                            text = { Text("إضافة '$value' كجديد", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                            onClick = {
                                onValueChange(value)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }"""

content = pattern.sub(replacement, content)

with open("app/src/main/java/com/example/ui/screens/AddItemScreen.kt", "w") as f:
    f.write(content)
