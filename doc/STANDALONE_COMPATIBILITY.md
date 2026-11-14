# Standalone Android compatibility and limits

Target: Samsung Galaxy Tab A9 Wi-Fi SM-X110, Android 15. The app supports Android 11+ because that is the first Android release with the on-device Wireless debugging pairing flow used by Shizuku. It does not require Samsung DeX support.

## Hardware evidence

Samsung's SM-X110 specifications list an 8.7-inch 1340 × 800 display, **USB 2.0**, **MHL: No**, Bluetooth 5.3 and HID/HOGP profiles [1](https://www.samsung.com/levant/tablets/galaxy-tab-a/galaxy-tab-a9-wifi-mystic-navy-128gb-sm-x110ndbemea/). Therefore:

- A passive USB-C-to-HDMI/DisplayPort dongle cannot provide wired external desktop video on this model.
- Bluetooth/USB HID keyboards, mice and trackpads can work through Android when paired/connected normally; OS layout and pointer settings still apply.
- The app uses density-independent layouts and Android's native hardware pointer rather than drawing a second fake cursor.
- Do not confuse SM-X110 (Tab A9) with SM-X210 (Tab A9+) or Tab S models with different display hardware.

## Feature matrix

| Feature | Implementation / boundary |
| --- | --- |
| Standalone APK | Real native Activity; no PC or remote session |
| Local desktop windows | Real Notes/Files/Calculator/App Library/Settings/Device windows; drag, resize, minimize, maximize, close, focus |
| Arbitrary apps inside those windows | **Not supported by Android's public app sandbox.** Apps launch in Android instead; no screenshots pretending to be interactive windows |
| Native freeform | Runtime feature flag + authorized shell settings/task API probe; explicit experimental launch; firmware/app can override |
| Force-resizable activities | Optional, explicit development-setting consent; can break app layouts; reversible |
| Split-screen / Recents | Real Android system features; Accessibility or authorized shell can open Recents |
| Running apps | Real shell task list when compatible; otherwise explicit fallback. Recent launches are labeled as history |
| Close other Android apps | Explicit task-remove confirmation through authorized shell; not a fake Ctrl+W force-stop |
| Shizuku after reboot | Must be started again through Wireless debugging; no root/auto-start bypass |
| Accessibility | Genuine user-enabled service; foreground package changes and explicit navigation; process death is not inferred from window changes |
| Accessibility text/input | Not collected; no key filtering; inspector excludes password/editable nodes and does not read labels/text |
| Return overlay | Optional accessibility overlay; no SYSTEM_ALERT_WINDOW permission |
| Power/restart | Android power dialog via Accessibility; restart only if firmware offers it |
| Wi-Fi/Bluetooth toggles | Open Android panels/settings; no silent privileged connectivity toggle |
| External displays | Only Android-exposed public presentation displays; own desktop Activity can be launched there; SM-X110 wired video unavailable |
| Resolution/density changes | Not performed; actual metrics reported and Android Display settings linked |
| Keyboard shortcuts | Implemented in desktop; Android/system may reserve Alt+Tab or Super before app dispatch |
| Legacy PC gaming/audio/mirroring | Original source absent from checkout; not ported or advertised as working in this APK |

## Why Shizuku is not a compositor

Shizuku executes authorized code with the shell UID. It does not grant an app permission to bypass Activity visibility, embed another package's UI, or become the system window manager. This implementation uses a narrow UserService for task queries/switches and validated launches. It does not use root, disable SELinux, patch SystemUI, or inject arbitrary touches into other apps.

Freeform support is heterogeneous: a feature flag can be absent while a development override exists; a global override can be present while an individual activity refuses resizing; Samsung can ignore or customize AOSP behavior. The UI therefore reports probe results and treats every native freeform launch as a request, not a guarantee.

## Runtime detection

The app reads `PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT`, `FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS`, actual Display objects/metrics, input-device sources, network capabilities and battery state. After authorization it reads `enable_freeform_support` and `force_resizable_activities` and attempts the shell task API. Unknown/failed probes remain unknown/unavailable; they are never converted into successful permission or support states.
