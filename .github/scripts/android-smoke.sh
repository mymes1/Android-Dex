#!/usr/bin/env bash
set -euo pipefail
PACKAGE=com.shrey.androiddex.standalone
DEBUG_PACKAGE="$PACKAGE.debug"
SERVICE="$DEBUG_PACKAGE/com.shrey.androiddex.standalone.system.DesktopAccessibilityService"
mkdir -p "$REPORT_DIR"
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

# Install the actual optimized, release-mode APK, not a debug stand-in.
adb install -r "$APK_DIR/Shreys-Android-DeX-1.0.0-TEST-RELEASE.apk"
adb logcat -c
adb shell am start -W -n "$PACKAGE/.MainActivity" | tee "$REPORT_DIR/release-startup.txt"
grep -q 'Status: ok' "$REPORT_DIR/release-startup.txt"
adb shell pidof "$PACKAGE"
adb shell uiautomator dump /sdcard/dex-window.xml >/dev/null
adb shell cat /sdcard/dex-window.xml > "$REPORT_DIR/release-window.xml"
grep -q 'Your tablet. Your desktop.' "$REPORT_DIR/release-window.xml"
adb exec-out screencap -p > "$REPORT_DIR/release-onboarding.png"
if adb logcat -d -s AndroidRuntime:E | grep -F "$PACKAGE"; then
  echo 'Release startup produced a fatal runtime exception' >&2
  exit 1
fi

# Test-harness-only authorization on a disposable emulator. Application code never writes these settings.
adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
adb install -r "$APK_DIR/app-debug.apk"
adb install -r "$APK_DIR/app-debug-androidTest.apk"
adb shell am start -W -n "$DEBUG_PACKAGE/.MainActivity" >/dev/null
adb shell settings get secure enabled_accessibility_services | tee "$REPORT_DIR/accessibility-setting.txt"
set +e
adb shell am instrument -w -r -e notClass com.shrey.androiddex.standalone.ShizukuDeviceWorkflowTest "$DEBUG_PACKAGE.test/androidx.test.runner.AndroidJUnitRunner" | tee "$REPORT_DIR/instrumentation.txt"
INSTRUMENT_STATUS=${PIPESTATUS[0]}
set -e
adb exec-out screencap -p > "$REPORT_DIR/after-tests.png"
adb logcat -d > "$REPORT_DIR/logcat.txt"
adb shell settings put secure enabled_accessibility_services ''
adb shell settings put secure accessibility_enabled 0
if [[ "$INSTRUMENT_STATUS" -ne 0 ]] || ! grep -Eq '^OK \([0-9]+ tests?\)' "$REPORT_DIR/instrumentation.txt"; then
  echo 'Instrumentation tests failed or did not complete' >&2
  exit 1
fi
# Exercise the real official Shizuku dialog and UserService. ADB starts the service only
# on this disposable CI emulator; the app itself never starts Shizuku or grants permission.
adb install -r "$APK_DIR/shizuku.apk"
adb shell monkey -p moe.shizuku.privileged.api -c android.intent.category.LAUNCHER 1 >/dev/null
for i in $(seq 1 20); do
  if adb shell test -f /sdcard/Android/data/moe.shizuku.privileged.api/start.sh; then break; fi
  sleep 1
done
adb shell 'nohup sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh >/data/local/tmp/shizuku-ci.log 2>&1 &'
sleep 3
adb shell cat /data/local/tmp/shizuku-ci.log > "$REPORT_DIR/shizuku-start.txt" || true
set +e
adb shell am instrument -w -r -e class com.shrey.androiddex.standalone.ShizukuDeviceWorkflowTest "$DEBUG_PACKAGE.test/androidx.test.runner.AndroidJUnitRunner" | tee "$REPORT_DIR/shizuku-instrumentation.txt"
SHIZUKU_STATUS=${PIPESTATUS[0]}
set -e
adb exec-out screencap -p > "$REPORT_DIR/shizuku-authorized.png"
adb shell uiautomator dump /sdcard/shizuku-window.xml >/dev/null || true
adb shell cat /sdcard/shizuku-window.xml > "$REPORT_DIR/shizuku-window.xml" || true
if [[ "$SHIZUKU_STATUS" -ne 0 ]] || ! grep -Eq '^OK \([0-9]+ tests?\)' "$REPORT_DIR/shizuku-instrumentation.txt"; then
  echo 'Official Shizuku authorization/task-bridge test failed' >&2
  exit 1
fi
printf 'Release startup, desktop/Accessibility instrumentation, and official Shizuku authorization/task bridge passed on the API 35 emulator. Wireless pairing and physical peripherals remain device-only tests.\n' | tee "$REPORT_DIR/summary.txt"
