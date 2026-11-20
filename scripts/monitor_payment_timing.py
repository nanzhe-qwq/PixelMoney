"""Collect only the diagnostic APK's fixed numeric/enum timing schema, for five minutes."""
from pathlib import Path
import datetime
import json
import queue
import re
import subprocess
import threading
import time

ADB = ['D:/Android/Sdk/platform-tools/adb.exe', '-s', 'b6433f26']
OUT = Path('E:/hermes-workspace/PixelMoney/artifacts/xiaomi15u/diagnostics')
OUT.mkdir(parents=True, exist_ok=True)
stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
output = OUT / ('timing-live-' + stamp + '.jsonl')
stop = OUT / ('timing-live-' + stamp + '.stop')
pattern = re.compile(r'(?P<at>\d\d-\d\d \d\d:\d\d:\d\d\.\d+)\s+(?P<pid>\d+)\s+\d+\s+I PayTiming\s*:\s*id=(?P<id>\d+) source=(?P<source>(?:ALIPAY|WECHAT)_(?:ACCESSIBILITY|NOTIFICATION)) stage=(?P<stage>RECEIVED|SNAPSHOT|NO_SCREEN|PARSE_MATCH|PARSE_REJECT|DUPLICATE|SERVICE_REQUEST|SERVICE_ACCEPTED|SERVICE_STARTED|VIEW_ATTACHED|FIRST_PRE_DRAW|ERROR|ROOT_SNAPSHOT|SOURCE_SNAPSHOT|ROOT_UNAVAILABLE|SOURCE_UNAVAILABLE|REJECT_TRANSIENT_PAGE|REJECT_BLOCKED_STATUS|REJECT_NO_COMPLETED_STATUS|REJECT_NO_RECEIPT_CONTEXT|REJECT_NO_AMOUNT|REJECT_ZERO_AMOUNT|REJECT_INVALID_AMOUNT) uptime_ms=(?P<uptime_ms>\d+) elapsed_ms=(?P<elapsed_ms>\d+) age_ms=(?P<age_ms>-?\d+) count=(?P<count>-?\d+)\s*$')
proc = subprocess.Popen(ADB + ['logcat', '-T', '1', '-v', 'threadtime', 'PayTiming:I', '*:S'],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                        text=True, encoding='utf-8', errors='replace')
lines = queue.Queue()
def reader():
    if proc.stdout is not None:
        for line in proc.stdout:
            lines.put(line)
threading.Thread(target=reader, daemon=True).start()
print(json.dumps({'event': 'timing_monitor_ready', 'serial': 'b6433f26', 'log': str(output),
                  'stop_signal': str(stop), 'seconds': 300}, ensure_ascii=False), flush=True)
end = time.monotonic() + 300
stages = {}
count = 0
try:
    with output.open('x', encoding='utf-8') as f:
        while time.monotonic() < end and not stop.exists():
            if proc.poll() is not None and lines.empty():
                raise RuntimeError('ADB timing stream ended before observation completed')
            try:
                line = lines.get(timeout=0.5)
            except queue.Empty:
                continue
            match = pattern.search(line)
            if match is None:
                continue
            item = match.groupdict()
            f.write(json.dumps(item, ensure_ascii=False) + '\n')
            f.flush()
            count += 1
            key = (item['pid'], item['id'], item['source'])
            stage = item['stage']
            stages.setdefault(key, []).append(stage)
            if stage in ['FIRST_PRE_DRAW', 'ERROR']:
                print(json.dumps({'event': 'timing_result', 'at': item['at'], 'pid': item['pid'],
                                  'id': item['id'], 'source': item['source'], 'stage': stage,
                                  'elapsed_ms': int(item['elapsed_ms']), 'stages': stages[key]}, ensure_ascii=False), flush=True)
    print(json.dumps({'event': 'timing_monitor_finished', 'accepted_lines': count, 'log': str(output)}, ensure_ascii=False), flush=True)
finally:
    proc.terminate()
    try:
        proc.wait(timeout=5)
    except subprocess.TimeoutExpired:
        proc.kill()
