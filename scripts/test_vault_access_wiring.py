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


class VaultAccessWiringTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main = MAIN.read_text(encoding="utf-8")
        cls.controller = CONTROLLER.read_text(encoding="utf-8")
        cls.worker = REMINDER_WORKER.read_text(encoding="utf-8")

    def test_production_activity_uses_biometric_compatible_host_and_controller(self):
        self.assertIn("class MainActivity : FragmentActivity()", self.main)
        self.assertIn("VaultAccessController(this, VaultSession())", self.main)
        self.assertIn("LaunchedEffect(Unit) { controller.initialize() }", self.main)
        self.assertIn("WindowManager.LayoutParams.FLAG_SECURE", self.main)

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
