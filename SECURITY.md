# Security & Privacy Policy

## Scope

This policy describes the **standalone Android application** under `android/` in this branch. The historical PC edition described by the retained documents is a separate proprietary product whose source was not present in the starting checkout. The static website still uses its original external font/icon resources; the Android APK does not load that website.

## Standalone Android app

- **No INTERNET permission and no telemetry.** The app does not contact analytics, cloud storage, a remote desktop or a PC. Opening an install/documentation link hands off to the user's browser. Shizuku is a separate application and performs its own local wireless-debugging pairing.
- **Local app data.** Notes, pins, recent launch package names, window/icon layout and the selected wallpaper are stored in app-private storage. Android backup is disabled. This is not an additional encrypted-notes product; device encryption and lock-screen security remain important.
- **User-selected files only.** The Storage Access Framework folder picker and photo picker grant specific access. There is no broad storage permission. Folder access can be released in Settings or revoked in Android. Wallpaper import bounds memory use and re-encodes the image to remove source metadata.
- **Shizuku boundary.** The official client API is used. Authorization is queried live, never hardcoded or restored from preferences. The non-daemon shell UserService exposes a narrow AIDL interface, not a general shell UI. Setting keys/values are allowlisted and components validated. It runs only on demand and is unbound when the UI stops.
- **Accessibility boundary.** The user enables the service in Android Settings. It observes foreground package transitions and supports explicit navigation. It does not filter key events, record passwords/typed text, take screenshots or retain window text. The optional inspector excludes password/editable nodes and uses generic labels; control actions require an explicit user tap.
- **No silent security changes.** The app does not enable Accessibility, Wireless debugging, overlay access or Shizuku authorization itself. It does not request root, device admin, notification contents or draw-over-apps permission.
- **Battery/background restraint.** No persistent foreground service, boot receiver or reconnection polling loop. Optional Accessibility is event-driven. Visible-scoped system callbacks and task polling stop when their UI is disposed.

## Release safety

CI `TEST-RELEASE` APKs are signed with a disposable test key and are for testing, not production distribution. They are clearly labeled. Production releases require a protected, persistent signing key supplied outside Git. Verify the source and SHA-256 checksum of any APK before installing. Do not enter Android debugging pairing codes into DeX or an untrusted app; codes belong in the official Shizuku pairing flow.

## Original project / upstream

Original project and artwork: Copyright (c) 2026 Shrey Patel. The original proprietary license and notices are retained. The original official upstream distribution source is <https://github.com/Shrey113/Android-Dex>. This implementation branch is not an upstream proprietary desktop release and does not relicense the original project.

Third-party software retains its own licenses. See `NOTICE` and `android/THIRD_PARTY_NOTICES.md`.

## Reporting a vulnerability

Use this repository's private security advisory facility if available, or contact the repository maintainer. Include the APK version/build, Android version, device model, reproduction steps and relevant permission state. Do not publish pairing codes, keystores, passwords, private notes or other users' screen content.
