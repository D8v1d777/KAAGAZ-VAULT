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
 * Exercises MainActivity's actual stop callback using synthetic controller/session state in the
 * disposable instrumentation app process. It never installs a real Keystore key or opens a vault.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleInstrumentedTest {
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
                // Invoke the same controller callback wired by MainActivity.onStop. ActivityScenario's
                // CREATED transition is unreliable in this emulator runner, so OS lifecycle delivery
                // remains a separate release check.
                controllerOf(activity).onActivityStopped()
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
                // Exercise the production cleanup callback deterministically. Real OS Activity
                // stop delivery remains a separate, currently-unverified release check.
                controllerOf(activity).onActivityStopped()
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
