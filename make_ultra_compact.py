import os
import re

directory = "app/src/main/java/com/example/ui/screens/"

for filename in os.listdir(directory):
    if filename.endswith(".kt"):
        filepath = os.path.join(directory, filename)
        with open(filepath, "r") as f:
            content = f.read()

        # Ultra compacting paddings
        content = content.replace("padding(16.dp)", "padding(12.dp)")
        content = content.replace("padding(horizontal = 16.dp)", "padding(horizontal = 12.dp)")
        content = content.replace("padding(horizontal=16.dp)", "padding(horizontal=12.dp)")
        content = content.replace("padding(vertical = 16.dp)", "padding(vertical = 12.dp)")
        
        content = content.replace("padding(14.dp)", "padding(10.dp)")
        content = content.replace("padding(12.dp)", "padding(8.dp)")
        content = content.replace("padding(horizontal = 12.dp, vertical = 12.dp)", "padding(8.dp)")
        content = content.replace("padding(horizontal = 14.dp, vertical = 6.dp)", "padding(horizontal = 10.dp, vertical = 6.dp)")

        # Ultra compacting spacings
        content = content.replace("Arrangement.spacedBy(16.dp)", "Arrangement.spacedBy(8.dp)")
        content = content.replace("Arrangement.spacedBy(14.dp)", "Arrangement.spacedBy(8.dp)")
        content = content.replace("Arrangement.spacedBy(12.dp)", "Arrangement.spacedBy(8.dp)")
        content = content.replace("Arrangement.spacedBy(10.dp)", "Arrangement.spacedBy(6.dp)")

        # Less rounded corners
        content = content.replace("RoundedCornerShape(16.dp)", "RoundedCornerShape(10.dp)")
        content = content.replace("RoundedCornerShape(14.dp)", "RoundedCornerShape(10.dp)")
        content = content.replace("RoundedCornerShape(12.dp)", "RoundedCornerShape(8.dp)")
        content = content.replace("RoundedCornerShape(24.dp)", "RoundedCornerShape(12.dp)")
        content = content.replace("RoundedCornerShape(20.dp)", "RoundedCornerShape(12.dp)")

        with open(filepath, "w") as f:
            f.write(content)

print("Ultra compacting applied.")
