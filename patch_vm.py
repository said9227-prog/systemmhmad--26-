with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r", encoding="utf-8") as f:
    vm = f.read()

target = """    fun updateClient(client: Client) {
        viewModelScope.launch {
            repository.updateClient(client)
            triggerAutoDriveBackup()
        }
    }"""

replacement = """    fun updateClient(client: Client) {
        viewModelScope.launch {
            repository.updateClient(client)
            repository.recalculateClientBalance(client.id)
            triggerAutoDriveBackup()
        }
    }"""

vm = vm.replace(target, replacement)

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w", encoding="utf-8") as f:
    f.write(vm)

print("Patched updateClient")
