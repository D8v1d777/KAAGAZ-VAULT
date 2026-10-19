package com.kaagazvault

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SimplifiedModePreferences {
    private const val FILE_NAME = "ui_preferences"
    private const val KEY_SIMPLIFIED_MODE = "simplified_mode"

    fun read(context: Context): Boolean = runCatching {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SIMPLIFIED_MODE, false)
    }.getOrDefault(false)

    fun write(context: Context, enabled: Boolean) {
        runCatching {
            context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SIMPLIFIED_MODE, enabled)
                .apply()
        }
    }
}

@Composable
fun rememberSimplifiedMode(context: Context): MutableState<Boolean> = remember {
    mutableStateOf(SimplifiedModePreferences.read(context))
}

object SimplifiedLayout {
    val screenHorizontalPadding = 24.dp
    val sectionSpacing = 24.dp
    val cardPadding = 24.dp
    val actionGap = 16.dp
    val controlMinHeight = 56.dp
    val primaryControlMinHeight = 64.dp
    val iconTouchTarget = 56.dp
    val cornerRadius = 20.dp
}

private fun TextStyle.simplified(size: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit) =
    copy(fontSize = size, lineHeight = lineHeight)

fun Typography.simplified(): Typography = copy(
    displaySmall = displaySmall.simplified(48.sp, 56.sp),
    headlineLarge = headlineLarge.simplified(32.sp, 40.sp),
    titleLarge = titleLarge.simplified(24.sp, 32.sp),
    titleMedium = titleMedium.simplified(20.sp, 28.sp),
    bodyLarge = bodyLarge.simplified(20.sp, 30.sp),
    bodyMedium = bodyMedium.simplified(18.sp, 27.sp),
    bodySmall = bodySmall.simplified(18.sp, 27.sp),
    labelLarge = labelLarge.simplified(18.sp, 24.sp),
    labelMedium = labelMedium.simplified(18.sp, 24.sp),
    labelSmall = labelSmall.simplified(16.sp, 22.sp)
)
