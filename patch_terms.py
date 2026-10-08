with open("app/src/main/java/com/example/ui/screens/AddClientScreen.kt", "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace('var balanceType by remember { mutableStateOf("عليه لنا") } // عليه لنا, له عندنا', 'var balanceType by remember { mutableStateOf("مدين") } // مدين, دائن')
content = content.replace('selected = balanceType == "عليه لنا",\n                            text = "عليه لنا",\n                            onClick = { balanceType = "عليه لنا" },', 'selected = balanceType == "مدين",\n                            text = "مدين",\n                            onClick = { balanceType = "مدين" },')
content = content.replace('selected = balanceType == "له عندنا",\n                            text = "له عندنا",\n                            onClick = { balanceType = "له عندنا" },', 'selected = balanceType == "دائن",\n                            text = "دائن",\n                            onClick = { balanceType = "دائن" },')

with open("app/src/main/java/com/example/ui/screens/AddClientScreen.kt", "w", encoding="utf-8") as f:
    f.write(content)

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r", encoding="utf-8") as f:
    vm = f.read()

vm = vm.replace('balanceType: String = "عليه لنا"', 'balanceType: String = "مدين"')
vm = vm.replace('val calculatedInitialBalance = if (balanceType == "عليه لنا") initialBalance else -initialBalance', 'val calculatedInitialBalance = if (balanceType == "مدين" || balanceType == "عليه لنا") initialBalance else -initialBalance')

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w", encoding="utf-8") as f:
    f.write(vm)

with open("app/src/main/java/com/example/data/repository/AppRepository.kt", "r", encoding="utf-8") as f:
    repo = f.read()

repo = repo.replace('val calculatedBalance = (if (client.balanceType == "عليه لنا") client.initialBalance else -client.initialBalance) + totalInvoiced - totalPaid', 'val isDebit = client.balanceType == "مدين" || client.balanceType == "عليه لنا"\n        val calculatedBalance = (if (isDebit) client.initialBalance else -client.initialBalance) + totalInvoiced - totalPaid')

with open("app/src/main/java/com/example/data/repository/AppRepository.kt", "w", encoding="utf-8") as f:
    f.write(repo)

print("Updated terminology successfully")
