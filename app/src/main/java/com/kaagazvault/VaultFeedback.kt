package com.kaagazvault

import android.media.AudioManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/** Semantic events keep feedback independent from document content and repository state. */
enum class VaultFeedbackEvent {
    ButtonPressed,
    OperationSucceeded
}

/**
 * Uses the platform click note and a short predefined key haptic. Both are best-effort and
 * remain subject to the user's system settings. No audio asset, focus request, or vibration
 * permission is needed.
 */
fun emitVaultFeedback(view: View, event: VaultFeedbackEvent) {
    when (event) {
        VaultFeedbackEvent.ButtonPressed -> {
            if (view.context.getSystemService(AudioManager::class.java)?.ringerMode != AudioManager.RINGER_MODE_SILENT) {
                view.playSoundEffect(SoundEffectConstants.CLICK)
            }
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        VaultFeedbackEvent.OperationSucceeded -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}

/** Backward-compatible entry point for existing button call sites. */
fun playVaultClick(view: View) = emitVaultFeedback(view, VaultFeedbackEvent.ButtonPressed)

/** One short confirmation after a durable success; intentionally no extra sound. */
fun playVaultSuccess(view: View) = emitVaultFeedback(view, VaultFeedbackEvent.OperationSucceeded)

@Composable
fun VaultPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val view = LocalView.current
    Button(
        onClick = { playVaultClick(view); onClick() },
        modifier = modifier,
        enabled = enabled,
        content = content
    )
}

@Composable
fun VaultTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val view = LocalView.current
    FilledTonalButton(
        onClick = { playVaultClick(view); onClick() },
        modifier = modifier,
        enabled = enabled,
        content = content
    )
}

@Composable
fun VaultOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val view = LocalView.current
    OutlinedButton(
        onClick = { playVaultClick(view); onClick() },
        modifier = modifier,
        enabled = enabled,
        content = content
    )
}

@Composable
fun VaultTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val view = LocalView.current
    TextButton(
        onClick = { playVaultClick(view); onClick() },
        modifier = modifier,
        enabled = enabled,
        content = content
    )
}

/** Named motion tokens keep transitions calm and make reduced-motion behavior testable. */
enum class VaultMotionToken(val durationMillis: Int) {
    Quick(120),
    Standard(220),
    Settle(320)
}

@Composable
fun vaultMotionDuration(token: VaultMotionToken): Int {
    val context = LocalContext.current
    val reducedMotion = androidx.compose.runtime.remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
    return if (reducedMotion) 0 else token.durationMillis
}
