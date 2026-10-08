import sys

with open('app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if 'private val _itemSuggestions' in line:
        skip = True
    if skip and 'suspend fun checkItemExists' in line:
        pass
    if skip and 'return repository.getItemByName(name.trim())' in line:
        skip = False
        continue
    if skip and '    }' in line:
        skip = False
        continue
        
    if not skip:
        new_lines.append(line)

# Let's write back
with open('app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
