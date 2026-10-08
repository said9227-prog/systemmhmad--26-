import re

with open("app/src/main/java/com/example/ui/screens/AddItemScreen.kt", "r") as f:
    content = f.read()

# Replace modifier in OutlinedTextFields
# We look for OutlinedTextField( ... modifier = Modifier.xxx )
def replacer(match):
    full_match = match.group(0)
    if "scrollToTopOnFocus" not in full_match:
        return full_match.replace("modifier = Modifier", "modifier = Modifier.scrollToTopOnFocus(scrollState)")
    return full_match

content = re.sub(r"OutlinedTextField\s*\([^)]+modifier\s*=\s*Modifier[^)]+\)", replacer, content, flags=re.DOTALL)
content = re.sub(r"SearchableDropdownString\s*\([^)]+modifier\s*=\s*Modifier[^)]+\)", replacer, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/ui/screens/AddItemScreen.kt", "w") as f:
    f.write(content)

