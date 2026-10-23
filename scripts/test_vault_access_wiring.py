"""Regression guards for production vault access wiring.

These source-level checks complement (not replace) Kotlin unit tests and Android
instrumentation. They prevent accidental bypasses when the UI is refactored.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kaagazvault/MainActivity.kt"
CONTROLLER = ROOT / "app/src/main/java/com/kaagazvault/security/VaultAccessController.kt"
REMINDER_WORKER = ROOT / "app/src/main/java/com/kaagazvault/reminders/ReminderScheduler.kt"
WORKFLOW = ROOT / ".github/workflows/android-ci.yml"


class VaultAccessWiringTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = MAIN.read_text(encoding="utf-8")
        cls.controller = CONTROLLER.read_text(encoding="utf-8")
        cls.worker = REMINDER_WORKER.read_text(encoding="utf-8")
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")


    def test_emulator_pin_setup_and_cleanup_stay_in_one_shell(self):
        job_start = self.workflow.index("  android-instrumented-tests:")
        job = self.workflow[job_start:]
        marker = "          script: >-\n"
        script_start = job.index(marker) + len(marker)
        # Bound the script to its own YAML step. Later CI steps (including the screenshot
        # artifact upload) must not be misread as extra emulator script lines.
        script = job[script_start:].split("\n      - name:", 1)[0].strip()

        # android-emulator-runner executes each newline-delimited script line in a
        # separate shell, so state setup, its EXIT cleanup trap, and Gradle must be one line.
        self.assertNotIn("\n", script)
        self.assertIn("adb shell locksettings set-pin 2468", script)
        self.assertIn("trap 'status=$?; adb shell locksettings clear --old 2468", script)
        self.assertIn("&& ./gradlew --no-daemon :app:connectedDebugAndroidTest", script)

    def test_runtime_ui_capture_is_separate_and_artifact_upload_is_pinned(self):
        self.assertIn("&& python3 scripts/capture_runtime_ui.py", self.workflow)
        self.assertIn("uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2", self.workflow)
        self.assertIn("retention-days: 7", self.workflow)

    def test_production_activity_uses_biometric_compatible_host_and_controller(self):
        self.assertIn("class MainActivity : FragmentActivity()", self.main)
        self.assertIn("VaultAccessController(this, VaultSession())", self.main)
        self.assertIn("LaunchedEffect(Unit) { controller.initialize() }", self.main)
        self.assertIn("WindowManager.LayoutParams.FLAG_SECURE", self.main)

    def test_activity_stop_locks_before_super_and_destroy_disposes_controller(self):
        stop_start = self.main.index("override fun onStop()")
        destroy_start = self.main.index("override fun onDestroy()", stop_start)
        stop_body = self.main[stop_start:destroy_start]
        destroy_end = self.main.index("\n    }", destroy_start) + len("\n    }")
        destroy_body = self.main[destroy_start:destroy_end]

        self.assertIn("accessController?.onActivityStopped()", stop_body)
        self.assertLess(
            stop_body.index("accessController?.onActivityStopped()"),
            stop_body.index("super.onStop()"),
        )
        self.assertIn("accessController?.dispose()", destroy_body)
        self.assertLess(
            destroy_body.index("accessController?.dispose()"),
            destroy_body.index("accessController = null"),
        )
        self.assertIn("ioExecutor.shutdownNow()", destroy_body)
        self.assertLess(
            destroy_body.index("ioExecutor.shutdownNow()"),
            destroy_body.index("super.onDestroy()"),
        )

    def test_background_lock_clears_unconfirmed_recovery_material(self):
        lifecycle_start = self.controller.index("fun onActivityStopped()")
        lifecycle_end = self.controller.index("fun dispose()", lifecycle_start)
        lifecycle_body = self.controller[lifecycle_start:lifecycle_end]

        self.assertIn("is VaultAccessUiState.Ready, is VaultAccessUiState.Migrating", lifecycle_body)
        self.assertIn("lockNow(", lifecycle_body)
        confirmation_start = lifecycle_body.index("is VaultAccessUiState.ConfirmRecoveryKey")
        confirmation_end = lifecycle_body.index("\n            else -> if (purpose != null)", confirmation_start)
        confirmation_body = lifecycle_body[confirmation_start:confirmation_end]
        self.assertIn("cancelPendingPrompt(", lifecycle_body)
        self.assertIn("pendingVek?.fill(0)", confirmation_body)
        self.assertIn("pendingVek = null", confirmation_body)
        self.assertIn("pendingRecoveryText = null", confirmation_body)
        self.assertIn("VaultAccessUiState.Locked", confirmation_body)

    def test_document_home_is_only_created_for_ready_state(self):
        state_switch = self.main.index("when (val state = accessState)")
        ready_branch = self.main.index("is VaultAccessUiState.Ready ->", state_switch)
        home_creation = self.main.index("VaultHomeScreen(", ready_branch)
        fallback = self.main.index("else -> VaultAccessScreen", ready_branch)
        self.assertLess(ready_branch, home_creation)
        self.assertLess(home_creation, fallback)
        self.assertNotIn("EncryptedDocumentStore(", self.main[:self.main.index("private fun VaultHomeScreen(")])

    def test_lock_invalidates_pending_migration_and_closes_database_after_session_lock(self):
        lock_start = self.controller.index("fun lockNow(")
        lock_end = self.controller.index("fun onActivityStopped(", lock_start)
        lock_body = self.controller[lock_start:lock_end]
        self.assertIn("accessEpochGate.invalidate", lock_body)
        self.assertIn("runtime.also { runtime = null }", lock_body)
        self.assertLess(lock_body.index("session.lock()"), lock_body.index("previousRuntime?.databaseProvider?.close()"))
        self.assertIn("migrationEpoch != currentEpoch || !session.isUnlocked()", self.controller)
        self.assertIn("postStateIfEpoch(VaultAccessUiState.Ready(next), migrationEpoch)", self.controller)

    def test_migration_closes_legacy_provider_on_success_and_all_providers_on_failure(self):
        self.assertIn("oldProvider?.close()", self.controller)
        self.assertIn("oldProvider = null", self.controller)
        self.assertIn("newProvider?.close()", self.controller)
        self.assertIn("accessEpochGate.withLock", self.controller)

    def test_dispose_invalidates_late_authentication_callbacks_and_clears_wrapped_key(self):
        dispose_start = self.controller.index("fun dispose()")
        dispose_end = self.controller.index("private fun startPrompt(", dispose_start)
        dispose_body = self.controller[dispose_start:dispose_end]
        self.assertIn("disposed = true", dispose_body)
        self.assertIn("clearPrompt()", dispose_body)
        self.assertLess(dispose_body.index("clearPrompt()"), dispose_body.index("prompt.cancelAuthentication()"))
        self.assertIn("if (disposed) return", self.controller)
        self.assertIn("if (!disposed && accessEpochGate.currentEpoch() == expectedEpoch)", self.controller)

    def test_synchronous_auth_prompt_failures_clear_recovery_state(self):
        setup_start = self.controller.index("fun beginSetup()")
        setup_end = self.controller.index("fun beginUnlock()", setup_start)
        setup_body = self.controller[setup_start:setup_end]
        self.assertIn("clearPrompt()", setup_body)
        self.assertIn("setupKey?.discardAbandonedSetup()", setup_body)

        recovery_start = self.controller.index("fun recoverWithKey(")
        recovery_end = self.controller.index("fun confirmRecoveryKeyStored(", recovery_start)
        recovery_body = self.controller[recovery_start:recovery_end]
        self.assertIn("pendingVek?.fill(0)", recovery_body)
        self.assertIn("pendingVek = null", recovery_body)
        self.assertIn("pendingRecoveryText = null", recovery_body)
        self.assertIn("clearPrompt()", recovery_body)
        self.assertIn("recoveryWrappingKey?.discardAbandonedSetup()", recovery_body)

    def test_verified_key_installation_is_serialized_with_epoch_invalidation(self):
        install_start = self.controller.index("private fun installAndMigrate(")
        install_end = self.controller.index("private fun migrate()", install_start)
        install_body = self.controller[install_start:install_end]
        gate_start = install_body.index("accessEpochGate.withLock {")
        key_install = install_body.index("session.installVerifiedKey(vek)")
        gate_end = install_body.index("\n            }\n        } catch", gate_start)
        self.assertLess(gate_start, key_install)
        self.assertLess(key_install, gate_end)
        self.assertIn("if (disposed) throw VaultLockedException()", install_body)

        lock_start = self.controller.index("fun lockNow(")
        lock_end = self.controller.index("fun onActivityStopped(", lock_start)
        lock_body = self.controller[lock_start:lock_end]
        self.assertIn("val previousRuntime = accessEpochGate.invalidate", lock_body)
        self.assertIn("session.lock()", lock_body)
        self.assertLess(
            lock_body.index("accessEpochGate.invalidate"),
            lock_body.index("previousRuntime?.databaseProvider?.close()"),
        )

    def test_recovery_confirmation_does_not_zero_the_vek_being_transferred(self):
        start = self.controller.index("private fun prepareRecoveryConfirmation(")
        end = self.controller.index("private fun installAndMigrate(", start)
        body = self.controller[start:end]

        # finishRecoveryRewrap passes pendingVek itself into this method. Wiping the
        # old pending reference unconditionally would zero the same VEK before the
        # user confirms it, then persist a zeroed key into the authenticated session.
        self.assertIn("val previousPendingVek = pendingVek", body)
        self.assertIn("if (previousPendingVek !== vek) previousPendingVek?.fill(0)", body)
        self.assertIn("if (pendingVek === vek) pendingVek = null", body)
        self.assertIn("pendingVek = vek", body)

    def test_recovery_rewrap_prompt_failure_discards_uncommitted_keystore_alias(self):
        failure_start = self.controller.index("private fun onPromptFailure(")
        failure_end = self.controller.index("private fun clearPrompt(", failure_start)
        failure_body = self.controller[failure_start:failure_end]
        recovery_start = failure_body.index("Purpose.RECOVERY_REWRAP ->")
        recovery_end = failure_body.index("else -> state(", recovery_start)
        recovery_failure = failure_body[recovery_start:recovery_end]

        self.assertIn("key?.discardAbandonedSetup()", recovery_failure)
        self.assertLess(
            recovery_failure.index("pendingVek?.fill(0)"),
            recovery_failure.index("key?.discardAbandonedSetup()"),
        )

    def test_stale_migration_failure_cannot_lock_a_newer_authenticated_session(self):
        migration_start = self.controller.index("private fun migrate()")
        catch_start = self.controller.index("} catch (failure: Exception) {", migration_start)
        catch_end = self.controller.index("try { newProvider?.close()", catch_start)
        failure_cleanup = self.controller[catch_start:catch_end]
        self.assertIn("accessEpochGate.ifCurrent(migrationEpoch)", failure_cleanup)
        self.assertIn("accessEpochGate.ifCurrent(migrationEpoch)", failure_cleanup)
        self.assertLess(
            failure_cleanup.index("accessEpochGate.ifCurrent(migrationEpoch)"),
            failure_cleanup.index("session.lock()"),
        )
        self.assertIn("if (disposed) throw VaultLockedException()", self.controller)
        self.assertIn("if (!session.isUnlocked()) session.installVerifiedKey(vek)", self.controller)

    def test_background_reminder_worker_does_not_open_encrypted_database(self):
        worker_start = self.worker.index("internal class ReminderNotificationWorker")
        worker = self.worker[worker_start:]
        self.assertNotIn("VaultDatabaseProvider", worker)
        self.assertNotIn("ReminderRepository", worker)
        self.assertIn('"You have a reminder in Kaagaz Vault."', worker)
        self.assertNotIn("reminder.title", worker)
        self.assertNotIn("documentName", worker)


if __name__ == "__main__":
    unittest.main()
