"""Validate the closed diagnostic schema without importing/running the ADB collector."""
import ast
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]

def collector_pattern():
    tree = ast.parse((ROOT / 'scripts/monitor_payment_timing.py').read_text(encoding='utf-8'))
    assignment = next(n for n in tree.body if isinstance(n, ast.Assign)
                      and any(isinstance(t, ast.Name) and t.id == 'pattern' for t in n.targets))
    return re.compile(ast.literal_eval(assignment.value.args[0]))

def sample(stage):
    return ('11-17 14:14:14.984 27485 27485 I PayTiming: '
            'id=43 source=ALIPAY_ACCESSIBILITY stage=' + stage +
            ' uptime_ms=119127227 elapsed_ms=9 age_ms=-1 count=9')

class DiagnosticSchemaTest(unittest.TestCase):
    def test_explicit_rejection_and_read_paths_survive_the_collector(self):
        enum_source = (ROOT / 'app/src/main/java/com/pixel/moneytracker/service/PaymentTraceRecorder.kt').read_text(encoding='utf-8')
        enum = re.search(r'enum class TraceStage\s*\{([^}]+)\}', enum_source).group(1)
        declared = set(re.findall(r'\b[A-Z][A-Z_]+\b', enum))
        required = ['ROOT_SNAPSHOT', 'SOURCE_SNAPSHOT', 'ROOT_UNAVAILABLE',
                    'SOURCE_UNAVAILABLE', 'REJECT_TRANSIENT_PAGE', 'REJECT_BLOCKED_STATUS',
                    'REJECT_NO_COMPLETED_STATUS', 'REJECT_NO_RECEIPT_CONTEXT',
                    'REJECT_NO_AMOUNT', 'REJECT_ZERO_AMOUNT', 'REJECT_INVALID_AMOUNT']
        for stage in required:
            with self.subTest(stage=stage):
                self.assertIn(stage, declared)
                self.assertIsNotNone(collector_pattern().search(sample(stage)))

    def test_unknown_stages_and_financial_payloads_are_rejected(self):
        pattern = collector_pattern()
        for value in [sample('MERCHANT_SECRET'), sample('SNAPSHOT') + ' amount=18.80',
                      sample('PARSE_REJECT') + ' text=private',
                      sample('SNAPSHOT').replace('count=9', 'count=商户')]:
            self.assertIsNone(pattern.search(value))
        self.assertIsNotNone(pattern.search(sample('PARSE_REJECT')))

if __name__ == '__main__':
    unittest.main(verbosity=2)
