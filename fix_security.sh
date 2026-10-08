#!/bin/bash
sed -i 's/val securityPin by viewModel.securityPin.collectAsState()/val securityPin by viewModel.securityPin.collectAsState()\n    val settings by viewModel.storeSettings.collectAsState()\n    val storeName = settings.storeName/g' app/src/main/java/com/example/ui/screens/SecurityScreen.kt
