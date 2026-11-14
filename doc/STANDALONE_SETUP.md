# Standalone setup — Galaxy Tab A9 / Android 15

## 1. Install and first launch

Install the APK using Android's normal package installer. CI artifacts labeled `TEST-RELEASE` are test-signed; use a consistently signed build for upgrades. Open **Shrey's Android DeX**. The first screen explains the app and reads the actual Android version/device. Shizuku and Accessibility are optional: **Skip for now** still gives you the local desktop, app launcher and normal Android app switching.

## 2. Enable Wireless debugging

1. Connect the tablet to a trusted Wi-Fi network. Android's Wireless debugging setup generally requires Wi-Fi; the resulting desktop does not require a PC or an internet service.
2. Open **Settings → About tablet → Software information** and tap **Build number** seven times. Enter your tablet PIN if Android asks.
3. Open **Settings → Developer options** and enable **Wireless debugging**. Approve Android's network prompt.
4. Install the official Shizuku app from its official GitHub release page or a trusted official store listing. DeX's install button opens the official GitHub release page; it does not silently install another APK.

## 3. Pair and start Shizuku without a computer

1. Open Shizuku. Under **Start via Wireless debugging**, tap **Pairing**.
2. Keep Shizuku accessible in split-screen/pop-up view. On Samsung, Recents → the app icon → split screen/pop-up view can help. Android's pairing-code screen must remain open while you enter the code.
3. In **Developer options → Wireless debugging**, tap **Pair device with pairing code**.
4. Android displays a six-digit code and a pairing address/port. Enter that code in **Shizuku's pairing notification**. DeX does not generate or ask for this code.
5. After pairing, return to Shizuku. Tap **Start** if the service has not started. The pairing port and the service connection port are different; Shizuku handles discovery.
6. Return to DeX and tap **Refresh**, then **Request permission**. Approve the actual Shizuku authorization dialog.

DeX shows **Connected · authorized** only when the official API reports a live binder and granted permission. The task bridge has its own live status. Installation alone is never treated as authorization.

The upstream Shizuku manual documents on-device wireless startup and the need to repeat startup after a reboot: [2](https://github.com/RikkaApps/websites/pull/9136).

## 4. Optional Accessibility navigation

1. In DeX setup/settings, tap **Open Accessibility settings**.
2. Find **DeX desktop navigation**, possibly under **Installed apps / Downloaded apps**.
3. Enable it and approve Android's own prompt. DeX never writes the secure Accessibility setting or silently enables the service.
4. Return to DeX. The status should become **Service enabled and bound**.
5. Optionally enable **Floating navigation outside DeX**. A small DeX/Nav pill appears over other apps. **DeX** returns to the desktop; **Nav** offers Back, Home, Recents, Lock and the control inspector.

If a sideloaded build shows Android's **Restricted setting** prompt, use Android's App info menu to allow restricted settings only if you trust the APK source. The app cannot and does not bypass that prompt. Do not disable device security features to make setup appear successful.

The inspector deliberately shows generic control labels, not text from the other app. Password and editable fields are excluded. It can send a tap/hold/scroll only when you explicitly choose that control.

## 5. After a reboot or service stop

Wireless debugging and Shizuku's shell service do not survive a reboot as an auto-starting root daemon. Reopen Shizuku and start it via Wireless debugging; pairing is normally retained, but Android may require pairing again after network/debugging changes. Return to DeX and refresh. If permission was revoked, allow DeX again inside Shizuku's authorized-app settings.

Troubleshooting:

- Keep Wi-Fi connected during pairing/start; a VPN or guest-network isolation can interfere with local discovery.
- Use a fresh pairing-code screen; its port changes.
- Toggle Wireless debugging off/on and retry Shizuku start.
- Update Shizuku if DeX reports an old API.
- If Shizuku is connected but the task bridge is stopped, use **Reconnect task bridge**.
- If the shell task API is incompatible with Samsung firmware, use **Android Recents**; local desktop windows remain available.
- Turning off Wireless debugging after Shizuku starts may stop the service on some firmware. DeX reports the loss rather than pretending to be connected.

## 6. Desktop controls

- Tap/click desktop icons to open tools or Android apps.
- Drag an icon to move it. Long-press or mouse right-click for context actions. Long-press apps in Start to pin/unpin.
- Drag a tool window's title bar; drag its bottom-right handle to resize. Double-click the title to maximize/restore. Minimize/close controls and taskbar restore are real.
- Notes autosave locally. Files requires choosing a folder with Android's picker. Calculator evaluates arithmetic locally.
- Start searches installed launchable apps. Recent is launch history, not a fake running-process list.
- The tray reads battery/network state, adjusts media volume and opens Android Wi-Fi/Bluetooth controls.
- Alt+Tab switches DeX windows first, then authorized Android tasks; Android may consume it globally. Super/Windows opens Start. Escape closes menus. Ctrl+W closes the focused DeX tool window, not an unrelated Android app.
- Power/restart opens Android's own power dialog through Accessibility. A restart button is not fabricated if firmware does not offer one.

## 7. Displays and freeform

**Device & displays** reports actual feature flags, display metrics and shell settings. Native freeform is experimental: an explicit consent dialog explains the two global development keys before changing them. You can restore both to `0`. No density/resolution command is issued.

SM-X110 cannot output wired video through its USB-C port. Android-visible public presentation displays can receive another DeX Activity where supported; a display that Android does not expose cannot be created by this app. See [compatibility](STANDALONE_COMPATIBILITY.md).
