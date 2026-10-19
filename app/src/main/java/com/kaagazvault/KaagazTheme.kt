package com.kaagazvault

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightVaultColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF2F6F64),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFB9E9DC),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF00201A),
    secondary = androidx.compose.ui.graphics.Color(0xFF50645F),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFD3E8E1),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF0C1F1B),
    tertiary = androidx.compose.ui.graphics.Color(0xFF6B5C8D),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFE9DDFF),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF25153F),
    background = androidx.compose.ui.graphics.Color(0xFFF8FAF8),
    surface = androidx.compose.ui.graphics.Color(0xFFF8FAF8),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFDCE5E1),
    onSurface = androidx.compose.ui.graphics.Color(0xFF171D1B),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF3F4945)
)

private val DarkVaultColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF9DD7CA),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00382F),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF155047),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFB9E9DC),
    secondary = androidx.compose.ui.graphics.Color(0xFFB7CCC5),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF384B46),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFD3E8E1),
    tertiary = androidx.compose.ui.graphics.Color(0xFFD2BFFF),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF51436D),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFE9DDFF),
    background = androidx.compose.ui.graphics.Color(0xFF101513),
    surface = androidx.compose.ui.graphics.Color(0xFF101513),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF3F4945),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE0E3E0),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFBEC9C4)
)

@Composable
fun KaagazTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        dynamic && darkTheme -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkVaultColors
        else -> LightVaultColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
