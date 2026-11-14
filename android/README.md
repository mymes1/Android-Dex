# Shrey's Android DeX — native Android module

Kotlin + Jetpack Compose, application ID `com.shrey.androiddex.standalone`, min API 30, target/compile API 35. No Flutter runtime, scrcpy, ADB executable, network server, root binary or PC dependency is bundled.

## Build

Use JDK 17 and Android SDK Platform 35 / Build Tools 35.0.0. Android Studio can open this `android/` directory directly.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
```

The debug build uses application ID suffix `.debug`, so it can coexist with the release package. Release enables R8 and resource shrinking. It is unsigned unless these environment variables are all set:

- `DEX_KEYSTORE`: absolute path to a PKCS12/JKS keystore outside Git
- `DEX_KEYSTORE_PASSWORD`
- `DEX_KEY_ALIAS`
- `DEX_KEY_PASSWORD`

Example local test key (keep it and its passwords private; do not commit them):

```sh
keytool -genkeypair -keystore ../dex-local-test.p12 -storetype PKCS12 \
  -alias dex-local -keyalg RSA -keysize 2048 -validity 365
```

CI creates a **new disposable key per run**, builds the actual release variant, verifies its signature and uploads `Shreys-Android-DeX-1.0.0-TEST-RELEASE.apk`. That artifact is for testing/fresh installs. Different CI runs have different signatures, so uninstall the old test build before installing another run's artifact if Android reports a signature mismatch. Use one protected, persistent production keystore for real releases.

## Architecture

```text
MainActivity / Compose desktop
  ├─ DesktopViewModel + PreferencesRepository
  │    ├─ real local tool windows, notes, pins, launch history, wallpaper
  │    ├─ AppRepository (launcher query + bounded icon cache)
  │    └─ SystemMonitor (visible-scoped battery/network/input/clock callbacks)
  ├─ ShizukuManager (official API/provider 13.1.5)
  │    └─ IDesktopTaskBridge AIDL → PrivilegedTaskService (UID 2000)
  │         ├─ ActivityTaskManager task list/switch/remove via shell runtime
  │         ├─ allowlisted freeform development settings
  │         └─ validated am launches / Recents input
  └─ DesktopAccessibilityService (user-enabled in Android Settings)
       ├─ foreground package transitions, no text/key retention
       ├─ explicit global navigation actions
       └─ optional TYPE_ACCESSIBILITY_OVERLAY return pill / Nav inspector
```

### Window boundary

`DesktopWindow` instances are real Compose windows inside this app. Their title bars drag, resize handles resize, and minimize/maximize/close change actual window state. Notes and SAF files are functional content, not screenshots.

Android does not provide a public API to render arbitrary other applications inside a normal app's view hierarchy. Shizuku does not change that security boundary. Third-party apps therefore launch through Android. With an authorized shell bridge and active freeform support, a context menu can request `am start --windowingMode 5`; Android owns the resulting window and may ignore the request. The task list/switcher uses the shell task API when compatible, otherwise explicitly falls back to Android Recents. No fake embedded-app controls are shown.

### Shizuku lifecycle

- Official provider receives the binder; sticky received/dead/permission listeners update a live state model.
- Installation, binder liveness, API version, UID and current permission are distinct states.
- Authorization is rechecked on resume and before every bridge operation. Denial/revocation is never cached as success.
- The non-daemon UserService is bound while an Activity is started and unbound/destroyed when the last one stops. Binder death clears it. Reconnection is callback/manual-resume driven, not a background retry loop.
- The bridge exposes no arbitrary shell endpoint. Settings keys and values are allowlisted; components are validated; command execution has a timeout; task IPC contains no screenshots or user text.
- Hidden task APIs are reflected **inside the shell UserService**, not by bypassing hidden-API restrictions in the app process. Incompatible firmware produces an explicit fallback message.

### Accessibility and privacy

The service is declared with `BIND_ACCESSIBILITY_SERVICE` and can only be enabled by the user in Android Settings. It requests interactive-window retrieval, not key filtering or gesture injection. Window events retain package names only. The optional inspector traverses a bounded number of visible, enabled, non-password, non-editable controls and uses generic class/index labels; it never displays their text or view IDs. Actions occur only when the user taps a control.

The optional floating return pill uses an accessibility overlay. `SYSTEM_ALERT_WINDOW` is not requested. Returning to DeX uses a normal Activity launch, with an authorized Shizuku launch fallback if Android blocks the background start.

The app has no INTERNET permission. Notes, pins, recent launches, layout and the selected wallpaper are local app-private data. Backup is disabled. SAF grants are user-selected and persist only until released/revoked. Wallpaper import bounds decode size and re-encodes to strip source metadata. External documentation/install links open the user's browser; Shizuku's own wireless pairing is handled by the separate Shizuku app.

### Battery/background behavior

No always-on foreground service, wake lock, boot receiver or polling daemon. The Accessibility Service is event-driven when enabled. The shell service is on demand. Task polling runs only while the Android Tasks window is visible (15 seconds). Battery/network/input callbacks and the minute clock are registered by the visible desktop composition and removed on disposal. Keep-screen-awake is an explicit setting and applies only while the Activity is visible.

## Permissions

| Permission/integration | Required? | Purpose |
| --- | --- | --- |
| Shizuku authorization | Optional | Shell task operations and capability probes |
| Accessibility | Optional | Foreground changes, navigation, return pill, explicit control actions |
| `ACCESS_NETWORK_STATE` | Normal | Honest connectivity indicator |
| `REORDER_TASKS` | Normal | Android task compatibility; privileged switches run in shell |
| SAF folder/photo picker | Per selection | Selected files/wallpaper only |
| Overlay | Not requested | Accessibility has its own overlay type |
| INTERNET/storage/notification content/root | Not requested | Not needed by this implementation |

## Tests

JVM tests cover geometry, calculator parsing and permission/capability truth tables. Compose instrumentation covers first-run completion, persistence, real notes, window controls/taskbar, Start/search/keyboard shortcuts, absent-Shizuku behavior and real Accessibility binding when the disposable emulator is authorized by the test harness.

The `Standalone Android build and tests` workflow builds debug/test/lint, builds and verifies a release-mode APK, installs that APK on an API 35 emulator, checks startup, then runs instrumentation. Emulator screenshots/logs are uploaded as evidence. The test harness—not application code—enables Accessibility through ADB on that disposable emulator.

Physical SM-X110 wireless pairing, Samsung permission dialogs, firmware freeform behavior and real Bluetooth/USB peripherals still require the checklist in [../doc/STANDALONE_TEST_REPORT.md](../doc/STANDALONE_TEST_REPORT.md). CI is not represented as a physical-device test.
