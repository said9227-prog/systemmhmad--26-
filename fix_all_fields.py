import re

with open("app/src/main/java/com/example/ui/screens/AddItemScreen.kt", "r") as f:
    content = f.read()

# Make sure we import what we need
if "import com.example.util.scrollToTopOnFocus" not in content:
    content = content.replace("import com.example.util.FormatUtils", "import com.example.util.FormatUtils\nimport com.example.util.scrollToTopOnFocus\nimport androidx.compose.foundation.ScrollState")

# We replace any "modifier = Modifier" inside OutlinedTextField with "modifier = Modifier.scrollToTopOnFocus(scrollState)"
# A safe way is to split by "OutlinedTextField(" and "SearchableDropdownString("

def inject_modifier(text, component_name):
    parts = text.split(component_name + "(")
    if len(parts) == 1: return text
    
    res = parts[0]
    for part in parts[1:]:
        # Find the first "modifier = Modifier" inside this part (before the next main component)
        # It's a bit hacky, let's just replace "modifier = Modifier" -> "modifier = Modifier.scrollToTopOnFocus(scrollState)" 
        # but only the FIRST occurrence in this block, up to the next OutlinedTextField.
        # Actually, simpler: replace "modifier = Modifier." with "modifier = Modifier.scrollToTopOnFocus(scrollState)."
        # But what if there is "modifier = Modifier,"? Replace "modifier = Modifier," with "modifier = Modifier.scrollToTopOnFocus(scrollState),"
        
        # We will just do a simple replace on the part
        part = re.sub(r"modifier\s*=\s*Modifier\.", "modifier = Modifier.scrollToTopOnFocus(scrollState).", part, count=1)
        part = re.sub(r"modifier\s*=\s*Modifier,", "modifier = Modifier.scrollToTopOnFocus(scrollState),", part, count=1)
        res += component_name + "(" + part
    return res

content = inject_modifier(content, "OutlinedTextField")
content = inject_modifier(content, "SearchableDropdownString")

with open("app/src/main/java/com/example/ui/screens/AddItemScreen.kt", "w") as f:
    f.write(content)
