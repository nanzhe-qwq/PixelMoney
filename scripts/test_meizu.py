"""Explicitly target the Meizu only; fail on test failures, not just adb's exit code."""
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'artifacts' / 'auto-accounting'
ARTIFACTS.mkdir(parents=True, exist_ok=True)
ADB = ['D:/Android/Sdk/platform-tools/adb.exe', '-s', '461QYGE2224W8']
env = dict(os.environ, JAVA_HOME='D:/java17', GRADLE_USER_HOME='D:/Android/gradle-home')

def run(args, timeout=180):
    r = subprocess.run(args, cwd=ROOT, env=env, capture_output=True, encoding='utf-8', errors='replace', timeout=timeout)
    print(r.stdout, r.stderr, flush=True)
    if r.returncode:
        raise SystemExit(r.returncode)
    return r.stdout + r.stderr

if '--skip-build' not in sys.argv:
    run(['cmd.exe', '/c', 'gradlew.bat', ':app:assembleDebug', ':app:assembleDebugAndroidTest', '-Pkotlin.incremental=false', '--console=plain'], 240)
    for apk in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        output = run(ADB + ['install', '-r', '-t', apk])
        if 'Success' not in output:
            raise SystemExit('APK install failed; no uninstall or data clearing attempted')
name = next((x for x in sys.argv[1:] if not x.startswith('--')), 'com.pixel.moneytracker.AutoAccountingDeviceTest')
output = run(ADB + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', name, 'com.pixel.moneytracker.test/androidx.test.runner.AndroidJUnitRunner'])
(ARTIFACTS / 'device-latest.txt').write_text(output, encoding='utf-8')
for name in ['overlay-keyboard.png', 'overlay-keyboard.xml', 'overlay-windows.txt']:
    # Optional diagnostics do not decide test success.
    subprocess.run(ADB + ['pull', '/sdcard/Android/data/com.pixel.moneytracker/files/' + name, str(ARTIFACTS / name)], cwd=ROOT, capture_output=True)
if 'OK (' not in output or 'FAILURES!!!' in output or 'Process crashed' in output:
    raise SystemExit(1)
