import sys

with open('app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

nav_item = """        NavigationItem(
            route = "items",
            title = "الأصناف",
            selectedIcon = Icons.Default.Inventory2,
            unselectedIcon = Icons.Outlined.Inventory2,
            testTag = "nav_items"
        ),
"""

# Insert after clients
old_str = """        NavigationItem(
            route = "clients",
            title = "العملاء",
            selectedIcon = Icons.Default.People,
            unselectedIcon = Icons.Outlined.People,
            testTag = "nav_clients"
        ),"""

new_str = old_str + "\n" + nav_item

if old_str in content:
    content = content.replace(old_str, new_str)
    with open('app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
        f.write(content)
    print("Patched successfully")
else:
    print("Could not find the target string")
