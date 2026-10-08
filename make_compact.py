import os
import re

directory = "app/src/main/java/com/example/ui/screens/"

for filename in os.listdir(directory):
    if filename.endswith(".kt"):
        filepath = os.path.join(directory, filename)
        with open(filepath, "r") as f:
            content = f.read()

        # Reduce general paddings
        content = content.replace("padding(16.dp)", "padding(12.dp)")
        content = content.replace("padding(horizontal = 16.dp, vertical = 8.dp)", "padding(horizontal = 12.dp, vertical = 8.dp)")
        content = content.replace("padding(24.dp)", "padding(16.dp)")
        
        # Reduce spacings
        content = content.replace("Arrangement.spacedBy(16.dp)", "Arrangement.spacedBy(12.dp)")
        content = content.replace("Arrangement.spacedBy(24.dp)", "Arrangement.spacedBy(16.dp)")
        
        # Reduce font sizes slightly if they are huge (just keeping it safe)
        
        # Replace TopAppBars (already done, but just in case)
        
        with open(filepath, "w") as f:
            f.write(content)

print("Compacted paddings globally.")
