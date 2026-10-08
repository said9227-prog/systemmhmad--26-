#!/bin/bash
sed -i 's/val isDarkMode by viewModel.isDarkMode.collectAsState()/val isDarkMode by viewModel.isDarkMode.collectAsState()\n    val storeSettings by viewModel.storeSettings.collectAsState()/g' app/src/main/java/com/example/MainActivity.kt
