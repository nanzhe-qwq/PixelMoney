"""Rollback contract: keep v5 data readable but disable the rejected notification experiment."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/com/pixel/moneytracker'
NS = '{http://schemas.android.com/apk/res/android}'

class OverlayRollbackTest(unittest.TestCase):
    def test_detection_uses_legacy_overlay_without_creating_pending_notifications(self):
        source = (SRC/'service/FloatingWindowService.kt').read_text(encoding='utf-8')
        entry = source.split('override fun onStartCommand', 1)[1].split('@SuppressLint', 1)[0]
        self.assertIn('showFloatingWindow(amount, source, note, isIncome, trace, transactionTime = transactionTime)', entry)
        self.assertNotIn('PaymentPromptDispatcher(', entry)
        self.assertNotIn('PendingPaymentNotifications(', entry)
        self.assertNotIn('pendingStore', entry)

    def test_rejected_activity_and_restore_are_disabled_without_database_downgrade(self):
        manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
        activity = next(a for a in manifest.findall('application/activity') if a.get(NS+'name') == '.PaymentConfirmActivity')
        self.assertEqual('false', activity.get(NS+'enabled'))
        self.assertNotIn('PendingPayments.restoreNotifications', (SRC/'MainActivity.kt').read_text(encoding='utf-8'))
        db = (SRC/'data/database/AppDatabase.kt').read_text(encoding='utf-8')
        self.assertGreaterEqual(int(db.split('version = ')[1].split(',')[0]), 5)
        self.assertIn('MIGRATION_4_5', db)

if __name__ == '__main__':
    unittest.main(verbosity=2)
