package com.kaagazvault

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Modifier.vaultAnimateContentSize(): Modifier = animateContentSize(
    animationSpec = tween(vaultMotionDuration(VaultMotionToken.Standard))
)

@Composable
fun vaultFadeIn(): EnterTransition = fadeIn(
    animationSpec = tween(vaultMotionDuration(VaultMotionToken.Standard))
)

@Composable
fun vaultFadeOut(): ExitTransition = fadeOut(
    animationSpec = tween(vaultMotionDuration(VaultMotionToken.Standard))
)
