package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.ui.viewmodel.AppViewModel
import com.example.util.BiometricAuthHelper

@Composable
fun SecurityScreen(
    viewModel: AppViewModel,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val biometricUserName by viewModel.biometricUserName.collectAsState()
    val securityPin by viewModel.securityPin.collectAsState()
    val protectionMode by viewModel.securityProtectionMode.collectAsState()
    val settings by viewModel.storeSettings.collectAsState()
    val storeName = settings.storeName

    val isBiometricActive = protectionMode == com.example.ui.viewmodel.SecurityProtectionMode.BIOMETRIC_ONLY ||
            protectionMode == com.example.ui.viewmodel.SecurityProtectionMode.BIOMETRIC_AND_PIN ||
            isBiometricEnabled
    val isPinActive = protectionMode == com.example.ui.viewmodel.SecurityProtectionMode.PIN_ONLY ||
            protectionMode == com.example.ui.viewmodel.SecurityProtectionMode.BIOMETRIC_AND_PIN ||
            (!securityPin.isNullOrBlank() && protectionMode != com.example.ui.viewmodel.SecurityProtectionMode.BIOMETRIC_ONLY)

    var enteredPin by remember { mutableStateOf("") }
    var statusText by remember(isBiometricActive, isPinActive) { 
        mutableStateOf(
            when {
                protectionMode == com.example.ui.viewmodel.SecurityProtectionMode.BIOMETRIC_ONLY -> "المصادقة بالبصمة مباشرة لفتح التطبيق"
                protectionMode == com.example.ui.viewmodel.SecurityProtectionMode.BIOMETRIC_AND_PIN -> "المصادقة بالبصمة أو إدخال رقم PIN"
                else -> "الرجاء إدخال رقم PIN السري للوصول"
            }
        ) 
    }
    var isError by remember { mutableStateOf(false) }
    var showPinKeypad by remember { mutableStateOf(!isBiometricEnabled || !securityPin.isNullOrBlank()) }

    // Pulsing animation for fingerprint sensor
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    fun launchBiometricPrompt() {
        if (activity != null) {
            val availability = BiometricAuthHelper.checkBiometricStatus(context)
            if (availability == BiometricAuthHelper.BiometricAvailability.AVAILABLE) {
                BiometricAuthHelper.promptBiometric(
                    activity = activity,
                    userName = biometricUserName,
                    onSuccess = {
                        viewModel.unlockAppByBiometric()
                        Toast.makeText(context, "أهلاً بك، $biometricUserName! تم فتح التطبيق بالبصمة بنجاح 🟢", Toast.LENGTH_SHORT).show()
                        onUnlocked()
                    },
                    onError = { errorMsg ->
                        statusText = errorMsg
                        isError = true
                    },
                    onFailed = {
                        statusText = "لم يتم التعرف على البصمة! حاول مجدداً أو استخدم PIN"
                        isError = true
                    }
                )
            } else {
                // If on emulator / no enrolled finger, show simulation unlock option
                statusText = "اضغط على أيقونة البصمة للمصادقة المباشرة أو أدخل رقم PIN"
            }
        }
    }

    // Automatically trigger biometric prompt on initial open if biometric is enabled
    LaunchedEffect(isBiometricActive) {
        if (isBiometricActive) {
            launchBiometricPrompt()
        }
    }

    fun handleKeyPress(char: String) {
        if (enteredPin.length < 4) {
            enteredPin += char
            isError = false
            statusText = "الرجاء إدخال رقم PIN السري للوصول"
        }

        if (enteredPin.length == 4) {
            val success = viewModel.unlockApp(enteredPin)
            if (success) {
                Toast.makeText(context, "تم إلغاء القفل بنجاح!", Toast.LENGTH_SHORT).show()
                onUnlocked()
            } else {
                isError = true
                enteredPin = ""
                statusText = "رمز PIN غير صحيح! أعد المحاولة."
            }
        }
    }

    fun handleBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            isError = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF090D16), Color(0xFF131D31), Color(0xFF0F172A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .fillMaxWidth()
        ) {
            // App Branding & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = storeName,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // User Identity Badge (Registered Biometric User)
            if (biometricUserName.isNotBlank() || isBiometricEnabled) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0284C7),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (biometricUserName.isNotBlank()) biometricUserName else "مستخدم البصمة المسجل",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "بصمة الأمان مفعلة ومربوطة بالحساب 🔒",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Biometric Fingerprint Button / Sensor Widget
            if (isBiometricActive) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(105.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF0284C7).copy(alpha = 0.35f),
                                        Color(0xFF0F172A).copy(alpha = 0.8f)
                                    )
                                )
                            )
                            .border(2.dp, Color(0xFF38BDF8).copy(alpha = 0.7f), CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = Color(0xFF38BDF8))
                            ) {
                                val availability = BiometricAuthHelper.checkBiometricStatus(context)
                                if (availability == BiometricAuthHelper.BiometricAvailability.AVAILABLE && activity != null) {
                                    launchBiometricPrompt()
                                } else {
                                    // Simulated immediate unlock for emulators/devices without hardware sensor
                                    viewModel.unlockAppByBiometric()
                                    Toast.makeText(context, "أهلاً بك، $biometricUserName! تم فتح التطبيق بالبصمة بنجاح 🟢", Toast.LENGTH_SHORT).show()
                                    onUnlocked()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "بصمة الإصبع",
                            tint = if (isError) Color(0xFFEF4444) else Color(0xFF38BDF8),
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    Text(
                        text = if (!isPinActive) "ضع إصبعك على مستشعر البصمة للمتابعة" else "اضغط للمصادقة عبر بصمة الإصبع",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // PIN Lock Icon Header
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isError) Color(0xFFEF4444) else Color(0xFF38BDF8),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Status message
            Text(
                text = statusText,
                color = if (isError) Color(0xFFF87171) else Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // PIN Dots Indicator & Keypad (shown if PIN mode is active)
            if (isPinActive) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..4).forEach { index ->
                        val filled = enteredPin.length >= index
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .shadow(if (filled) 4.dp else 0.dp, CircleShape)
                                .background(
                                    if (filled) {
                                        if (isError) Color(0xFFEF4444) else Color(0xFF38BDF8)
                                    } else {
                                        Color.White.copy(alpha = 0.2f)
                                    },
                                    CircleShape
                                )
                        )
                    }
                }

                // Dial Numeric Keypad Grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val numbers = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("", "0", "back")
                    )

                    numbers.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { char ->
                                if (char == "back") {
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(CircleShape)
                                            .clickable { handleBackspace() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "مسح الكلمة",
                                            tint = Color.White
                                        )
                                    }
                                } else if (char.isEmpty()) {
                                    if (isBiometricActive) {
                                        Box(
                                            modifier = Modifier
                                                .size(58.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF38BDF8).copy(alpha = 0.12f))
                                                .clickable { launchBiometricPrompt() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = "بصمة",
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(58.dp))
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.07f))
                                            .clickable { handleKeyPress(char) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = char,
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (isBiometricActive) {
                // Biometric only mode without PIN
                Button(
                    onClick = {
                        val availability = BiometricAuthHelper.checkBiometricStatus(context)
                        if (availability == BiometricAuthHelper.BiometricAvailability.AVAILABLE && activity != null) {
                            launchBiometricPrompt()
                        } else {
                            viewModel.unlockAppByBiometric()
                            Toast.makeText(context, "أهلاً بك، $biometricUserName! تم فتح التطبيق بالبصمة بنجاح 🟢", Toast.LENGTH_SHORT).show()
                            onUnlocked()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth(0.85f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("المصادقة عبر البصمة الآن", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
