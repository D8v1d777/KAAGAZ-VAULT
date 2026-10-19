package com.kaagazvault

import android.view.View

/** @deprecated Use the Phase 14 semantic feedback helpers in [VaultFeedback]. */
@Deprecated("Use playVaultClick from VaultFeedback")
fun legacyPlayVaultClick(view: View) = playVaultClick(view)
