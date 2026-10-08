import sys
import re

with open('app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the messy block starting at `val companies = ` and ending before `val backupHistory =`
match = re.search(r'(val companies = repository\.getAllCompaniesFlow\(\)\.stateIn\([\s\S]*?)(val backupHistory = repository\.getAllBackupHistoryFlow)', content)
if match:
    backup_history_block = match.group(2)
    
    new_insertion = """val companies = repository.getAllCompaniesFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    
    private val _itemSuggestions = MutableStateFlow<List<Item>>(emptyList())
    val itemSuggestions = _itemSuggestions.asStateFlow()

    private var searchJob: Job? = null

    fun searchItemAutocomplete(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _itemSuggestions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            val results = repository.autocompleteItems(query)
            _itemSuggestions.value = results.sortedWith { a, b ->
                val aExact = a.name.equals(query, ignoreCase = true)
                val bExact = b.name.equals(query, ignoreCase = true)
                if (aExact && !bExact) return@sortedWith -1
                if (!aExact && bExact) return@sortedWith 1
                val aStarts = a.name.startsWith(query, ignoreCase = true)
                val bStarts = b.name.startsWith(query, ignoreCase = true)
                if (aStarts && !bStarts) return@sortedWith -1
                if (!aStarts && bStarts) return@sortedWith 1
                a.name.compareTo(b.name)
            }.take(10)
        }
    }

    fun clearItemSuggestions() {
        searchJob?.cancel()
        _itemSuggestions.value = emptyList()
    }

    suspend fun checkItemExists(name: String): Item? {
        return repository.getItemByName(name.trim())
    }

    """
    new_content = content[:match.start()] + new_insertion + backup_history_block + content[match.end(2):]
    with open('app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt', 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("Fixed via regex")
else:
    print("Could not find the block to replace")
