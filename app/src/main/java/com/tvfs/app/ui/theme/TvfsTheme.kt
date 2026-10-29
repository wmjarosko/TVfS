package com.tvfs.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/**
 * SDS Section 5.1 Ergonomic Standards & Visual Tokens (calibrated for 7 to 10 feet viewing).
 */
object TvfsTokens {
    // Typography (SDS 5.1)
    val PrimaryHeadingSize = 38.sp      // 38sp, Bold
    val ProgramTitleSize = 30.sp        // 30sp, Semi-Bold
    val SecondaryMetadataSize = 24.sp   // 24sp, Regular
    val MinimumTextSize = 22.sp         // Minimum Text Size across entire app: 22sp

    // Contrast & Color Tokens (SDS 5.1)
    // Background Overlay: ARGB(210, 16, 20, 24) (82% opacity dark slate blue/charcoal)
    val OverlayBackground = Color(red = 16, green = 20, blue = 24, alpha = 210)
    val ScreenBackground = Color(0xFF0B0E12)
    val CardBackground = Color(0xFF161D26)
    val CardFocusedBackground = Color(0xFF222D3B)

    // Active Focused Border: 4dp solid outline in high-visibility Solar Yellow (#FFD54F) with 1.05x scale factor
    val SolarYellow = Color(0xFFFFD54F)
    val ActiveFocusBorderWidth: Dp = 4.dp
    const val ActiveFocusScale: Float = 1.05f

    // Text Colors
    val PureWhite = Color(0xFFFFFFFF)
    val SecondaryGray = Color(0xFFCCCCCC)
    val AlertRed = Color(0xFFFF6B6B)
    val LiveBadgeGreen = Color(0xFF4CAF50)
}

/**
 * Reusable high-contrast focusable card modifier implementing SDS 5.1:
 * - 4dp solid outline in Solar Yellow (#FFD54F) when focused
 * - 1.05x scale factor when focused
 * - Responds to D-Pad Center / Enter / Click
 */
@Composable
fun TvfsFocusableSurface(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    val baseModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }

    Box(
        modifier = baseModifier
            .scale(if (isFocused) TvfsTokens.ActiveFocusScale else 1.0f)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    onFocused?.invoke()
                }
            }
            .background(
                color = if (isFocused) TvfsTokens.CardFocusedBackground else TvfsTokens.CardBackground,
                shape = shape
            )
            .border(
                border = if (isFocused) {
                    BorderStroke(TvfsTokens.ActiveFocusBorderWidth, TvfsTokens.SolarYellow)
                } else {
                    BorderStroke(2.dp, Color(0xFF2C3847))
                },
                shape = shape
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter ||
                        keyEvent.key == Key.Enter ||
                        keyEvent.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable { onClick() },
        content = { content(isFocused) }
    )
}

/**
 * High-contrast action button with 4dp Solar Yellow focus outline and minimum 24sp/30sp text.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvfsActionButton(
    label: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    TvfsFocusableSurface(
        modifier = modifier,
        focusRequester = focusRequester,
        onClick = onClick
    ) { isFocused ->
        Box(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isFocused) TvfsTokens.SolarYellow else TvfsTokens.PureWhite,
                fontSize = TvfsTokens.ProgramTitleSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
