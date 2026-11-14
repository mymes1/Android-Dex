# Standalone Android — verification and test report

This report separates **what automated CI actually executed and passed** from **what still
requires a physical Galaxy Tab A9 (SM-X110) acceptance run**. CI results are never presented as
physical-device results.

- Date: **14 November 2026**
- Branch: `arena/d76f6e08-android-dex`
- Verified code commit: **`a587341f237e2b677d70e7a0183f510c58aedf1b`**
- Green workflow run: **[43593016472](https://github.com/mymes1/Android-Dex/actions/runs/43593016472)**
  (`build` ✓ 6m11s, `emulator` ✓ 2m3s, evidence check-run `132443600275` ✓)
- Installable test artifact: **[Shreys-Android-DeX-test-release](https://github.com/mymes1/Android-Dex/actions/runs/43593016472/artifacts/14113087783)**
  (disposable CI test key; GitHub artifact retention applies).

## 1. Verdict

The `Standalone Android build and tests` workflow is **green end to end** on the commit above:
debug/test/lint compilation, an optimized release-mode APK with verified signature, JVM unit
tests, release cold-start on an emulator, the full desktop/Accessibility instrumentation suite,
the **official Shizuku authorization + shell task-bridge** instrumentation, and the **native
freeform window + opt-in overlay dock** instrumentation all passed. The native pass was **not
skipped**.

The emulator is an **API 35 AOSP ATD `x86_64` image (Nexus 9 profile)** — it is **not** a physical
SM-X110 and does not run Samsung firmware. Wireless pairing, Samsung-specific dialogs, firmware
freeform behavior and real Bluetooth/USB peripherals were **not** exercised and remain manual
acceptance items in §6.

## 2. Automated coverage and results

| Stage | What runs | Result |
| --- | --- | --- |
| Compile + lint | `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --continue` (lint `abortOnError=true`) | **Pass** |
| JVM unit tests | `CoreLogicTest` — geometry, native pixel bounds, calculator, Shizuku/freeform truth tables | **14 / 14 pass** |
| Release build | `assembleRelease` with minify+shrink, signed using a disposable CI PKCS12 test key, `apksigner verify`, `SHA256SUMS` | **Pass** (TEST-RELEASE artifact) |
| Release cold start | Install release APK on API 35 emulator, `am start -W`, confirm `Status: ok`, live pid, no fatal runtime exception | **Pass** (`TotalTime: 245`) |
| Desktop / Accessibility instrumentation | `DesktopFlowTest` (6 tests, see §4) | **`OK (6 tests)`**, `Time: 8.186` |
| Real Accessibility binding | Harness enables the genuine service over ADB; `dumpsys accessibility` shows it **Bound** with empty `Crashed services` before tests | **Pass** |
| Official Shizuku authorization + task bridge | Install official Shizuku v13.6.0, start it via the packaged native starter, run `ShizukuDeviceWorkflowTest` | **`OK (1 test)`**, `Time: 5.448` |
| Native freeform + overlay dock | `NativeDesktopWorkflowTest` against a real test-only Android task: freeform launch/bounds, overlay caption focus/drag/maximize/restore/minimize/restore/close | **`OK (1 test)`**, `Time: 7.244` |

### Quoted evidence (green run 43593016472)

Release cold start:

```text
Starting: Intent { cmp=com.shrey.androiddex.standalone/.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.shrey.androiddex.standalone/.MainActivity
TotalTime: 245
WaitTime: 247
Complete
```

Accessibility service bound before instrumentation (`dumpsys accessibility`):

```text
Bound services:{Service[label=DeX desktop navigation, feedbackType[FEEDBACK_GENERIC],
  capabilities=1, eventTypes=[TYPE_WINDOW_STATE_CHANGED, TYPE_WINDOWS_CHANGED],
  notificationTimeout=200, requestA11yBtn=false]}
Enabled services:{{com.shrey.androiddex.standalone.debug/com.shrey.androiddex.standalone.system.DesktopAccessibilityService}}
Crashed services:{}
```

Desktop / Accessibility instrumentation: `Time: 8.186` → `OK (6 tests)`.

Official Shizuku native starter (started by the harness over ADB, not by application code):

```text
info: starter begin
info: killing old process...
info: apk path is /data/app/~~qyM2_vO3p8ZNbaASo84A7w==/moe.shizuku.privileged.api-dFGuOHHzysXap8PsJE8HWA==/base.apk
info: starting server...
info: shizuku_server pid is 2177
info: shizuku_starter exit with 0
```

Official Shizuku authorization / task-bridge instrumentation:
`test=officialAuthorizationDialogAndShellTaskBridge`, `INSTRUMENTATION_STATUS_CODE: 0`,
`Time: 5.448` → `OK (1 test)`.

Native freeform / overlay-dock instrumentation:
`test=nativeFreeformWindowAndOverlayDockControlRealTask`, `INSTRUMENTATION_STATUS_CODE: 0`,
`Time: 7.244` → `OK (1 test)`. `native-logcat.txt` contains no `DesktopDockService:E` or
`AndroidRuntime:E` entries.

Harness summary (`summary.txt`):

```text
Release startup, desktop/Accessibility instrumentation, official Shizuku authorization/task bridge,
native freeform window controls and the opt-in overlay dock passed on the API 35 emulator. Wireless
pairing and physical peripherals remain device-only tests.
```

## 3. What the privileged tests actually prove

`ShizukuDeviceWorkflowTest.officialAuthorizationDialogAndShellTaskBridge` is **not** a mock. On the
disposable emulator it:

1. Assumes the official `moe.shizuku.privileged.api` package is installed (the harness installs it).
2. Completes first-run setup and opens Settings.
3. Waits for the **real Shizuku binder** to be detected (`binderAlive`).
4. Requests permission through the app, which launches **Shizuku's own `RequestPermissionActivity`**;
   the test clicks the official dialog's Allow button (`android:id/button1`) with UiAutomator.
5. Asserts `authorized` **and** that the non-daemon shell **UserService** (`IDesktopTaskBridge`) binds.
6. Calls `tasks(0)` through the authorized shell bridge and asserts a **non-empty real Android task list**.
7. Exercises the capability probe and re-checks authorization.

`NativeDesktopWorkflowTest.nativeFreeformWindowAndOverlayDockControlRealTask` then proves the
redesigned native-window path:

1. Enables the experimental freeform setting through the authorized shell bridge and enables the
   opt-in persistent dock (the harness grants `SYSTEM_ALERT_WINDOW` only on the disposable emulator).
2. Finds the real dock controls through **all accessibility windows**, not only UiAutomator's active
   window dump (`TYPE_APPLICATION_OVERLAY` islands can be absent from that dump).
3. Launches `NativeTestTargetActivity`, a tiny exported resizeable Activity declared by the
   androidTest APK, with `am start -W --windowingMode 5 --display 0`, and asserts the shell reports a
   **real freeform task** (`windowingMode == 5`) with non-empty bounds.
4. Touches the app content below the caption to prove the overlay does not steal the whole window.
5. Clicks the overlay caption's maximize/restore controls and asserts the real task mode changes
   `5 → 1 → 5`.
6. Drags the caption and asserts the **real Android task bounds move**.
7. Minimizes through the caption, restores through the dock's Tasks panel, then closes through the
   caption and asserts the real task is removed.

Authorization is queried live from Shizuku; it is never hardcoded, cached as granted, or inferred
from installation. The app itself never starts Shizuku and never writes Accessibility, Shizuku or
app-op grants — only the disposable-emulator harness does, over ADB.

## 4. Test inventory

**JVM (`android/app/src/test/.../CoreLogicTest.kt`) — 14:**
`geometryClampsToWorkspace`, `geometryHandlesTinyWorkspace`, `resizeCannotEscapeOrCollapse`,
`calculatorPrecedenceAndParentheses`, `calculatorRejectsZeroDivisor`, `calculatorRejectsCode`,
`calculatorRejectsOverflow`, `calculatorRejectsIncompleteInput`, `shizukuNeverFakesAuthorization`,
`freeformRequiresLiveShellAndActiveSupport`, `nativeBoundsParsingRejectsEmptyOrShortArrays`,
`nativeWindowMoveCannotLeaveDisplay`, `nativeWindowResizeHonorsMinimumAndDisplayEdge`,
`nativeCascadeLeavesRoomForDock`.

**Desktop instrumentation (`DesktopFlowTest.kt`) — 6:**
`firstRunCompletesAndDoesNotRepeat`, `notesAreRealPersistentWindows`,
`windowControlsAndTaskbarRestoreWork`, `startSearchAndKeyboardShortcutsWork`,
`absentShizukuIsNotReportedConnected`, `accessibilityActivationIsObservedWhenTestHarnessEnablesIt`.

**Shizuku instrumentation (`ShizukuDeviceWorkflowTest.kt`) — 1:**
`officialAuthorizationDialogAndShellTaskBridge` (separate pass after the harness installs/starts
official Shizuku).

**Native freeform / overlay instrumentation (`NativeDesktopWorkflowTest.kt`) — 1:**
`nativeFreeformWindowAndOverlayDockControlRealTask` (separate pass after Shizuku authorization;
skipped assumptions are treated as failures by the harness).

## 5. Framework subtleties handled during implementation and testing

These were diagnosed against AOSP `android15-release` sources. Application code never writes system
grants; harness-only workarounds stay in `.github/scripts/android-smoke.sh`.

- **Accessibility under instrumentation.** `am instrument` restarts the target process, so the
  previously bound service binder dies and `AccessibilityManagerService` moves it to its *crashed*
  set until the grant is toggled. The harness explicitly writes the enabled service, polls for the
  real bound state, and tests use wall-clock waits for binder callbacks.
- **Overlay controls and UiAutomator.** `TYPE_APPLICATION_OVERLAY` dock/caption windows are not
  reliably returned by `uiautomator dump`/active-window queries. The native test searches every
  `UiAutomation.windows` root for the real content descriptions and clicks their screen bounds.
- **Minimal ATD images.** AOSP ATD does not guarantee a launchable `com.android.settings`; the native
  test therefore uses its own `NativeTestTargetActivity` instead of assuming AOSP Settings exists.
  The harness fails on `AssumptionViolatedException` / `STATUS_CODE: -4` in the native pass.
- **Freeform mode read.** `WindowConfiguration` stores the mode in private `mWindowingMode`; the
  shell bridge reads public `getWindowingMode()` (with a field-read fallback) instead of a
  non-existent public `windowingMode` field.
- **Caption dragging.** The caption is a separate WindowManager window that moves under the pointer.
  Gestures anchor to raw screen coordinates and a fixed origin, and task polling pauses during the
  gesture so preview bounds are not snapped back to stale shell bounds.
- **Shizuku starter path.** `dumpsys package` prints `legacyNativeLibraryDir`, not
  `nativeLibraryDir`, and code paths contain `==` segments; the harness resolves
  `<codePath>/lib/<abi>/libshizuku.so` from `pm path` instead of splitting on `=`.
- **Teardown.** `settings put secure enabled_accessibility_services ''` drops the empty value over
  ADB; the harness uses `settings delete` and best-effort teardown so a passing run is not aborted.

The post-instrumentation `dumpsys accessibility` may list the service under `Crashed services`
because `am instrument` force-stops the target process when it finishes; this is expected teardown,
not an app crash (the pre-test dump shows it Bound with an empty crashed set, and `AndroidRuntime`
logs are empty).

## 6. Physical-device acceptance checklist (NOT performed by CI)

The following require an on-device run on the **Galaxy Tab A9 Wi-Fi SM-X110 / Android 15** and are
explicitly **not** claimed as passed:

- [ ] **Wireless pairing without a computer:** enable Developer options → Wireless debugging, pair
      Shizuku with the 6-digit code, start Shizuku, and confirm DeX detects the live binder.
- [ ] **Reboot recovery:** after a reboot, Shizuku is stopped; restart it via Wireless debugging and
      confirm DeX keeps normal desktop tools available and re-detects the service.
- [ ] **Samsung permission dialogs:** confirm the official Shizuku authorization prompt and any
      Samsung-specific Accessibility/install prompts behave correctly on One UI.
- [ ] **Accessibility on firmware:** enable *DeX desktop navigation* in Android Accessibility
      settings and verify Back/Home/Recents/Lock, the return pill and the control inspector.
- [ ] **Native freeform behavior:** verify the runtime capability probe and experimental freeform
      launch/window controls against Samsung firmware (which may ignore or customize AOSP freeform).
- [ ] **Real peripherals:** pair/connect Bluetooth and USB keyboards, mice and trackpads; verify
      pointer, right-click, trackpad scrolling and keyboard shortcuts through Android.
- [ ] **External display:** confirm behavior with Android-exposed presentation displays. Note the
      SM-X110 has USB 2.0 and **no MHL video output**, so a passive USB-C HDMI dongle cannot add
      wired desktop video (see [Compatibility](STANDALONE_COMPATIBILITY.md)).
- [ ] **Third-party app launch/switch:** verify real apps launch in Android and the task switcher /
      authorized shell task tools behave as documented (no fake embedded windows).

## 7. Known non-fatal warnings

- Deprecated non-`AutoMirrored` Material icons (compile warnings only).
- `libandroidx.graphics.path.so` reported unstripped by the packaging step.
- GitHub Actions Node.js 20 deprecation annotations on `checkout@v4`, `upload-artifact@v4`,
  `download-artifact@v4`, `github-script@v7`, `gradle/actions/*@v4` (forced to Node 24; cosmetic).

None of these fail the build or tests.

## 8. Reproducing and locating evidence

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebugAndroidTest :app:assembleRelease
```

CI artifacts on the green run:

- `Shreys-Android-DeX-test-release` — optimized release APK signed with a disposable CI test key.
- `android-test-inputs` — debug, debug-androidTest, official Shizuku APKs and `SHA256SUMS`.
- `android-build-reports` — lint/test reports.
- `android-emulator-evidence` — `progress.txt`, startup/pid logs, UI dumps, screenshots,
  `accessibility-before-tests.txt`, `instrumentation.txt`, `shizuku-start.txt`,
  `shizuku-instrumentation.txt`, `native-instrumentation.txt`, `native-tasks.txt`,
  `overlay-windows.txt`, `native-logcat.txt` and `summary.txt`.

Because artifact blob downloads can be unreachable from API-only sandboxes, the workflow also
publishes an **`Android emulator diagnostics` check-run on every run** (its conclusion mirrors the
job status). Its `output.summary` embeds the same evidence text and is readable through the GitHub
Checks API, e.g. check-run `132443600275` for the green run above.
