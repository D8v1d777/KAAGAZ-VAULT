package com.kaagazvault

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaagazvault.security.VaultAccessController
import com.kaagazvault.security.VaultAccessUiState
import com.kaagazvault.security.VaultSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Invokes MainActivity.onStop itself using synthetic controller/session state in the
 * disposable instrumentation app process. This verifies callback wiring, not OS-driven lifecycle delivery or
 * process death. It never installs a real Keystore key or opens a vault.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleInstrumentedTest {
    @Test
    fun foregroundLossCancelsPendingSetupPromptAndRemovesOnlyItsUncommittedAlias() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitControllerInitialization(scenario)
        var testKey: com.kaagazvault.security.AuthBoundVaultKey? = null
        try {
            scenario.onActivity { activity ->
                val controller = controllerOf(activity)
                val alias = com.kaagazvault.security.AuthBoundVaultKey().newAliasForRecoveryOrSetup()
                val key = com.kaagazvault.security.AuthBoundVaultKey(alias = alias)
                val cipher = key.prepareSetupCipher()
                testKey = key
                setPrivateField(controller, "purpose", enumPurpose(controller, "SETUP"))
                setPrivateField(controller, "authKey", key)
                setPrivateField(controller, "authAlias", alias)
                setPrivateField(controller, "activeCipher", cipher)
                controller.uiState.value = VaultAccessUiState.SetupRequired()
                invokeMainActivityOnStop(activity)

                assertTrue("foreground loss must detach the pending prompt purpose", privateField(controller, "purpose") == null)
                assertNull("foreground loss must detach its CryptoObject cipher", privateField(controller, "activeCipher"))
                assertFalse("cancelled setup must not leave an orphan test alias", key.hasWrappingKeyAlias())
                assertTrue("setup remains retryable after prompt cancellation", controller.uiState.value is VaultAccessUiState.SetupRequired)
            }
        } finally {
            scenario.close()
            try { testKey?.discardAbandonedSetup() } catch (_: Exception) { }
        }
    }

    @Test
    fun foregroundLossCancelsRecoveryPromptAndClearsPendingRecoveryBytes() {
        val syntheticPendingVek = ByteArray(32) { 0x43 }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitControllerInitialization(scenario)
        var testKey: com.kaagazvault.security.AuthBoundVaultKey? = null
        try {
            scenario.onActivity { activity ->
                val controller = controllerOf(activity)
                val alias = com.kaagazvault.security.AuthBoundVaultKey().newAliasForRecoveryOrSetup()
                val key = com.kaagazvault.security.AuthBoundVaultKey(alias = alias)
                val cipher = key.prepareSetupCipher()
                testKey = key
                setPrivateField(controller, "purpose", enumPurpose(controller, "RECOVERY_REWRAP"))
                setPrivateField(controller, "authKey", key)
                setPrivateField(controller, "authAlias", alias)
                setPrivateField(controller, "activeCipher", cipher)
                setPrivateField(controller, "pendingVek", syntheticPendingVek)
                setPrivateField(controller, "pendingRecoveryText", "SYNTHETIC-RECOVERY-KEY")
                controller.uiState.value = VaultAccessUiState.RecoveryRequired("Synthetic pending recovery prompt")
                invokeMainActivityOnStop(activity)

                assertTrue("foreground loss must invalidate the recovery prompt", privateField(controller, "purpose") == null)
                assertTrue("pending recovery VEK bytes must be zeroed", syntheticPendingVek.all { it == 0.toByte() })
                assertNull("controller must release pending recovery VEK", privateField(controller, "pendingVek"))
                assertNull("controller must release pending recovery text", privateField(controller, "pendingRecoveryText"))
                assertFalse("cancelled recovery must remove only its uncommitted alias", key.hasWrappingKeyAlias())
                assertTrue("recovery remains explicit and fail-closed", controller.uiState.value is VaultAccessUiState.RecoveryRequired)
            }
        } finally {
            scenario.close()
            syntheticPendingVek.fill(0)
            try { testKey?.discardAbandonedSetup() } catch (_: Exception) { }
        }
    }

    private fun enumPurpose(controller: VaultAccessController, name: String): Any {
        val field = VaultAccessController::class.java.getDeclaredField("purpose")
        field.isAccessible = true
        val enumType = field.type as Class<out Enum<*>>
        return enumType.enumConstants.first { it.name == name }
    }

    @Test
    fun foregroundLossLocksSessionWhenMigrationIsActive() {
        val syntheticKey = ByteArray(32) { 0x31 }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitControllerInitialization(scenario)
        var activeSession: VaultSession? = null
        try {
            scenario.onActivity { activity ->
                val controller = controllerOf(activity)
                activeSession = sessionOf(controller)
                activeSession!!.installVerifiedKey(syntheticKey)
                controller.uiState.value = VaultAccessUiState.Migrating("synthetic lifecycle fixture")
                assertTrue(
                    "Synthetic migration state must be installed before moving Activity lifecycle",
                    controller.uiState.value is VaultAccessUiState.Migrating
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                // Invoke the production Activity callback itself. This verifies MainActivity.onStop wiring;
                // it does not simulate OS-driven stop delivery or process death.
                invokeMainActivityOnStop(activity)
            }

            scenario.onActivity { activity ->
                val controller = controllerOf(activity)
                assertTrue(
                    "Foreground-loss callback must place the controller in Locked state; actual state=${controller.uiState.value}",
                    controller.uiState.value is VaultAccessUiState.Locked
                )
                assertFalse(
                    "MainActivity.onStop must clear the active session key",
                    activeSession!!.isUnlocked()
                )
            }
        } finally {
            scenario.close()
            syntheticKey.fill(0)
        }
    }

    @Test
    fun foregroundLossClearsUnconfirmedRecoveryKeyBuffers() {
        val syntheticPendingVek = ByteArray(32) { 0x52 }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitControllerInitialization(scenario)
        try {
            scenario.onActivity { activity ->
                val controller = controllerOf(activity)
                setPrivateField(controller, "pendingVek", syntheticPendingVek)
                setPrivateField(controller, "pendingRecoveryText", "SYNTHETIC-RECOVERY-CONFIRMATION")
                controller.uiState.value = VaultAccessUiState.ConfirmRecoveryKey(
                    "SYNTHETIC-RECOVERY-KEY",
                    "Synthetic test fixture"
                )
                assertTrue(
                    "Synthetic recovery confirmation must be installed before moving Activity lifecycle",
                    controller.uiState.value is VaultAccessUiState.ConfirmRecoveryKey
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                // Invoke the production Activity callback itself. Real OS Activity stop delivery and
                // process-death behavior remain separate, currently-unverified release checks.
                invokeMainActivityOnStop(activity)
            }

            scenario.onActivity { activity ->
                val controller = controllerOf(activity)
                assertTrue(
                    "Interrupted recovery confirmation must lock the controller; actual state=${controller.uiState.value}",
                    controller.uiState.value is VaultAccessUiState.Locked
                )
                assertTrue(
                    "Pending recovery VEK bytes must be zeroed",
                    syntheticPendingVek.all { it == 0.toByte() }
                )
                assertNull(
                    "Controller must drop its pending recovery VEK reference",
                    privateField(controller, "pendingVek")
                )
                assertNull(
                    "Controller must drop its pending recovery text",
                    privateField(controller, "pendingRecoveryText")
                )
            }
        } finally {
            scenario.close()
            syntheticPendingVek.fill(0)
        }
    }

    @Test
    fun explicitLockCancelsPendingUnlockPromptButPreservesExistingWrappingAlias() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitControllerInitialization(scenario)
        var testKey: com.kaagazvault.security.AuthBoundVaultKey? = null
        try {
            scenario.onActivity { activity ->
                val controller = controllerOf(activity)
                val alias = com.kaagazvault.security.AuthBoundVaultKey().newAliasForRecoveryOrSetup()
                val key = com.kaagazvault.security.AuthBoundVaultKey(alias = alias)
                val cipher = key.prepareSetupCipher()
                testKey = key
                setPrivateField(controller, "purpose", enumPurpose(controller, "UNLOCK"))
                setPrivateField(controller, "authKey", key)
                setPrivateField(controller, "authAlias", alias)
                setPrivateField(controller, "activeCipher", cipher)
                controller.uiState.value = VaultAccessUiState.Locked("Synthetic unlock prompt")
                controller.lockNow("Synthetic explicit lock")

                assertTrue("explicit lock must invalidate the pending unlock", privateField(controller, "purpose") == null)
                assertNull("explicit lock must detach the pending CryptoObject", privateField(controller, "activeCipher"))
                assertTrue("unlock cancellation must preserve the existing wrapping alias", key.hasWrappingKeyAlias())
                assertTrue("unlock cancellation must remain locked", controller.uiState.value is VaultAccessUiState.Locked)
            }
        } finally {
            scenario.close()
            try { testKey?.discardAbandonedSetup() } catch (_: Exception) { }
        }
    }

    @Test
    fun screenOffCancelsPendingSetupPromptAndRemovesItsUncommittedAlias() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitControllerInitialization(scenario)
        var testKey: com.kaagazvault.security.AuthBoundVaultKey? = null
        try {
            scenario.onActivity { activity ->
                val controller = controllerOf(activity)
                val alias = com.kaagazvault.security.AuthBoundVaultKey().newAliasForRecoveryOrSetup()
                val key = com.kaagazvault.security.AuthBoundVaultKey(alias = alias)
                val cipher = key.prepareSetupCipher()
                testKey = key
                setPrivateField(controller, "purpose", enumPurpose(controller, "SETUP"))
                setPrivateField(controller, "authKey", key)
                setPrivateField(controller, "authAlias", alias)
                setPrivateField(controller, "activeCipher", cipher)
                controller.uiState.value = VaultAccessUiState.SetupRequired()

                val receiverField = MainActivity::class.java.getDeclaredField("screenOffReceiver")
                receiverField.isAccessible = true
                val receiver = requireNotNull(receiverField.get(activity) as? android.content.BroadcastReceiver)
                receiver.onReceive(activity, android.content.Intent(android.content.Intent.ACTION_SCREEN_OFF))

                assertTrue("screen-off must invalidate the pending setup purpose", privateField(controller, "purpose") == null)
                assertNull("screen-off must detach the pending CryptoObject", privateField(controller, "activeCipher"))
                assertFalse("screen-off cancellation must not leave an orphan setup alias", key.hasWrappingKeyAlias())
                assertTrue("cancelled setup must remain retryable", controller.uiState.value is VaultAccessUiState.SetupRequired)
            }
        } finally {
            scenario.close()
            try { testKey?.discardAbandonedSetup() } catch (_: Exception) { }
        }
    }

    @Test
    fun screenOffReceiverLocksSessionAndClearsAuthenticatedAccess() {
        val syntheticKey = ByteArray(32) { 0x61 }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitControllerInitialization(scenario)
        try {
            scenario.onActivity { activity ->
                val controller = controllerOf(activity)
                val session = sessionOf(controller)
                session.installVerifiedKey(syntheticKey)
                controller.uiState.value = VaultAccessUiState.Migrating("synthetic screen-off fixture")

                // Invoke the exact registered receiver callback without broadcasting a protected
                // system action. This verifies the production receiver-to-controller wiring.
                val receiverField = MainActivity::class.java.getDeclaredField("screenOffReceiver")
                receiverField.isAccessible = true
                val receiver = requireNotNull(receiverField.get(activity) as? android.content.BroadcastReceiver)
                receiver.onReceive(activity, android.content.Intent(android.content.Intent.ACTION_SCREEN_OFF))

                assertTrue(
                    "Screen-off receiver must lock the controller",
                    controller.uiState.value is VaultAccessUiState.Locked
                )
                assertFalse(
                    "Screen-off receiver must clear the active session key",
                    session.isUnlocked()
                )
            }
        } finally {
            scenario.close()
            syntheticKey.fill(0)
        }
    }

    /**
     * Wait for MainActivity's Compose LaunchedEffect to finish controller.initialize() before
     * tests replace the UI state with synthetic lifecycle fixtures. Otherwise delayed state discovery
     * can overwrite those fixtures before the foreground-loss lifecycle callback is observed.
     */
    private fun awaitControllerInitialization(scenario: ActivityScenario<MainActivity>) {
        var initialized = false
        repeat(40) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                initialized = controllerOf(activity).uiState.value !is VaultAccessUiState.Initializing
            }
            if (initialized) return
            android.os.SystemClock.sleep(50)
        }
        assertTrue("VaultAccessController did not finish initial state discovery", initialized)
    }

    private fun invokeMainActivityOnStop(activity: MainActivity) {
        val method = MainActivity::class.java.getDeclaredMethod("onStop")
        method.isAccessible = true
        method.invoke(activity)
    }

    private fun controllerOf(activity: MainActivity): VaultAccessController {
        val field = MainActivity::class.java.getDeclaredField("accessController")
        field.isAccessible = true
        return requireNotNull(field.get(activity) as? VaultAccessController) {
            "MainActivity did not retain its access controller"
        }
    }

    private fun sessionOf(controller: VaultAccessController): VaultSession {
        val field = VaultAccessController::class.java.getDeclaredField("session")
        field.isAccessible = true
        return requireNotNull(field.get(controller) as? VaultSession) {
            "VaultAccessController did not retain its test session"
        }
    }

    private fun privateField(target: Any, name: String): Any? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }
}
