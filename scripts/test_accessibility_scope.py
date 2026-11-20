"""Guard the least-privilege candidate's declared scope, not a claim of bank compatibility."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'

class AccessibilityScopeTest(unittest.TestCase):
    def test_current_page_reading_without_window_enumeration_or_resource_ids(self):
        config = ET.parse(ROOT / 'app/src/main/res/xml/accessibility_service_config.xml').getroot()
        flags = set(config.attrib[ANDROID + 'accessibilityFlags'].split('|'))
        self.assertNotIn('flagRetrieveInteractiveWindows', flags)
        self.assertNotIn('flagReportViewIds', flags)
        self.assertEqual({'flagDefault'}, flags)
        self.assertEqual('true', config.attrib[ANDROID + 'canRetrieveWindowContent'])
        self.assertNotEqual('true', config.attrib.get(ANDROID + 'canTakeScreenshot'))
        self.assertNotEqual('true', config.attrib.get(ANDROID + 'canPerformGestures'))
        self.assertNotEqual('true', config.attrib.get(ANDROID + 'isAccessibilityTool'))
        self.assertEqual({'com.eg.android.AlipayGphone', 'com.tencent.mm', 'com.taobao.taobao',
                          'com.xunmeng.pinduoduo', 'com.jingdong.app.mall',
                          'com.sankuai.meituan', 'me.ele'},
                         set(config.attrib[ANDROID + 'packageNames'].split(',')))
        self.assertEqual({'typeWindowContentChanged', 'typeWindowStateChanged'},
                         set(config.attrib[ANDROID + 'accessibilityEventTypes'].split('|')))
        self.assertEqual('300', config.attrib[ANDROID + 'notificationTimeout'])
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        service = next(n for n in manifest.findall('application/service')
                       if n.attrib.get(ANDROID + 'name') == '.service.PaymentAccessibilityService')
        self.assertEqual('android.permission.BIND_ACCESSIBILITY_SERVICE', service.attrib[ANDROID + 'permission'])
        self.assertEqual('false', service.attrib[ANDROID + 'exported'])
        permissions = {n.attrib[ANDROID + 'name'] for n in manifest.findall('uses-permission')}
        self.assertNotIn('android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION', permissions)
        self.assertNotIn('android.permission.CAPTURE_VIDEO_OUTPUT', permissions)

if __name__ == '__main__':
    unittest.main(verbosity=2)
