"""Static safety/wiring checks. Runtime UI behavior is covered by PendingPaymentDeviceTest."""
import pathlib
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/pixel/moneytracker"

class PendingPaymentContractTest(unittest.TestCase):
    def test_activity_is_private_and_needs_no_overlay_permission(self):
        root = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        ns = "{http://schemas.android.com/apk/res/android}"
        activity = next((x for x in root.findall("application/activity") if x.get(ns + "name") == ".PaymentConfirmActivity"), None)
        self.assertIsNotNone(activity, "Missing notification confirmation Activity")
        self.assertEqual("false", activity.get(ns + "exported"))
        text = (SRC / "PaymentConfirmActivity.kt").read_text(encoding="utf-8")
        self.assertIn("ComponentActivity()", text)
        self.assertNotIn("canDrawOverlays", text)
        self.assertIn("PaymentConfirmDialog(", text)
        self.assertIn("detachCandidate", text)

    def test_notification_is_direct_private_and_candidate_specific(self):
        path = SRC / "service/PendingPaymentNotifications.kt"
        self.assertTrue(path.exists(), "Missing pending payment notification publisher")
        text = path.read_text(encoding="utf-8")
        for required in ["PendingIntent.getActivity", "FLAG_IMMUTABLE", "PaymentConfirmActivity::class.java", "IMPORTANCE_HIGH", "VISIBILITY_PRIVATE", "setPublicVersion", "areNotificationsEnabled", "SecurityException", "candidate.id"]:
            self.assertIn(required, text)
        for forbidden in ["setFullScreenIntent", "PendingIntent.getService", "PendingIntent.getBroadcast", "startActivity("]:
            self.assertNotIn(forbidden, text)

    def test_dormant_candidate_resolution_stays_private_without_promoting_windows(self):
        text = (SRC / "service/FloatingWindowService.kt").read_text(encoding="utf-8")
        for required in ["resolveCandidate(", "detachCandidate", "TYPE_APPLICATION_OVERLAY"]:
            self.assertIn(required, text)
        self.assertNotIn("TYPE_ACCESSIBILITY_OVERLAY", text)
        self.assertIn("PaymentPopupActivity::class.java", text)
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        ns = "{http://schemas.android.com/apk/res/android}"
        popup = next(a for a in manifest.findall("application/activity") if a.get(ns + "name") == ".PaymentPopupActivity")
        self.assertEqual("false", popup.get(ns + "exported"))
        self.assertEqual("true", popup.get(ns + "excludeFromRecents"))
        self.assertEqual("singleTask", popup.get(ns + "launchMode"))

if __name__ == "__main__":
    unittest.main()
