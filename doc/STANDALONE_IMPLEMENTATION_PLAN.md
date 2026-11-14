# Shrey's Android DeX — standalone implementation plan

## Audit of the starting checkout (14 November 2026)

Base commit: `7fefa550c7eaae365030d192618650270fdf9b82`.

The repository contains a static HTML/CSS/JavaScript distribution website, screenshots/video, GitHub Pages and issue workflows, IntelliJ Flutter run metadata, and architecture specifications. It contains **no Dart, Kotlin or Java application source, no AndroidManifest.xml, no Gradle project, no companion APK and no logic-engine JAR**. The documented proprietary product uses a Flutter desktop client, ADB, scrcpy, a shell JAR and a Kotlin companion. Its source is not present in this checkout. There is therefore no existing native implementation to port or preserve; the website, branding, media, license and historical specifications will be retained. Historical PC documentation must not be mistaken for implemented standalone features.

## Product and platform decisions

- Add a native Kotlin/Jetpack Compose application under `android/`; make it the primary project in the README without removing the legacy distribution site.
- Application ID `com.shrey.androiddex.standalone`; Android 11+ (API 30), compile/target API 35. Wireless pairing without a computer requires Android 11+. No root, PC, bundled ADB executable, remote desktop or network server is used by this app.
- Official `dev.rikka.shizuku:api` and `:provider` 13.1.5. Shizuku itself is a separately installed, user-authorized application. Pairing/start happens in Shizuku, not through a simulated permission dialog in this app.
- Observe binder received/dead and permission-result callbacks. Recheck authorization on resume and before privileged operations. Never infer authorization from installation or a previous launch.
- A non-daemon Shizuku UserService exposes a narrow AIDL task bridge. It is bound only while the UI is started and torn down on unbind/binder death. No polling shell, boot receiver, persistent foreground service or background reconnection loop.
- A genuine, optional Accessibility Service observes foreground-window transitions, provides explicit Back/Home/Recents/Lock actions, and can inspect non-editable, non-password controls on demand. It does not request key-event filtering or store text, passwords, view IDs or screen content. An optional accessibility navigation overlay does not need SYSTEM_ALERT_WINDOW.
- Real desktop windows host local Notes, a SAF file browser, Calculator, App Library, Settings and Device information. Drag/resize/minimize/maximize/close controls control these actual windows. Arbitrary third-party activities cannot be embedded by an ordinary Android app or by Shizuku alone.
- Launch third-party apps through Android. Offer experimental native freeform launches only when runtime feature/settings probes and the authorized task bridge support them. Those windows belong to Android, not to the app's compositor; do not offer fake embedded-window controls. Show real privileged tasks when available and an explicit Recents fallback otherwise.
- Preserve the dark graphite design and existing app artwork, with cyan/mint accents. Support touch, Android's native hardware mouse cursor, right-click menus, keyboard/trackpad input, responsive landscape layouts and runtime-detected public presentation displays.
- No INTERNET, broad storage, notification-listener, device-admin or root permission. A launcher intent query provides the launchable app list without QUERY_ALL_PACKAGES. SAF and the photo picker grant only user-selected files. Preferences, notes, recent launches and wallpaper stay local.

## Implementation modules

1. Gradle/manifest/resources, release signing configuration and dependency/ProGuard rules.
2. Preference/window models, geometry constraints, app repository, bounded icon cache, wallpaper import and calculator parser.
3. Shizuku lifecycle manager, permission state machine, AIDL task bridge, capability probes and explicit freeform-setting consent.
4. Accessibility lifecycle, privacy-preserving foreground state and user-controlled navigation overlay.
5. First-run setup; desktop workspace; start/search/pins/recents; taskbar/tray; real tool windows; settings/troubleshooting; keyboard shortcuts and peripheral/display detection.
6. JVM tests, Compose/instrumentation tests, Android CI build/lint/release artifact and emulator installation/startup smoke tests.
7. Installation/setup instructions, privacy description, platform limitations and a device acceptance checklist distinguishing automated coverage from physical Tab A9 tests.

## Verification boundaries

A successful build, emulator startup, absent-Shizuku behavior and test-harness Accessibility activation can be verified in CI. Actual wireless pairing, Shizuku's permission dialog, Samsung firmware freeform behavior, Bluetooth/USB peripherals and the physical SM-X110 require an on-device acceptance run. CI must not enable permissions inside application code or report those unperformed tests as passed. Release artifacts built with an ephemeral CI test key are explicitly test builds, not production-signed releases.
