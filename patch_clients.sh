#!/bin/bash
sed -i 's/fun ClientsScreen(/fun ClientsScreen(\n    onNavigateToAddClient: () -> Unit,/g' app/src/main/java/com/example/ui/screens/ClientsScreen.kt
sed -i 's/onClick = { showAddDialog = true }/onClick = onNavigateToAddClient/g' app/src/main/java/com/example/ui/screens/ClientsScreen.kt
