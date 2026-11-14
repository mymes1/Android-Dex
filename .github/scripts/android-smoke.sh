#!/usr/bin/env bash
set -euo pipefail

PACKAGE=com.shrey.androiddex.standalone
DEBUG_PACKAGE="$PACKAGE.debug"
SERVICE="$DEBUG_PACKAGE/com.shrey.androiddex.standalone.system.DesktopAccessibilityService"
: "${APK_DIR:?APK_DIR is required}"
: "${REPORT_DIR:?REPORT_DIR is required}"
mkdir -p "$REPORT_DIR"
PROGRESS="$REPORT_DIR/progress.txt"

step() {
  printf '%s %s\n' "$(date -u +%FT%TZ)" "$*" | tee -a "$PROGRESS"
}

on_exit() {
  local rc=$?
  if [[ "$rc" -ne 0 ]]; then
    step "FAILED with exit code $rc; collecting emulator evidence"
    adb logcat -d > "$REPORT_DIR/logcat.txt" 2>/dev/null || true
    adb shell dumpsys activity activities > "$REPORT_DIR/activities.txt" 2>/dev/null || true
    adb shell dumpsys accessibility > "$REPORT_DIR/accessibility-dump.txt" 2>/dev/null || true
    adb exec-out screencap -p > "$REPORT_DIR/failure.png" 2>/dev/null || true
    adb shell uiautomator dump /sdcard/failure-window.xml > "$REPORT_DIR/failure-dump.txt" 2>&1 || true
    adb shell cat /sdcard/failure-window.xml > "$REPORT_DIR/failure-window.xml" 2>/dev/null || true
  fi
  exit "$rc"
}
trap on_exit EXIT

step 'Waiting for emulator'
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

step 'Installing optimized release APK'
adb install -r "$APK_DIR/Shreys-Android-DeX-1.0.0-TEST-RELEASE.apk"
adb logcat -c
step 'Cold-starting release MainActivity'
adb shell am start -W -n "$PACKAGE/.MainActivity" | tee "$REPORT_DIR/release-startup.txt"
grep -q 'Status: ok' "$REPORT_DIR/release-startup.txt"
sleep 2
adb shell pidof "$PACKAGE" | tee "$REPORT_DIR/release-pid.txt"
test -s "$REPORT_DIR/release-pid.txt"
adb shell dumpsys activity activities > "$REPORT_DIR/activities.txt"
if grep -Eq "mResumedActivity.*$PACKAGE|ResumedActivity.*$PACKAGE" "$REPORT_DIR/activities.txt"; then
  step 'Release activity is resumed'
else
  step 'WARNING: could not confirm resumed activity from dumpsys; checking process and crash log'
fi

# uiautomator can be flaky on headless Compose surfaces; keep its output as evidence,
# but verify release UI through the debug instrumentation suite below as well.
step 'Capturing release onboarding evidence'
timeout 30s adb shell uiautomator dump /sdcard/dex-window.xml > "$REPORT_DIR/uiautomator-dump.txt" 2>&1 || true
adb shell cat /sdcard/dex-window.xml > "$REPORT_DIR/release-window.xml" 2>/dev/null || true
if grep -q 'Your tablet. Your desktop.' "$REPORT_DIR/release-window.xml" 2>/dev/null; then
  step 'Release onboarding text found in uiautomator dump'
else
  step 'WARNING: onboarding text not found in uiautomator dump; screenshot and instrumentation will verify UI'
fi
adb exec-out screencap -p > "$REPORT_DIR/release-onboarding.png"
adb logcat -d -s AndroidRuntime:E > "$REPORT_DIR/release-crashes.txt"
if grep -F "$PACKAGE" "$REPORT_DIR/release-crashes.txt"; then
  echo 'Release startup produced a fatal runtime exception' >&2
  exit 1
fi

step 'Installing debug and instrumentation APKs'
adb install -r "$APK_DIR/app-debug.apk"
adb install -r "$APK_DIR/app-debug-androidTest.apk"

# Test-harness-only authorization on a disposable emulator. Application code never writes these settings.
step 'Enabling real AccessibilityService through test-harness ADB'
adb shell am force-stop "$DEBUG_PACKAGE"
adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
adb shell am start -W -n "$DEBUG_PACKAGE/com.shrey.androiddex.standalone.MainActivity" > "$REPORT_DIR/debug-startup.txt"
sleep 3
adb shell settings get secure enabled_accessibility_services | tee "$REPORT_DIR/accessibility-setting.txt"
grep -q "$SERVICE" "$REPORT_DIR/accessibility-setting.txt"
adb shell dumpsys accessibility > "$REPORT_DIR/accessibility-before-tests.txt"
adb logcat -d -s AndroidRuntime:E > "$REPORT_DIR/accessibility-crash.txt" || true
if grep -q 'Crashed services:{{.*DesktopAccessibilityService' "$REPORT_DIR/accessibility-before-tests.txt" || grep -Eq 'FATAL EXCEPTION|DesktopAccessibilityService' "$REPORT_DIR/accessibility-crash.txt"; then
  echo 'AccessibilityService crashed before instrumentation' >&2
  exit 1
fi

step 'Running desktop/Accessibility instrumentation'
set +e
adb shell am instrument -w -r -e notClass com.shrey.androiddex.standalone.ShizukuDeviceWorkflowTest "$DEBUG_PACKAGE.test/androidx.test.runner.AndroidJUnitRunner" | tee "$REPORT_DIR/instrumentation.txt"
INSTRUMENT_STATUS=${PIPESTATUS[0]}
set -e
adb exec-out screencap -p > "$REPORT_DIR/after-tests.png"
adb shell dumpsys accessibility > "$REPORT_DIR/accessibility-after-tests.txt" 2>/dev/null || true
adb logcat -d -s AndroidRuntime:E > "$REPORT_DIR/runtime-after-tests.txt" 2>/dev/null || true
adb logcat -d | grep -Ei 'DesktopAccessibilityService|AccessibilityService|AndroidRuntime|FATAL EXCEPTION|com.shrey.androiddex.standalone.debug' | tail -300 > "$REPORT_DIR/logcat-accessibility.txt" 2>/dev/null || true
# Best-effort teardown on the disposable emulator. `settings put ... ''` loses the empty
# argument over adb and exits non-zero, which previously aborted the run under `set -e`
# even when instrumentation passed; `settings delete` is the correct way to clear the grant.
adb shell settings delete secure enabled_accessibility_services >/dev/null 2>&1 || true
adb shell settings put secure accessibility_enabled 0 >/dev/null 2>&1 || true
if [[ "$INSTRUMENT_STATUS" -ne 0 ]] || ! grep -Eq '^OK \([0-9]+ tests?\)' "$REPORT_DIR/instrumentation.txt"; then
  echo 'Instrumentation tests failed or did not complete' >&2
  exit 1
fi

# Exercise the real official Shizuku dialog and UserService. ADB starts the service only
# on this disposable CI emulator; the app itself never starts Shizuku or grants permission.
step 'Installing official Shizuku'
adb install -r "$APK_DIR/shizuku.apk"
# Official Shizuku >=13 starter is the packaged native executable libshizuku.so, not the
# old start.sh. `dumpsys package` only prints legacyNativeLibraryDir (not nativeLibraryDir),
# and the code path contains '==' segments, so never split on '='. Resolve the real extracted
# starter from `pm path`: it lives at <codePath>/lib/<abi>/libshizuku.so on the device.
SHIZUKU_APK="$(adb shell pm path moe.shizuku.privileged.api | tr -d '\r' | sed -n 's/^package://p' | head -1 || true)"
SHIZUKU_SO=''
if [[ -n "$SHIZUKU_APK" ]]; then
  SHIZUKU_CODE_DIR="$(dirname "$SHIZUKU_APK")"
  SHIZUKU_SO="$(adb shell "ls '$SHIZUKU_CODE_DIR'/lib/*/libshizuku.so 2>/dev/null" | tr -d '\r' | head -1 || true)"
fi
if [[ -z "$SHIZUKU_SO" ]]; then
  echo 'Could not locate the official Shizuku native starter (libshizuku.so)' >&2
  adb shell dumpsys package moe.shizuku.privileged.api > "$REPORT_DIR/shizuku-package.txt" 2>/dev/null || true
  exit 1
fi
step "Starting Shizuku through official native starter: $SHIZUKU_SO"
# Bound the start so a misbehaving starter can never hang the CI job; capture its output.
timeout 60s adb shell "$SHIZUKU_SO" 2>&1 | tee "$REPORT_DIR/shizuku-start.txt" || true
sleep 3
adb shell ps -A > "$REPORT_DIR/processes.txt" 2>/dev/null || true

# Test-harness-only overlay grant on the disposable emulator. The app never writes app-ops.
adb shell appops set "$DEBUG_PACKAGE" SYSTEM_ALERT_WINDOW allow
adb shell pm grant "$DEBUG_PACKAGE" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

step 'Running official Shizuku authorization, native freeform and overlay-dock instrumentation'
set +e
adb shell am instrument -w -r -e class com.shrey.androiddex.standalone.ShizukuDeviceWorkflowTest,com.shrey.androiddex.standalone.NativeDesktopWorkflowTest "$DEBUG_PACKAGE.test/androidx.test.runner.AndroidJUnitRunner" | tee "$REPORT_DIR/shizuku-instrumentation.txt"
SHIZUKU_STATUS=${PIPESTATUS[0]}
set -e
adb exec-out screencap -p > "$REPORT_DIR/shizuku-authorized.png"
adb shell uiautomator dump /sdcard/shizuku-window.xml >/dev/null 2>&1 || true
adb shell cat /sdcard/shizuku-window.xml > "$REPORT_DIR/shizuku-window.xml" 2>/dev/null || true
adb shell dumpsys window windows > "$REPORT_DIR/overlay-windows.txt" 2>/dev/null || true
adb shell dumpsys activity activities > "$REPORT_DIR/native-tasks.txt" 2>/dev/null || true
if [[ "$SHIZUKU_STATUS" -ne 0 ]] || ! grep -Eq '^OK \([0-9]+ tests?\)' "$REPORT_DIR/shizuku-instrumentation.txt"; then
  echo 'Official Shizuku authorization/task-bridge test failed' >&2
  exit 1
fi

printf 'Release startup, desktop/Accessibility instrumentation, official Shizuku authorization/task bridge, native freeform window controls and the opt-in overlay dock passed on the API 35 emulator. Wireless pairing and physical peripherals remain device-only tests.\n' | tee "$REPORT_DIR/summary.txt"
step 'All emulator smoke checks passed'
