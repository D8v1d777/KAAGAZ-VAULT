"""Source-level regression guard for the owner-approved screen-off lock policy."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kaagazvault/MainActivity.kt"


class ScreenOffLockPolicyTest(unittest.TestCase):
    def test_screen_off_receiver_locks_and_is_unregistered(self):
        source = MAIN.read_text(encoding="utf-8")
        self.assertIn("Intent.ACTION_SCREEN_OFF", source)
        self.assertIn("ContextCompat.registerReceiver(", source)
        self.assertIn("ContextCompat.RECEIVER_NOT_EXPORTED", source)
        self.assertIn('accessController?.lockNow("Vault locked because the screen turned off.")', source)
        self.assertIn("unregisterReceiver(screenOffReceiver)", source)
        self.assertIn("screenOffReceiverRegistered = false", source)


if __name__ == "__main__":
    unittest.main()
