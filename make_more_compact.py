import os

directory = "app/src/main/java/com/example/ui/screens/"

for filename in os.listdir(directory):
    if filename.endswith(".kt"):
        filepath = os.path.join(directory, filename)
        with open(filepath, "r") as f:
            content = f.read()

        # Reduce specific paddings and spacings further
        content = content.replace("padding(horizontal = 16.dp)", "padding(horizontal = 12.dp)")
        content = content.replace("padding(vertical = 16.dp)", "padding(vertical = 12.dp)")
        content = content.replace("padding(horizontal = 24.dp)", "padding(horizontal = 16.dp)")
        content = content.replace("Arrangement.spacedBy(16.dp)", "Arrangement.spacedBy(8.dp)")
        content = content.replace("Arrangement.spacedBy(12.dp)", "Arrangement.spacedBy(8.dp)")
        
        # Rounder corners are okay, but make them slightly less pronounced to look more compact
        content = content.replace("RoundedCornerShape(16.dp)", "RoundedCornerShape(12.dp)")
        content = content.replace("RoundedCornerShape(24.dp)", "RoundedCornerShape(16.dp)")
        
        with open(filepath, "w") as f:
            f.write(content)

print("More compacting applied.")
