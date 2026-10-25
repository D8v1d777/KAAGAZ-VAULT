package com.kaagazvault

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightVaultColors = lightColorScheme(
    primary = Color(0xFF2F6F64),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9E9DC),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF50645F),
    secondaryContainer = Color(0xFFD3E8E1),
    onSecondaryContainer = Color(0xFF0C1F1B),
    tertiary = Color(0xFF6B5C8D),
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF25153F),
    background = Color(0xFFF8FAF8),
    surface = Color(0xFFF8FAF8),
    surfaceVariant = Color(0xFFDCE5E1),
    onSurface = Color(0xFF171D1B),
    onSurfaceVariant = Color(0xFF3F4945)
)

private val DarkVaultColors = darkColorScheme(
    primary = Color(0xFF9DD7CA),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF155047),
    onPrimaryContainer = Color(0xFFB9E9DC),
    secondary = Color(0xFFB7CCC5),
    secondaryContainer = Color(0xFF384B46),
    onSecondaryContainer = Color(0xFFD3E8E1),
    tertiary = Color(0xFFD2BFFF),
    tertiaryContainer = Color(0xFF51436D),
    onTertiaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF101513),
    surface = Color(0xFF101513),
    surfaceVariant = Color(0xFF3F4945),
    onSurface = Color(0xFFE0E3E0),
    onSurfaceVariant = Color(0xFFBEC9C4)
)

/**
 * Applies the calm vault theme. On Android 12+, dynamicColor extracts Material You roles from
 * the system wallpaper palette. The generated role pairs are kept intact so Material 3 can
 * preserve its accessible on-* / container relationships. Older devices and extraction failures
 * use the reviewed calm fallback palettes.
 */
@Composable
fun KaagazTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val colors = materialYouColorScheme(LocalContext.current, darkTheme, dynamicColor)
    MaterialTheme(colorScheme = colors, content = content)
}

private fun materialYouColorScheme(
    context: Context,
    darkTheme: Boolean,
    dynamicColor: Boolean
): ColorScheme {
    val fallback = if (darkTheme) DarkVaultColors else LightVaultColors
    if (!dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback

    return runCatching {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }.getOrElse { fallback }
}
