package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.FormatUtils

/**
 * High-clarity number display component.
 * - Formats numbers completely as plain numbers without commas (e.g. 1000000000, 50000000).
 * - Leaves room for numbers by using short currency symbols (ر.س, ر.ي, دولار).
 * - If space is constrained and the number has many trailing zeros (e.g. 1000000000),
 *   it seamlessly displays the clean mathematical exponential representation: 1×10⁹ or 5×10⁷.
 * - Tapping on the number toggles between full and exponential format for complete transparency.
 */
@Composable
fun ClearNumberText(
    amount: Double,
    currency: String? = null,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 1
) {
    val fullFormatted = remember(amount) { FormatUtils.formatAmount(amount) }
    val exponentFormatted = remember(amount) { FormatUtils.toExponentFormat(amount) }
    val compactCurrency = remember(currency) { FormatUtils.formatCurrencyCompact(currency) }

    // Toggle between full and exponent on click if exponent is available
    var isToggledToExponent by remember { mutableStateOf<Boolean?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val availableWidth = maxWidth

        // Decide if space is constrained:
        // A plain number like 1000000000 has 10 chars. With currency symbol it's ~14 chars.
        // If available width is tight and the number has many trailing zeros, display 1×10⁹.
        val needsExponent = exponentFormatted != null && (
            isToggledToExponent == true || 
            (isToggledToExponent == null && availableWidth < 85.dp && fullFormatted.length > 8)
        )

        val displayText = if (needsExponent && exponentFormatted != null) {
            if (compactCurrency.isNotBlank()) "$exponentFormatted $compactCurrency" else exponentFormatted
        } else {
            if (compactCurrency.isNotBlank()) "$fullFormatted $compactCurrency" else fullFormatted
        }

        // Auto-scale font size if full text is long and space is tight
        val effectiveFontSize = when {
            displayText.length > 15 && availableWidth < 120.dp -> (fontSize.value * 0.75f).sp
            displayText.length > 11 && availableWidth < 100.dp -> (fontSize.value * 0.82f).sp
            displayText.length > 8 && availableWidth < 85.dp -> (fontSize.value * 0.88f).sp
            else -> fontSize
        }

        Text(
            text = displayText,
            color = color,
            fontSize = effectiveFontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = if (exponentFormatted != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isToggledToExponent = if (isToggledToExponent == true) false else true
                }
            } else Modifier
        )
    }
}

/**
 * Standard safe button text component.
 * Prevents button labels from wrapping into vertical letters or getting clipped vertically.
 */
@Composable
fun ButtonLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 12.5.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign = TextAlign.Center
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 2.dp),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}
