sed -i '/val viewModel: AppViewModel = viewModel(/i \            val reportViewModel: com.example.ui.viewmodel.ReportViewModel = viewModel(\n                factory = com.example.ui.viewmodel.ReportViewModelFactory(application, repository)\n            )' app/src/main/java/com/example/MainActivity.kt

sed -i 's/ReportsScreen(viewModel = viewModel)/ReportsScreen(reportViewModel = reportViewModel)/' app/src/main/java/com/example/MainActivity.kt
