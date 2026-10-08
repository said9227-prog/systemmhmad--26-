import os
import re

directory = "app/src/main/java/com/example/ui/screens/"

# 1. Replace MediumTopAppBar and LargeTopAppBar with TopAppBar
# 2. Reduce the size of TopAppBar by stripping multiline sub-titles if it's too much, but for now we'll just rename the AppBar component and its defaults.
for filename in os.listdir(directory):
    if filename.endswith(".kt"):
        filepath = os.path.join(directory, filename)
        with open(filepath, "r") as f:
            content = f.read()

        new_content = content.replace("MediumTopAppBar(", "TopAppBar(")
        new_content = new_content.replace("MediumTopAppBar", "TopAppBar")
        new_content = new_content.replace("mediumTopAppBarColors", "topAppBarColors")
        
        new_content = new_content.replace("LargeTopAppBar(", "TopAppBar(")
        new_content = new_content.replace("LargeTopAppBar", "TopAppBar")
        new_content = new_content.replace("largeTopAppBarColors", "topAppBarColors")

        if new_content != content:
            with open(filepath, "w") as f:
                f.write(new_content)
                print(f"Updated {filename}")
