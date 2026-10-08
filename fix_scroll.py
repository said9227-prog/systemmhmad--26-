import re

with open("app/src/main/java/com/example/ui/screens/AddItemScreen.kt", "r") as f:
    content = f.read()

# Add import
if "import com.example.util.scrollToTopOnFocus" not in content:
    content = content.replace("import com.example.util.FormatUtils", "import com.example.util.FormatUtils\nimport com.example.util.scrollToTopOnFocus\nimport androidx.compose.foundation.ScrollState")

# Add scroll state var
pattern_var = r"    val photoPickerLauncher = rememberLauncherForActivityResult"
replacement_var = "    val scrollState = rememberScrollState()\n    val photoPickerLauncher = rememberLauncherForActivityResult"
content = re.sub(pattern_var, replacement_var, content)

# Replace verticalScroll
content = content.replace(".verticalScroll(rememberScrollState())", ".verticalScroll(scrollState)")

# Apply to OutlinedTextFields and SearchableDropdownString
# We can search for `modifier = Modifier` inside OutlinedTextField and SearchableDropdownString and append `.scrollToTopOnFocus(scrollState)`
# But wait, we need to be careful not to apply it twice.

with open("app/src/main/java/com/example/ui/screens/AddItemScreen.kt", "w") as f:
    f.write(content)
