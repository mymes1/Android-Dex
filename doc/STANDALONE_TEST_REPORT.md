# Standalone Android — verification and test report

This report separates **what automated CI actually executed and passed** from **what still
requires a physical Galaxy Tab A9 (SM-X110) acceptance run**. CI results are never presented as
physical-device results.

- Date: **14 November 2026**
- Branch: `arena/d76f6e08-android-dex`
- Verified commit: **`b2cabf5f92c573edf0ca27323646eebfeff50e2f`**
- Green workflow run: **[43567764912](https://github.com/mymes1/Android-Dex/actions/runs/43567764912)**
  (`build` ✓ 5m20s, `emulator` ✓ 1m50s, evidence check-run `132366968646` ✓)
- Corroborating green run: **[43566865908](https://github.com/mymes1/Android-Dex/actions/runs/43566865908)**
  on commit `2ca3c01` (first fully green end-to-end run, including official Shizuku authorization).

## 1. Verdict

The `Standalone Android build and tests` workflow is **green end to end** on the commit above:
debug/test/lint compilation, an optimized release-mode APK with verified signature, JVM unit
tests, release cold-start on an emulator, the full desktop/Accessibility instrumentation suite,
and the **official Shizuku authorization + shell task-bridge** instrumentation all passed.

The emulator is an **API 35 AOSP ATD `x86_64` image (Nexus 9 profile)** — it is **not** a physical
SM-X110 and does not run Samsung firmware. Wireless pairing, Samsung-specific dialogs, firmware
freeform behavior and real Bluetooth/USB peripherals were **not** exercised and remain manual
acceptance items in §6.

## 2. Automated coverage and results

| Stage | What runs | Result |
| --- | --- | --- |
| Compile + lint | `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --continue` (lint `abortOnError=true`) | **Pass** |
| JVM unit tests | `CoreLogicTest` — geometry clamping/resize, calculator precedence + rejection of code/overflow/zero-divisor/incomplete input, Shizuku authorization truth table, freeform capability truth table | **10 / 10 pass** |
| Release build | `assembleRelease` with minify+shrink, signed using a disposable CI PKCS12 test key, `apksigner verify`, `SHA256SUMS` | **Pass** (TEST-RELEASE artifact) |
| Release cold start | Install release APK on API 35 emulator, `am start -W`, confirm `Status: ok`, live pid, resumed activity, no fatal runtime exception, onboarding text in UI dump | **Pass** |
| Desktop / Accessibility instrumentation | `DesktopFlowTest` (6 tests, see §4) | **`OK (6 tests)`** |
| Real Accessibility binding | Test harness enables the genuine service over ADB; `dumpsys accessibility` shows it **Bound** with empty `Crashed services` before tests; instrumentation observes the bound state and the Settings UI | **Pass** |
| Official Shizuku authorization + task bridge | Install official Shizuku v13.6.0, start it via the packaged native starter, run `ShizukuDeviceWorkflowTest` | **`OK (1 test)`** |

### Quoted evidence (green run 43567764912)

Release cold start:

```text
Starting: Intent { cmp=com.shrey.androiddex.standalone/.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.shrey.androiddex.standalone/.MainActivity
TotalTime: 197
WaitTime: 202
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

Desktop / Accessibility instrumentation: `Time: 7.712` → `OK (6 tests)`.

Official Shizuku native starter (started by the harness over ADB, not by application code):

```text
info: starter begin
info: killing old process...
info: apk path is /data/app/~~aingSM7O2CtFh0Nubc5h7g==/moe.shizuku.privileged.api-Q9ldweICs_TbJ2WU0tQiQg==/base.apk
info: starting server...
info: shizuku_server pid is 2156
info: shizuku_starter exit with 0
```

Official Shizuku authorization / task-bridge instrumentation:
`test=officialAuthorizationDialogAndShellTaskBridge`, `INSTRUMENTATION_STATUS_CODE: 0`,
`Time: 5.277` → `OK (1 test)`.

Harness summary (`summary.txt`):

```text
Release startup, desktop/Accessibility instrumentation, and official Shizuku authorization/task
bridge passed on the API 35 emulator. Wireless pairing and physical peripherals remain
device-only tests.
```

## 3. What the Shizuku test actually proves

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

Authorization is queried live from Shizuku; it is never hardcoded, cached as granted, or inferred
from installation. The app itself never starts Shizuku and never writes Accessibility or Shizuku
grants — only the disposable-emulator harness does, over ADB.

## 4. Test inventory

**JVM (`android/app/src/test/.../CoreLogicTest.kt`) — 10:**
`geometryClampsToWorkspace`, `geometryHandlesTinyWorkspace`, `resizeCannotEscapeOrCollapse`,
`calculatorPrecedenceAndParentheses`, `calculatorRejectsZeroDivisor`, `calculatorRejectsCode`,
`calculatorRejectsOverflow`, `calculatorRejectsIncompleteInput`, `shizukuNeverFakesAuthorization`,
`freeformRequiresLiveShellAndActiveSupport`.

**Desktop instrumentation (`DesktopFlowTest.kt`) — 6:**
`firstRunCompletesAndDoesNotRepeat`, `notesAreRealPersistentWindows`,
`windowControlsAndTaskbarRestoreWork`, `startSearchAndKeyboardShortcutsWork`,
`absentShizukuIsNotReportedConnected`, `accessibilityActivationIsObservedWhenTestHarnessEnablesIt`.

**Shizuku instrumentation (`ShizukuDeviceWorkflowTest.kt`) — 1:**
`officialAuthorizationDialogAndShellTaskBridge` (run in a separate instrumentation pass after the
harness installs and starts official Shizuku).

## 5. Framework subtleties handled by the harness (not app bugs)

These were diagnosed against AOSP `android-15.0.0_r1` sources and are handled in the **test
harness only**; application code never writes system grants:

- **Accessibility under instrumentation.** `am instrument` restarts the target process, so the
  previously bound service binder dies and `AccessibilityManagerService` moves it to its *crashed*
  set and refuses to rebind until the grant is toggled off/on. A default `UiAutomation` connection
  also *suppresses* all real accessibility services. The test connects with
  `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` and toggles the ADB grant off→on, then polls for the
  real bound state with wall-clock waits (Compose idling does not advance binder callbacks).
- **Desktop key focus.** After the Start-menu search field is dismissed, root focus is re-requested
  so `Meta`/`Esc`/`Ctrl+W`/`Alt+Tab` reach the desktop key handler.
- **Shizuku starter path.** `dumpsys package` prints `legacyNativeLibraryDir`, not
  `nativeLibraryDir`, and code paths contain `==` segments; the harness resolves
  `<codePath>/lib/<abi>/libshizuku.so` from `pm path` instead of splitting on `=`.
- **Teardown.** `settings put secure enabled_accessibility_services ''` drops the empty value over
  ADB and exits non-zero; the harness uses `settings delete` and best-effort teardown so a passing
  run is not aborted.

The post-instrumentation `dumpsys accessibility` lists the service under `Crashed services` because
`am instrument` force-stops the target process when it finishes; this is expected teardown, not an
app crash (the pre-test dump shows it Bound with an empty crashed set, and `AndroidRuntime` logs are
empty).

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
- [ ] **Native freeform behavior:** verify the runtime capability probe and an experimental freeform
      launch against Samsung firmware (which may ignore or customize AOSP freeform).
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

CI artifacts on a green run:

- `Shreys-Android-DeX-test-release` — optimized release APK (disposable CI test key) + `SHA256SUMS`.
- `android-test-inputs` — debug, debug-androidTest and official Shizuku APKs used by the emulator job.
- `android-build-reports` — lint/test reports.
- `android-emulator-evidence` — `progress.txt`, startup/pid logs, UI dumps, screenshots,
  `accessibility-before-tests.txt`, `instrumentation.txt`, `shizuku-start.txt`,
  `shizuku-instrumentation.txt` and `summary.txt`.

Because artifact blob downloads are unreachable from API-only sandboxes, the workflow also publishes
an **`Android emulator diagnostics` check-run on every run** (its conclusion mirrors the job status).
Its `output.summary` embeds the same evidence text and is readable through the GitHub Checks API,
e.g. check-run `132366968646` for the green run above.
