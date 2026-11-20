"""Run sample-replay tests on an explicitly selected physical Android device.

No uninstall, data clearing, permission grant, emulator, or real-payment operation.
The caller must reinstall Release after testing because this runner installs Debug.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--skip-build', action='store_true')
parser.add_argument('--test-class', default='com.pixel.moneytracker.AutoAccountingDeviceTest')
args = parser.parse_args()
if args.serial.startswith('emulator-') or not re.fullmatch(r'[A-Za-z0-9._:-]+', args.serial):
    parser.error('An explicit physical-device serial is required')
ADB = ['D:/Android/Sdk/platform-tools/adb.exe', '-s', args.serial]
ARTIFACTS = ROOT / 'artifacts' / 'device-tests' / args.serial
ARTIFACTS.mkdir(parents=True, exist_ok=True)
env = dict(os.environ, JAVA_HOME='D:/java17', GRADLE_USER_HOME='D:/Android/gradle-home')

def run(command, timeout=180):
    result = subprocess.run(command, cwd=ROOT, env=env, capture_output=True,
                            encoding='utf-8', errors='replace', timeout=timeout)
    print(result.stdout, result.stderr, flush=True)
    if result.returncode:
        raise SystemExit(result.returncode)
    return result.stdout + result.stderr

if run(ADB + ['get-state']).strip() != 'device':
    raise SystemExit('Target device is not ready')
if run(ADB + ['shell', 'getprop', 'ro.kernel.qemu']).strip() == '1':
    raise SystemExit('Emulator refused')
if not args.skip_build:
    run(['cmd.exe', '/c', 'gradlew.bat', ':app:assembleDebug', ':app:assembleDebugAndroidTest',
         '-Pkotlin.incremental=false', '--console=plain'], 240)
for apk in ['app/build/outputs/apk/debug/app-debug.apk',
            'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
    output = run(ADB + ['install', '-r', '-t', apk])
    if 'Success' not in output:
        raise SystemExit('APK installation failed; no uninstall or clearing attempted')
# Xiaomi may leave Settings/launcher foreground after installation; establish the app first.
launch = run(ADB + ['shell', 'am', 'start', '-W', '-n', 'com.pixel.moneytracker/.MainActivity'])
if 'Status: ok' not in launch:
    raise SystemExit('Cannot establish foreground activity before test instrumentation')
output = run(ADB + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                    args.test_class, 'com.pixel.moneytracker.test/androidx.test.runner.AndroidJUnitRunner'])
(ARTIFACTS / 'device-latest.txt').write_text(output, encoding='utf-8')
for name in ['overlay-keyboard.png', 'overlay-keyboard.xml', 'overlay-windows.txt']:
    subprocess.run(ADB + ['pull', '/sdcard/Android/data/com.pixel.moneytracker/files/' + name,
                         str(ARTIFACTS / name)], cwd=ROOT, capture_output=True, timeout=30)
if not re.search(r'OK \([1-9][0-9]* tests?\)', output) or any(
        marker in output for marker in ['FAILURES!!!', 'Process crashed', 'INSTRUMENTATION_FAILED']):
    raise SystemExit(1)
