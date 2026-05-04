# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project nature

A small companion APK for the Coagent V8AUTO-MX6Q head unit (Android 4.4.2, API 19). It hooks into the MCU's `SOURCE_CHANGED` and `KEY_CHANGED` broadcasts to splice the third-party HUR (Head Unit Revived / Android Auto) app into the steering-wheel SOURCE cycle and route audio through the `APP` MCU source.

This is **not** a standard Android Studio / Gradle project. There is no `build.gradle`, no `app/` module, no resources directory — just `src/AndroidManifest.xml` and Java sources. The build pipeline is hand-rolled and runs from Termux on the device itself (see `README.md` "Building from source").

## Source layout quirk

Sources live under `src/ui/` and `src/module/`, but **every `.java` file declares `package com.hur.sourceswitcher`** — the directories are organizational only and do not reflect the package. The manifest uses dot-prefixed names (`.KeyReceiver`, `.SourceService`, etc.) that resolve against `package="com.hur.sourceswitcher"` in `AndroidManifest.xml`. When adding a new class, place it in either directory but keep the flat package, and remember to add it to the `ecj` compile command in the README's build steps (sources are listed explicitly: `src/ui/*.java src/module/*.java`).

## Build / install

Building requires the device's own `framework-res.apk` and `framework.jar` (pulled via ADB) — these are not in the repo and are gitignored. The end-to-end commands are in `README.md` under "Building from source (Termux)". For a normal dev cycle the workflow is:

```bash
# Install (after rebuilding the APK in Termux)
adb connect <head unit IP>:5555
adb install -r hur-source-switcher.apk

# Mandatory after first install: app must be launched once or
# Android 4.4 will not deliver broadcasts to it (stopped state).
adb shell "am start -n com.hur.sourceswitcher/.MainActivity"

# Tail logs (everything tags as "HURSourceSwitcher")
adb logcat -s HURSourceSwitcher:*
```

There is no test suite, no lint config, no formatter — code is plain Android-4.4-era Java 1.7 (no diamond <>, no try-with-resources in some files, no Java 8 APIs).

## Runtime architecture

The app is fully event-driven; there is no foreground activity logic apart from `MainActivity` which exists only to take the package out of the "stopped" state and start `KeyMonitorService`.

**Two independent interception loops:**

1. **Source cycle** — `SourceReceiver` (manifest receiver for `com.coagent.intent.action.SOURCE_CHANGED`) detects two specific MCU transitions and overrides them by starting `SourceService`:
   - `F_AUX → TUNER` is rewritten to `→ APP` (and HUR is launched). This inserts APP into the wheel cycle after F_AUX.
   - `APP → F_AUX` is rewritten to `→ TUNER`. The MCU defaults to F_AUX when leaving an unknown source; we skip it to close the cycle cleanly. This is the cause of the brief AUX icon flicker noted in `TODO.md`.
   - A 3-second static `lastSwitchTime` cooldown blocks re-entry — our own `service call` to switch sources also fires `SOURCE_CHANGED`, so without the cooldown this would loop.
   - `SourceService` shells out via `Runtime.exec` to `service call coagent.source 1 s16 <SRC> i32 0 && service call coagent.settings 11 i32 0` (switch + unmute) on a worker thread, then optionally launches HUR via `getLaunchIntentForPackage("com.andrerinas.headunitrevived")`.

2. **Steering-wheel media keys** — there are **two parallel implementations** because Android 4.4's `sendBroadcastAsUser(..., UserHandle.ALL)` (used by the MCU) does not reliably reach third-party apps:
   - `KeyReceiver` (manifest receiver, priority 999) listens for `com.coagent.intent.action.KEY_CHANGED` directly. Unreliable per `TODO.md`.
   - `KeyMonitorService` (started from `MainActivity` and from `BOOT_COMPLETED` in `SourceReceiver`) instead spawns a `logcat -s KeyInfoService:*` subprocess and parses lines containing `setKeyInfoChange mKeyCode = ... aKeyState = NONE`. This is the more reliable path.
   - When a key is detected, `KeyMonitorService` first tries `Instrumentation.sendKeyDownUpSync(keyCode)`; on failure it falls back to `am broadcast android.intent.action.MEDIA_BUTTON`.
   - `KeyReceiver` takes a different fallback route: it writes the keycode as ASCII to `/data/local/tmp/keyrelay_cmd`. A separate **root shell daemon** ("keyrelay") polls that file every 1s and runs `input keyevent <code>`. The daemon is launched on `BOOT_COMPLETED` by `SourceReceiver` via a long `Runtime.exec(["sh","-c", ...])` one-liner. Root is needed because injecting key events requires `INJECT_EVENTS`, which a regular user-installed app cannot hold without a platform-key signature.

3. **USB attach** — `UsbReceiver` (`USB_DEVICE_ATTACHED`) waits 5 s, then checks `ActivityManager.getRunningAppProcesses()` for `com.andrerinas.headunitrevived`. If HUR is up, it starts `SourceService` to switch to APP without launching HUR (HUR auto-starts on USB attach; this only fixes audio routing).

**Important constants and IDs** (referenced across files; also in `README.md` and `TODO.md`):
- HUR package: `com.andrerinas.headunitrevived`
- MCU broadcasts: `com.coagent.intent.action.SOURCE_CHANGED`, `com.coagent.intent.action.KEY_CHANGED`
- `extra_from` / `extra_to` extras on `SOURCE_CHANGED`; `Key_state` / `Key_code` on `KEY_CHANGED`
- Source names (strings): `TUNER`, `USB`, `F_AUX`, `APP`, `BTAUDIO`
- Steering wheel key codes (strings): `NEXT`, `PRE`, `VR`
- All log entries use tag `HURSourceSwitcher`

## Open issues to be aware of

`TODO.md` is the source of truth for known limitations. Highlights that affect implementation choices:
- VR (voice) button **cannot** be intercepted from Android — the MCU handles it as a hardware mute toggle and never emits a `KEY_CHANGED` for it. Any `VR`-handling code paths in `KeyReceiver` / `KeyMonitorService` are best-effort and currently dead in practice.
- The keyrelay daemon's 1 s `sleep` adds up to 1 s of latency on NEXT/PRE; the daemon is also occasionally killed by the system. Both are flagged as "things to try" rather than fixed.
- AUX icon flickers on APP→TUNER are inherent to the broadcast-interception approach (the MCU has already committed to F_AUX before our receiver runs).

## Conventions

- Follow the existing logging style — single tag `HURSourceSwitcher`, `Log.i` for normal flow, `Log.e` with the exception message for failures.
- Keep all shell-outs on worker threads (every existing one already does this); broadcast receivers' `onReceive` runs on the main thread and a long `Runtime.exec().waitFor()` will ANR.
- Don't introduce Java 8+ syntax or APIs — `ecj -source 1.7 -target 1.7` is what the README's build uses, and the device runs Dalvik on Android 4.4. Existing code uses anonymous `Runnable` rather than lambdas for this reason.
- The `hur-source-switcher.apk` at the repo root is the prebuilt signed artifact and is intentionally **not** gitignored (the `.gitignore` has `!hur-source-switcher.apk`). Don't add other `.apk` files alongside it without considering this.
