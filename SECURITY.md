# Security & Privacy Policy

## Scope

This policy describes the **standalone Android application** under `android/` in this branch. The historical PC edition described by the retained documents is a separate proprietary product whose source was not present in the starting checkout. The static website still uses its original external font/icon resources; the Android APK does not load that website.

## Standalone Android app

- **No INTERNET permission and no telemetry.** The app does not contact analytics, cloud storage, a remote desktop or a PC. Opening an install/documentation link hands off to the user's browser. Shizuku is a separate application and performs its own local wireless-debugging pairing.
- **Local app data.** Notes, pins, recent launch package names, window/icon layout and the selected wallpaper are stored in app-private storage. Android backup is disabled. This is not an additional encrypted-notes product; device encryption and lock-screen security remain important.
- **User-selected files only.** The Storage Access Framework folder picker and photo picker grant specific access. There is no broad storage permission. Folder access can be released in Settings or revoked in Android. Wallpaper import bounds memory use and re-encodes the image to remove source metadata.
- **Shizuku boundary.** The official client API is used. Authorization is queried live, never hardcoded or restored from preferences. The non-daemon shell UserService exposes a narrow AIDL interface, not a general shell UI. Setting keys/values are allowlisted, components and window bounds validated, and task tokens stay inside the shell service. It runs while the desktop or opt-in dock needs it and is unbound when the last client stops.
- **Overlay boundary.** `SYSTEM_ALERT_WINDOW` is requested only for the user-enabled persistent dock/window bars. The app checks the grant live, never writes app-ops, and stops the dock when the grant is revoked. Overlay windows draw controls only; they do not capture, record or re-render other apps' video. Gaps between dock islands pass touches through.
- **Optional media/notification boundary.** Notification listener access is user-granted in Android Settings and used only for active media title/artist/artwork/playback state and a notification count. Notification titles, bodies, messages and typed text are not read or stored.
- **Accessibility boundary.** The user enables the service in Android Settings. It observes foreground package transitions and supports explicit navigation. It does not filter key events, record passwords/typed text, take screenshots or retain window text. The optional inspector excludes password/editable nodes and uses generic labels; control actions require an explicit user tap.
- **No silent security changes.** The app does not enable Accessibility, Wireless debugging, overlay access, notification access or Shizuku authorization itself. It opens Android's own grant screens and does not request root or device admin.
- **Battery/background restraint.** No boot receiver or hidden reconnection daemon. The only foreground service is the explicit, notification-visible persistent dock; hiding the dock or revoking overlay access stops it. Optional Accessibility is event-driven. Desktop system callbacks stop when their UI is disposed; dock task polling pauses while the screen is off.

## Release safety

CI `TEST-RELEASE` APKs are signed with a disposable test key and are for testing, not production distribution. They are clearly labeled. Production releases require a protected, persistent signing key supplied outside Git. Verify the source and SHA-256 checksum of any APK before installing. Do not enter Android debugging pairing codes into DeX or an untrusted app; codes belong in the official Shizuku pairing flow.

## Original project / upstream

Original project and artwork: Copyright (c) 2026 Shrey Patel. The original proprietary license and notices are retained. The original official upstream distribution source is <https://github.com/Shrey113/Android-Dex>. This implementation branch is not an upstream proprietary desktop release and does not relicense the original project.

Third-party software retains its own licenses. See `NOTICE` and `android/THIRD_PARTY_NOTICES.md`.

## Reporting a vulnerability

Use this repository's private security advisory facility if available, or contact the repository maintainer. Include the APK version/build, Android version, device model, reproduction steps and relevant permission state. Do not publish pairing codes, keystores, passwords, private notes or other users' screen content.
