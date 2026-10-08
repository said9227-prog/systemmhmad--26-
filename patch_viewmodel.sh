#!/bin/bash
cat app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt | sed -e '/val overdueInstallments = installments.map { list ->/,+5c\
    private val prefs = app.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)\
    \
    private val _dismissedInstallments = kotlinx.coroutines.flow.MutableStateFlow(\
        prefs.getStringSet("dismissed_installments", emptySet()) ?: emptySet()\
    )\
    \
    val overdueInstallments = kotlinx.coroutines.flow.combine(installments, _dismissedInstallments) { list, dismissed ->\
        val now = System.currentTimeMillis()\
        list.filter { !it.isPaid && it.dueDate < now && !dismissed.contains(it.id.toString()) }\
    }.stateIn(\
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()\
    )\
\
    fun dismissOverdueInstallments(ids: List<Int>) {\
        val newSet = _dismissedInstallments.value.toMutableSet().apply {\
            addAll(ids.map { it.toString() })\
        }\
        prefs.edit().putStringSet("dismissed_installments", newSet).apply()\
        _dismissedInstallments.value = newSet\
    }' > AppViewModel_patched.kt
mv AppViewModel_patched.kt app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt
