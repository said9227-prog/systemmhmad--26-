sed -i 's/fun MainAppContent(viewModel: AppViewModel)/fun MainAppContent(viewModel: AppViewModel, reportViewModel: com.example.ui.viewmodel.ReportViewModel)/' app/src/main/java/com/example/MainActivity.kt
sed -i 's/MainAppContent(viewModel)/MainAppContent(viewModel, reportViewModel)/' app/src/main/java/com/example/MainActivity.kt
