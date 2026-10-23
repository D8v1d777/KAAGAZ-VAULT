package com.kaagazvault

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaagazvault.security.VaultAccessController
import com.kaagazvault.security.VaultAccessUiState
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Produces a genuine screenshot of the production MainActivity in the non-sensitive SetupRequired
 * state. It temporarily clears FLAG_SECURE only after asserting that state, captures pixels through
 * UiAutomation, restores FLAG_SECURE immediately, and writes the screenshot to app-specific
 * external storage so the CI host can retrieve it. The test never taps setup or handles a key.
 */
@RunWith(AndroidJUnit4::class)
class RuntimeUiScreenshotInstrumentedTest {
    @Test
    fun capturesRealSetupRequiredScreenAndRestoresSecureWindowFlag() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var screenshot: Bitmap? = null
        try {
            var setupRequired = false
            val deadline = SystemClock.uptimeMillis() + 20_000L
            while (!setupRequired && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    setupRequired = controller(activity).uiState.value is VaultAccessUiState.SetupRequired
                }
                if (!setupRequired) SystemClock.sleep(150L)
            }
            assertTrue(
                "Screenshot is allowed only when production controller confirms SetupRequired",
                setupRequired
            )

            try {
                scenario.onActivity { activity ->
                    assertTrue(
                        "Activity must still be in SetupRequired when screenshot protection is bypassed in this disposable test",
                        controller(activity).uiState.value is VaultAccessUiState.SetupRequired
                    )
                    assertTrue(
                        "Production window should enable FLAG_SECURE",
                        (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
                    )
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                instrumentation.waitForIdleSync()
                screenshot = instrumentation.uiAutomation.takeScreenshot()
            } finally {
                // Restore production screenshot protection immediately after capture, even on error.
                scenario.onActivity { activity ->
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    assertTrue(
                        "FLAG_SECURE must be restored before the image is persisted",
                        (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
                    )
                }
            }

            val bitmap = requireNotNull(screenshot) { "UiAutomation did not return a screenshot bitmap" }
            assertTrue("Screenshot dimensions must be positive", bitmap.width > 0 && bitmap.height > 0)

            // Reject black/redacted frames. Sample across the full display rather than checking size alone.
            val colors = HashSet<Int>()
            var sampled = 0
            var nonBlack = 0
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    colors.add(pixel)
                    if (pixel != AndroidColor.BLACK) nonBlack++
                    sampled++
                    x += 8
                }
                y += 8
            }
            assertTrue(
                "Screenshot appears blank or FLAG_SECURE-redacted; unique sampled colors=${colors.size}",
                colors.size > 24
            )
            assertTrue(
                "Screenshot appears blank; not enough visible non-black pixels",
                nonBlack * 100 > sampled * 3
            )

            val externalRoot = requireNotNull(context.getExternalFilesDir(null)) {
                "App-specific external storage is unavailable for screenshot handoff"
            }
            val output = File(externalRoot, "kaagaz-vault-ui/setup-required-android36.png")
            assertTrue("Could not create screenshot handoff directory", output.parentFile?.mkdirs() == true || output.parentFile?.isDirectory == true)
            output.delete()
            FileOutputStream(output).use { stream ->
                assertTrue("Could not encode screenshot PNG", bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                stream.flush()
            }
            assertTrue("Screenshot file is unexpectedly small", output.length() > 4096L)
        } finally {
            screenshot?.recycle()
            scenario.close()
        }
    }

    private fun controller(activity: MainActivity): VaultAccessController {
        val field = MainActivity::class.java.getDeclaredField("accessController")
        field.isAccessible = true
        return requireNotNull(field.get(activity) as? VaultAccessController)
    }
}
