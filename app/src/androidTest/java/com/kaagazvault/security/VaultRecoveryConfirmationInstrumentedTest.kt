package com.kaagazvault.security

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaagazvault.MainActivity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for the recovery path's byte-array ownership transfer.
 *
 * The recovery re-wrap path passes pendingVek back into prepareRecoveryConfirmation.
 * The method must not clear the exact same array before the user confirms it.
 */
@RunWith(AndroidJUnit4::class)
class VaultRecoveryConfirmationInstrumentedTest {
    @Test
    fun reusingPendingVekForConfirmationDoesNotWipeRecoveredKey() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = VaultAccessController(activity, VaultSession())
                val vek = ByteArray(32) { (it + 17).toByte() }
                val expected = vek.copyOf()
                try {
                    val pending = VaultAccessController::class.java.getDeclaredField("pendingVek")
                        .apply { isAccessible = true }
                    pending.set(controller, vek)

                    val prepare = VaultAccessController::class.java.getDeclaredMethod(
                        "prepareRecoveryConfirmation",
                        ByteArray::class.java,
                        String::class.java
                    ).apply { isAccessible = true }
                    prepare.invoke(controller, vek, "recovery-key-confirmation-fixture")

                    assertArrayEquals(
                        "Confirmation must preserve the exact recovered VEK bytes",
                        expected,
                        vek
                    )
                    val state = controller.uiState.value
                    assertTrue(state is VaultAccessUiState.ConfirmRecoveryKey)
                    assertEquals(
                        "recovery-key-confirmation-fixture",
                        (state as VaultAccessUiState.ConfirmRecoveryKey).key
                    )
                } finally {
                    expected.fill(0)
                    controller.dispose()
                    vek.fill(0)
                }
            }
        }
    }
}
