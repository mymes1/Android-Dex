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
  │         ├─ ActivityTaskManager task list/switch/remove/bounds via shell runtime
  │         ├─ WindowContainerTransaction resize/mode/hide for real freeform tasks
  │         ├─ allowlisted freeform development settings
  │         └─ validated am launches / Recents input
  ├─ DesktopDockService (opt-in SYSTEM_ALERT_WINDOW foreground service)
  │    ├─ separate pass-through dock islands: navigation, media, apps, tray
  │    ├─ dark caption windows over real freeform tasks (drag/resize/min/max/close)
  │    └─ app/task/tray panels; stops when hidden or permission revoked
  ├─ DesktopMediaService (optional notification listener; media metadata + count only)
  └─ DesktopAccessibilityService (user-enabled in Android Settings)
       ├─ foreground package transitions, no text/key retention
       ├─ explicit global navigation actions
       └─ optional TYPE_ACCESSIBILITY_OVERLAY return pill / Nav inspector
```

### Window boundary

`DesktopWindow` instances are real Compose windows inside this app. Their title bars drag, resize handles resize, and minimize/maximize/close change actual window state. Notes and SAF files are functional content, not screenshots.

Android does not provide a public API to render arbitrary other applications inside a normal app's view hierarchy. Shizuku does not change that security boundary. Third-party apps are rendered by Android. With an authorized shell bridge and active freeform support, DeX requests `am start --windowingMode 5` and real task bounds; Android owns the resulting window and may ignore the request. The opt-in overlay dock draws dark caption bars over those native tasks and sends validated `WindowContainerTransaction`/task commands for drag, resize, minimize, maximize/restore and close. It does not capture or re-render app video. If freeform or a window operation is refused, DeX reports that and falls back to normal Android launching/Recents. No fake embedded-app video is shown.

### Shizuku lifecycle

- Official provider receives the binder; sticky received/dead/permission listeners update a live state model.
- Installation, binder liveness, API version, UID and current permission are distinct states.
- Authorization is rechecked on resume and before every bridge operation. Denial/revocation is never cached as success.
- The non-daemon UserService is bound while the desktop Activity or opt-in dock is active and unbound/destroyed when the last client stops. Binder death clears it. Reconnection is callback/manual-resume driven, not a hidden retry loop.
- The bridge exposes no arbitrary shell endpoint. Settings keys/values are allowlisted; components and pixel bounds are validated; command execution has a timeout; task tokens remain in the shell service; task IPC contains no screenshots or user text.
- Hidden task APIs are reflected **inside the shell UserService**, not by bypassing hidden-API restrictions in the app process. Incompatible firmware produces an explicit fallback message.

### Accessibility and privacy

The service is declared with `BIND_ACCESSIBILITY_SERVICE` and can only be enabled by the user in Android Settings. It requests interactive-window retrieval, not key filtering or gesture injection. Window events retain package names only. The optional inspector traverses a bounded number of visible, enabled, non-password, non-editable controls and uses generic class/index labels; it never displays their text or view IDs. Actions occur only when the user taps a control.

The optional floating return pill uses an accessibility overlay. The separate persistent dock/window bars use `SYSTEM_ALERT_WINDOW`, are off by default, and require the user to enable both the DeX setting and Android's Display over other apps grant. The app watches the app-op and stops the dock if the grant is revoked. Returning to DeX uses a normal Activity launch, with an authorized Shizuku launch fallback if Android blocks the background start.

Optional notification listener access powers the media island and badge. It reads active media metadata/playback state and counts non-ongoing notifications; it does not read or store notification titles/bodies/messages. The app has no INTERNET permission. Notes, pins, recent launches, layout and the selected wallpaper are local app-private data. Backup is disabled. SAF grants are user-selected and persist only until released/revoked. Wallpaper import bounds decode size and re-encodes to strip source metadata. External documentation/install/search links open the user's browser; Shizuku's own wireless pairing is handled by the separate Shizuku app.

### Battery/background behavior

No wake lock, boot receiver or hidden polling daemon. The Accessibility Service is event-driven when enabled. The shell service is on demand. The only foreground service is the explicit persistent dock, with an ongoing Hide dock notification; it stops when disabled or overlay access is revoked. Dock task polling runs only while the screen is on and Shizuku is connected; the in-app Android Tasks window polls every 15 seconds. Battery/network/input callbacks and the minute clock are registered by visible compositions and removed on disposal. Keep-screen-awake is an explicit setting and applies only while the Activity is visible.

## Permissions

| Permission/integration | Required? | Purpose |
| --- | --- | --- |
| Shizuku authorization | Optional for local tools; required for native app windows | Shell task/window operations and capability probes |
| Accessibility | Optional | Foreground changes, navigation, return pill, explicit control actions |
| `SYSTEM_ALERT_WINDOW` + special-use foreground service | Opt-in | Persistent dock and dark caption bars over real Android windows; no video capture |
| `POST_NOTIFICATIONS` | Runtime, optional | Show the ongoing Hide dock notice while the dock runs |
| Notification listener access | Opt-in | Media metadata/playback controls and notification count only; no notification bodies |
| `ACCESS_NETWORK_STATE` | Normal | Honest connectivity indicator |
| `REORDER_TASKS` | Normal | Android task compatibility; privileged switches run in shell |
| SAF folder/photo picker | Per selection | Selected files/wallpaper only |
| INTERNET/storage/root | Not requested | Search/install links hand off to the browser; no broad storage or root |

## Tests

JVM tests cover geometry, native pixel-bounds parsing/move/resize/cascade, calculator parsing and permission/capability truth tables. Compose instrumentation covers first-run completion, persistence, real notes, window controls/taskbar, Start/search/keyboard shortcuts, absent-Shizuku behavior and real Accessibility binding. A separate instrumented workflow exercises official Shizuku authorization plus real freeform launch, overlay caption drag, maximize/restore, minimize/restore and close on an AOSP Settings task.

The `Standalone Android build and tests` workflow builds debug/test/lint, builds and verifies a release-mode APK, installs that APK on an API 35 emulator, checks startup, then runs instrumentation. Emulator screenshots/logs are uploaded as evidence. The test harness—not application code—enables Accessibility and grants the overlay app-op/POST_NOTIFICATIONS only on that disposable emulator.

Physical SM-X110 wireless pairing, Samsung permission dialogs, firmware freeform behavior and real Bluetooth/USB peripherals still require the checklist in [../doc/STANDALONE_TEST_REPORT.md](../doc/STANDALONE_TEST_REPORT.md). CI is not represented as a physical-device test.
