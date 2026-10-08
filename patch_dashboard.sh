#!/bin/bash
cat app/src/main/java/com/example/ui/screens/DashboardScreen.kt | sed -e 's/showOverdueDialog = false/showOverdueDialog = false\n                    viewModel.dismissOverdueInstallments(overdueList.map { it.id })/g' > DashboardScreen_patched.kt
mv DashboardScreen_patched.kt app/src/main/java/com/example/ui/screens/DashboardScreen.kt
