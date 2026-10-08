import sys

with open('app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    new_lines.append(line)
    if 'val companies = repository.getAllCompaniesFlow().stateIn(' in line:
        pass
    if 'viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()' in line and 'companies' in new_lines[-2]:
        new_lines.append("""
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
            delay(300)
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
""")

with open('app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
