package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = WalletPrimaryContainerDark,
    onPrimaryContainer = WalletOnPrimaryContainerDark,
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    background = WalletBackgroundDark,
    surface = WalletSurfaceDark,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    outline = WalletCardBorderDark,
    outlineVariant = Color(0xFF1E293B)
)

private val LightColorScheme = lightColorScheme(
    primary = WalletPrimaryLight,
    onPrimary = WalletOnPrimaryLight,
    primaryContainer = WalletPrimaryContainerLight,
    onPrimaryContainer = WalletOnPrimaryContainerLight,
    secondary = WalletSecondaryLight,
    onSecondary = Color.White,
    background = WalletBackgroundLight,
    surface = WalletSurfaceLight,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    outline = WalletCardBorderLight,
    outlineVariant = Color(0xFFE2E8F0)
)

@Composable
fun CouponWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = CouponWalletTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
