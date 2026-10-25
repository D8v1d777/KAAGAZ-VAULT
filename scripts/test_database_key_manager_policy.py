"""Source-level regression guards for the post-migration SQLCipher key boundary.

These checks complement JVM and Android instrumentation tests. They ensure normal
session-bound database opening cannot silently fall back to the legacy wrapper.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/kaagazvault/database/DatabaseKeyManager.kt"


class DatabaseKeyManagerPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")
        start = cls.source.index("fun getOrCreateKey()")
        end = cls.source.index("fun rewrapLegacyKeyUnderVek()", start)
        cls.open_path = cls.source[start:end]

    def test_session_bound_open_fails_closed_before_legacy_wrapper_fallback(self):
        guard = "if (vekKeyAccess != null && keyFile.exists())"
        legacy_fallback = "if (keyFile.exists()) return readLegacyKey()"
        self.assertIn(guard, self.open_path)
        self.assertIn("VEK-wrapped database key is missing; refusing legacy-key fallback", self.open_path)
        self.assertLess(self.open_path.index(guard), self.open_path.index(legacy_fallback))

    def test_legacy_key_read_remains_only_in_explicit_migration_rewrap_path(self):
        start = self.source.index("fun rewrapLegacyKeyUnderVek()")
        end = self.source.index("private fun readLegacyKey()", start)
        migration_path = self.source[start:end]
        self.assertIn("readLegacyKey()", migration_path)
        self.assertIn("vekStore.saveIfAbsent(databaseKey)", migration_path)


if __name__ == "__main__":
    unittest.main()
