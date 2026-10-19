package com.kaagazvault

import android.media.AudioManager
import android.view.SoundEffectConstants
import android.view.View

/** Uses the platform click note, which respects the user's system sound settings. */
fun playVaultClick(view: View) {
    if (view.context.getSystemService(AudioManager::class.java)?.ringerMode != AudioManager.RINGER_MODE_SILENT) {
        view.playSoundEffect(SoundEffectConstants.CLICK)
    }
}
