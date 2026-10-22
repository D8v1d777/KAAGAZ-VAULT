package com.kaagazvault

import androidx.lifecycle.Lifecycle
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
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        var activeSession: VaultSession? = null
        try {
            scenario.onActivity { activity ->
                val controller = controllerOf(activity)
                activeSession = sessionOf(controller)
                activeSession!!.installVerifiedKey(syntheticKey)
                controller.uiState.value = VaultAccessUiState.Migrating("synthetic lifecycle fixture")
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.moveToState(Lifecycle.State.CREATED)

            scenario.onActivity { activity ->
                val controller = controllerOf(activity)
                assertTrue(
                    "MainActivity.onStop must place the controller in Locked state",
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
        try {
            scenario.onActivity { activity ->
                val controller = controllerOf(activity)
                setPrivateField(controller, "pendingVek", syntheticPendingVek)
                setPrivateField(controller, "pendingRecoveryText", "SYNTHETIC-RECOVERY-CONFIRMATION")
                controller.uiState.value = VaultAccessUiState.ConfirmRecoveryKey(
                    "SYNTHETIC-RECOVERY-KEY",
                    "Synthetic test fixture"
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.moveToState(Lifecycle.State.CREATED)

            scenario.onActivity { activity ->
                val controller = controllerOf(activity)
                assertTrue(
                    "Interrupted recovery confirmation must lock the controller",
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
