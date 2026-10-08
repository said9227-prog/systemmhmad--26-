package com.example.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthHelper {

    enum class BiometricAvailability {
        AVAILABLE,
        NONE_ENROLLED,
        NO_HARDWARE,
        HW_UNAVAILABLE,
        UNKNOWN
    }

    /**
     * Checks if biometric authentication is available on this device.
     */
    fun checkBiometricStatus(context: Context): BiometricAvailability {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HW_UNAVAILABLE
            else -> BiometricAvailability.UNKNOWN
        }
    }

    /**
     * Shows standard Android BiometricPrompt attached to the FragmentActivity.
     */
    fun promptBiometric(
        activity: FragmentActivity,
        userName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        try {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("المصادقة بالبصمة 🔒")
                .setSubtitle(if (userName.isNotBlank()) "المستخدم: $userName" else "تأكيد هوية المستخدم")
                .setDescription("يرجى وضع إصبعك على مستشعر البصمة للمتابعة وفتح التطبيق")
                .setNegativeButtonText("إلغاء / استخدام PIN")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
                .build()

            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            // In case device or system does not support negative button with device credential
            try {
                val promptInfoFallback = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("المصادقة بالبصمة 🔒")
                    .setSubtitle(if (userName.isNotBlank()) "المستخدم: $userName" else "تأكيد هوية المستخدم")
                    .setDescription("يرجى وضع إصبعك على مستشعر البصمة للمتابعة وفتح التطبيق")
                    .setNegativeButtonText("إلغاء")
                    .build()

                val biometricPrompt = BiometricPrompt(activity, executor, callback)
                biometricPrompt.authenticate(promptInfoFallback)
            } catch (ex: Exception) {
                onError("تعذر تشغيل مستشعر البصمة: ${ex.localizedMessage}")
            }
        }
    }
}
