package com.example.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shared Form Navigation Controller.
 * Provides consistent, seamless, and automatic field advancement across all data-entry forms.
 * Manages FocusRequester, BringIntoViewRequester, and smart ImeActions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class FormNavigator(
    val focusManager: FocusManager,
    private val coroutineScope: CoroutineScope
) {
    private val requesters = mutableStateMapOf<Any, FocusRequester>()
    private val bringIntoViewRequesters = mutableStateMapOf<Any, BringIntoViewRequester>()
    
    // Explicit ordered sequence of keys
    val orderedKeys = mutableStateListOf<Any>()

    fun registerKey(key: Any) {
        if (!orderedKeys.contains(key)) {
            orderedKeys.add(key)
        }
    }

    fun unregisterKey(key: Any) {
        orderedKeys.remove(key)
    }

    fun getFocusRequester(key: Any): FocusRequester {
        return requesters.getOrPut(key) { FocusRequester() }
    }

    fun getBringIntoViewRequester(key: Any): BringIntoViewRequester {
        return bringIntoViewRequesters.getOrPut(key) { BringIntoViewRequester() }
    }

    fun isLast(key: Any): Boolean {
        if (orderedKeys.isEmpty()) return true
        val idx = orderedKeys.indexOf(key)
        return idx == orderedKeys.size - 1 || idx == -1
    }

    fun next(currentKey: Any) {
        val currentIndex = orderedKeys.indexOf(currentKey)
        if (currentIndex != -1 && currentIndex < orderedKeys.size - 1) {
            val nextKey = orderedKeys[currentIndex + 1]
            focusOn(nextKey)
        } else {
            // Reached the end of form or key not found
            focusManager.clearFocus()
        }
    }

    fun focusOn(key: Any) {
        val requester = getFocusRequester(key)
        val bringIntoViewRequester = getBringIntoViewRequester(key)
        coroutineScope.launch {
            try {
                requester.requestFocus()
                // Small delay ensures layout calculation completes after focus transition
                delay(30)
                bringIntoViewRequester.bringIntoView()
            } catch (e: Exception) {
                try {
                    focusManager.moveFocus(FocusDirection.Down)
                } catch (_: Exception) {}
            }
        }
    }

    fun keyboardOptions(
        key: Any,
        keyboardType: KeyboardType = KeyboardType.Text,
        isLastOverride: Boolean? = null
    ): KeyboardOptions {
        val isLastField = isLastOverride ?: isLast(key)
        return KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (isLastField) ImeAction.Done else ImeAction.Next
        )
    }

    fun keyboardActions(
        key: Any,
        onDone: (() -> Unit)? = null
    ): KeyboardActions {
        return KeyboardActions(
            onNext = { next(key) },
            onDone = {
                if (onDone != null) {
                    onDone()
                } else {
                    focusManager.clearFocus()
                }
            }
        )
    }

    /**
     * Auto-advance when input completes a known expected length (e.g. phone numbers, OTP/PIN).
     * Prevents accidental jumps during backspaces or when user is navigating back.
     */
    fun checkAutoAdvance(
        key: Any,
        oldValue: String,
        newValue: String,
        targetLength: Int
    ) {
        if (newValue.length == targetLength && oldValue.length < newValue.length) {
            next(key)
        }
    }

    /**
     * Auto-advance for phone numbers:
     * Yemeni phone numbers are 9 digits (e.g. 77xxxxxxx, 73xxxxxxx, 71xxxxxxx, 70xxxxxxx)
     * Or 10 digits for local numbers with leading 0 (e.g. 05xxxxxxxx, 01xxxxxx)
     */
    fun checkPhoneAutoAdvance(
        key: Any,
        oldValue: String,
        newValue: String
    ) {
        val clean = newValue.filter { it.isDigit() }
        val oldClean = oldValue.filter { it.isDigit() }
        if (clean.length > oldClean.length) {
            if ((clean.startsWith("0") && clean.length == 10) || (!clean.startsWith("0") && clean.length == 9)) {
                next(key)
            }
        }
    }

    /**
     * Convenient builder for attaching form navigation to any Composable modifier.
     */
    fun fieldModifier(
        key: Any,
        baseModifier: Modifier = Modifier
    ): Modifier {
        return baseModifier.formNavigationField(this, key)
    }
}

/**
 * Creates and remembers a FormNavigator instance.
 */
@Composable
fun rememberFormNavigator(keys: List<Any>? = null): FormNavigator {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val navigator = remember { FormNavigator(focusManager, coroutineScope) }
    
    if (keys != null) {
        remember(keys) {
            navigator.orderedKeys.clear()
            navigator.orderedKeys.addAll(keys)
        }
    }
    
    return navigator
}

/**
 * Modifier to attach a field to the FormNavigator.
 * Automatically handles registration, FocusRequester, and smooth BringIntoView on focus.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.formNavigationField(
    navigator: FormNavigator,
    key: Any
): Modifier = composed {
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = navigator.getFocusRequester(key)
    val bringIntoViewRequester = navigator.getBringIntoViewRequester(key)

    DisposableEffect(key) {
        navigator.registerKey(key)
        onDispose {
            navigator.unregisterKey(key)
        }
    }

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .focusRequester(focusRequester)
        .onFocusEvent { focusState ->
            if (focusState.isFocused) {
                coroutineScope.launch {
                    delay(30)
                    try {
                        bringIntoViewRequester.bringIntoView()
                    } catch (_: Exception) {}
                }
            }
        }
}
