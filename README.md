# Shrey's Android DeX — Standalone Android Edition

**The Android app is now the primary implementation in this repository.** It runs directly on an Android 11+ tablet: no computer, remote desktop, emulator, root, Samsung DeX firmware support or PC connection is needed for normal use.

> [!IMPORTANT]
> The starting checkout contained the distribution website, artwork and architecture specifications—not the proprietary Flutter/Kotlin source described by those documents. This branch adds a real native Kotlin/Jetpack Compose application under [`android/`](android/). The original website, media, license and historical PC documentation are retained. This is not a claim that the missing PC implementation was ported.

## Android app

- Native APK, dark graphite desktop, wallpaper selection, draggable shortcuts and right-click menus.
- Real draggable/resizable/minimizable/maximizable windows for Notes, Files, Calculator, App Library, Settings and Device information.
- Bottom taskbar, searchable Start menu, installed apps, pins, local recent launches, battery, connectivity, clock, volume and quick settings.
- Official Shizuku API 13.1.5 integration: installation/service detection, real authorization requests, denial/revocation handling, reconnect/troubleshooting UI and an on-demand shell task bridge.
- Optional, genuine Accessibility Service for foreground changes, Back/Home/Recents/Lock, a return/navigation overlay and a privacy-preserving control inspector.
- Touch, Android's hardware mouse cursor, Bluetooth/USB keyboards and mice, trackpads, keyboard shortcuts and runtime-detected Android displays.
- Local-only notes and preferences; user-selected folder access through Storage Access Framework; no INTERNET or broad storage permission.

### Important Android limits

Other Android apps are **launched and switched**, not illegally embedded inside this APK. Their fullscreen/split-screen/freeform behavior belongs to Android. Native freeform is offered only after live capability checks and Shizuku authorization, and firmware may still override it. The app does not show fake window controls for third-party activities.

The target **Galaxy Tab A9 Wi-Fi (SM-X110)** has USB 2.0 and no MHL video output; a passive USB-C HDMI adapter cannot add wired desktop video. See [device compatibility](doc/STANDALONE_COMPATIBILITY.md).

## Install and set up

1. Obtain the APK. The [Android CI workflow](https://github.com/mymes1/Android-Dex/actions/workflows/android.yml) publishes a clearly labeled **TEST-RELEASE** artifact after a successful build. It is an optimized release build signed with a disposable CI test key, suitable for fresh installation/testing—not a production-signed distribution or a stable upgrade key.
2. Open the APK on the tablet and allow installation from that source through Android's normal prompt.
3. Follow the in-app setup. For advanced features, install the official [Shizuku](https://github.com/RikkaApps/Shizuku/releases/latest), enable Wireless debugging, pair/start Shizuku, then request permission in DeX.
4. Optionally enable **DeX desktop navigation** in Android Accessibility settings.
5. Enter the desktop. Optional permissions can be skipped; setup does not reappear on every launch.

**After a reboot, Shizuku must be started again using Wireless debugging.** DeX detects this and keeps normal desktop tools available. Detailed Samsung setup and recovery instructions: [Setup guide](doc/STANDALONE_SETUP.md).

## Build from source

Requirements: JDK 17, Android SDK 35 and Build Tools 35.0.0. Gradle 8.14 is supplied by the wrapper; AGP 8.10.1 and Kotlin 2.1.21 are pinned.

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
```

Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

A local release is **unsigned** unless all four signing environment variables are supplied: `DEX_KEYSTORE`, `DEX_KEYSTORE_PASSWORD`, `DEX_KEY_ALIAS`, `DEX_KEY_PASSWORD`. Never commit a keystore. See [Android build and architecture](android/README.md).

A computer/ADB may be used once to build or sideload during development; it is never a runtime dependency of the application.

## Documentation

| Document | Purpose |
| --- | --- |
| [Android module](android/README.md) | Build, architecture, permissions, tests and signing |
| [Implementation audit/plan](doc/STANDALONE_IMPLEMENTATION_PLAN.md) | What was actually present and the implementation decisions |
| [Standalone setup](doc/STANDALONE_SETUP.md) | Wireless ADB, Shizuku, Accessibility and recovery |
| [Compatibility](doc/STANDALONE_COMPATIBILITY.md) | Real platform limits and SM-X110 hardware notes |
| [Verification](doc/STANDALONE_TEST_REPORT.md) | CI evidence and remaining physical-device acceptance tests |
| [Security/privacy](SECURITY.md) | Local data, permission boundaries and shell bridge |

## Legacy PC edition and original project materials

The original [website](index.html), [download page](download.html), [screenshots/video](Data/) and [historical architecture documents](doc/) remain available. Those documents describe the separate, closed-source Windows/Linux/macOS client using Flutter, ADB, scrcpy, a shell JAR and a companion APK. That source was not in this checkout; legacy gaming maps, per-app audio and PC mirroring are not claimed as implemented standalone features.

Original project by [Shrey113](https://github.com/Shrey113). The original proprietary [LICENSE](LICENSE), [NOTICE](NOTICE) and official-upstream distribution warning are retained. This fork's standalone implementation does not grant rights to relicense or commercially redistribute the original proprietary project. Third-party components retain their own licenses; see [android/THIRD_PARTY_NOTICES.md](android/THIRD_PARTY_NOTICES.md).

> [!WARNING]
> The original upstream repository is <https://github.com/Shrey113/Android-Dex>. Do not mistake this implementation branch for an upstream proprietary desktop release. Shizuku is a separate official RikkaApps application; never enter debugging codes into an app you do not trust.
