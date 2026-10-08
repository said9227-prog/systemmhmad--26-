import os
import re

directory = "app/src/main/java/com/example/ui/screens/"

def replacer(match):
    full_match = match.group(0)
    if "scrollToTopOnFocus" not in full_match:
        # replace the first 'modifier = Modifier.' or 'modifier = Modifier,'
        full_match = re.sub(r"modifier\s*=\s*Modifier\.", "modifier = Modifier.scrollToTopOnFocus().", full_match, count=1)
        full_match = re.sub(r"modifier\s*=\s*Modifier,", "modifier = Modifier.scrollToTopOnFocus(),", full_match, count=1)
    return full_match

for filename in os.listdir(directory):
    if filename.endswith(".kt"):
        filepath = os.path.join(directory, filename)
        with open(filepath, "r") as f:
            content = f.read()

        original_content = content
        
        # We find OutlinedTextField( ... ) or TextField( ... ) or SearchableDropdownString( ... )
        # But matching balanced parentheses is hard in regex.
        # However, we can just replace "modifier = Modifier" -> "modifier = Modifier.scrollToTopOnFocus()" on lines that contain "OutlinedTextField" etc? No, they span multiple lines.

        # A simpler way: Find all occurrences of `OutlinedTextField`, `TextField`, `SearchableDropdownString`, `CustomTextField`
        # and then inject `.scrollToTopOnFocus()` into their `modifier = Modifier...`
        
        # Let's use a simpler heuristic: just search for `modifier = Modifier` inside the file, but that applies it to Cards and Buttons too!
        # The user specifically said "عندما اضغط على مربع" (when I click on a box), implying text inputs.
        
        # Since I can't easily parse Kotlin with regex, I'll do this:
        # Just split the text by "OutlinedTextField", "TextField", "SearchableDropdownString".
        
        for component in ["OutlinedTextField", "TextField", "SearchableDropdownString"]:
            parts = content.split(component + "(")
            if len(parts) > 1:
                new_content = parts[0]
                for part in parts[1:]:
                    # Find the first `modifier = Modifier`
                    part = re.sub(r"modifier\s*=\s*Modifier\.", "modifier = Modifier.scrollToTopOnFocus().", part, count=1)
                    part = re.sub(r"modifier\s*=\s*Modifier,", "modifier = Modifier.scrollToTopOnFocus(),", part, count=1)
                    new_content += component + "(" + part
                content = new_content

        if content != original_content:
            if "import com.example.util.scrollToTopOnFocus" not in content:
                content = content.replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier\nimport com.example.util.scrollToTopOnFocus")
                if "import com.example.util.scrollToTopOnFocus" not in content:
                   content = "import com.example.util.scrollToTopOnFocus\n" + content
            
            with open(filepath, "w") as f:
                f.write(content)

