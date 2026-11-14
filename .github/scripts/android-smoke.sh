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
adb shell am instrument -w -r -e notAnnotation org.junit.Ignore "$DEBUG_PACKAGE.test/androidx.test.runner.AndroidJUnitRunner" | tee "$REPORT_DIR/instrumentation.txt"
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
printf 'Release installation/startup and instrumentation completed successfully.\n' | tee "$REPORT_DIR/summary.txt"
