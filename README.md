# HUR Source Switcher

A companion app for [Head Unit Revived (HUR)](https://github.com/andreknieriem/headunit-revived) running on the Coagent V8AUTO-MX6Q head unit (Android 4.4.2). It adds HUR to the steering wheel SOURCE/MODE button cycle and makes audio routing to HUR work automatically.

## Table of Contents

- [What is this for?](#what-is-this-for)
- [The Problem](#the-problem-technical)
- [The Solution](#the-solution)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Development (Makefile)](#development-makefile)
- [Building from Source (Manual/Termux)](#building-from-source-manualtermux)
- [MCU Control Reference](#mcu-control--command-reference)
- [Known Issues](#known-issues)
- [Files](#files)
- [License](#license)

## What is this for?

[Head Unit Revived](https://github.com/andreknieriem/headunit-revived) brings Android Auto to older aftermarket head units. On the V8AUTO-MX6Q (and similar Coagent units) it works visually, but audio does not pass through to the speakers out of the box — the head unit's MCU routes audio per *source*, and HUR lives inside the Android stack which the MCU does not switch to on its own. There is also no way to reach HUR with the steering wheel SOURCE/MODE button: that button cycles only between built-in sources (radio, USB, AUX, Bluetooth) and skips Android apps entirely.

This app fixes both problems together. With it installed, pressing SOURCE/MODE on the steering wheel rotates HUR into the cycle: when you reach the HUR slot, the app launches automatically and the MCU is switched to the APP source so audio from HUR goes to the speakers. Pressing SOURCE/MODE again moves on to the next source (e.g. radio) and restores normal audio routing. No touchscreen tap, no manual audio switching — one button on the wheel takes you to HUR and back.

## The Problem (technical)

The V8AUTO head unit has a SOURCE/MODE button on the steering wheel that cycles between audio sources: TUNER (radio), USB, BTAUDIO, F_AUX. The HUR app (Android Auto) is not part of this cycle, so once you switch to another source, you can't return to HUR using the steering wheel button. On top of that, audio from Android apps (HUR included) only reaches the speakers when the MCU's active source is `APP`, and the stock firmware never selects `APP` from the SOURCE button.

## The Solution

The `com.hur.sourceswitcher` app listens to the `com.coagent.intent.action.SOURCE_CHANGED` broadcast and intercepts two transitions:

1. **F_AUX → TUNER**: switches to APP instead of TUNER, launches HUR, and routes audio to the speakers
2. **APP → F_AUX**: switches to TUNER instead of F_AUX (closes the cycle)
3. **NEXT/PRE/VR buttons**: intercepts steering wheel media keys and sends them to HUR (requires `keyrelay` daemon and Root).

### Result — steering wheel SOURCE cycle:
```
TUNER → [USB if connected] → [BTAUDIO if BT] → F_AUX → APP (HUR) → TUNER → ...
```

## How audio routing works through HUR

The head unit uses an MCU (microcontroller) for audio routing. To get audio from Android apps to the speakers, the MCU source must be switched to APP:

```bash
# Switch source to APP (audio from Android → speakers)
adb shell "service call coagent.source 1 s16 APP i32 0"

# Unmute MCU
adb shell "service call coagent.settings 11 i32 0"
```

The app does this automatically when switching to APP.

## Important details

### Mute on switching
When switching source, the app first mutes the audio, switches the source, then unmutes. This prevents two audio sources (radio + APP) from overlapping, which causes distortion.

### 3-second cooldown
After each interception there is a 3-second pause to prevent looping (our switch also generates a broadcast).

### Activation after install
On Android 4.4, an app in the "stopped" state (just installed, never launched) does not receive broadcasts. After installing the APK, MainActivity must be launched once:
```bash
adb shell "am start -n com.hur.sourceswitcher/.MainActivity"
```

## Prerequisites

- **Device**: Coagent V8AUTO-MX6Q head unit (or compatible).
- **OS**: Android 4.4.2 (API 19).
- **Root access**: Required for steering wheel button support (via `keyrelay` daemon) and recommended for reliable source switching.
- **Head Unit Revived (HUR)**: Installed and configured.

## Installation

```bash
# Connect to the head unit via WiFi ADB
adb connect <head unit IP>:5555

# Install the APK
adb install dist/hur-source-switcher.apk

# Launch once to activate
adb shell "am start -n com.hur.sourceswitcher/.MainActivity"
```

## Development (Makefile)

For a quick development cycle on a PC (requires Gradle and ADB in PATH):

```bash
# Build and copy APK to dist/hur-source-switcher.apk
make dist

# Install to connected device
make install

# Launch and follow logs
make run
make logcat

# Clean build artifacts
make clean
```

## Building from source (Manual/Termux)

Required packages: `ecj`, `dx`, `aapt2`, `apksigner`

```bash
# 1. Pull framework-res.apk from the head unit (needed for aapt2)
adb pull /system/framework/framework-res.apk

# 2. Pull framework.jar and convert to a jar with .class files
adb pull /system/framework/framework.jar
unzip framework.jar classes.dex
d2j-dex2jar classes.dex -o framework-classes.jar

# 3. Compile
ecj -source 1.7 -target 1.7 -classpath framework-classes.jar -d build/classes src/main/java/com/hur/sourceswitcher/*.java
```

# 4. Convert to DEX
```bash
dx --dex --output=build/classes.dex build/classes/
```

# 5. Build APK
```bash
aapt2 link --manifest src/main/AndroidManifest.xml -o build/base.apk -I framework-res.apk \
  --min-sdk-version 19 --target-sdk-version 19 --version-code 1 --version-name 1.5
```
cp build/base.apk build/app.apk
zip -j build/app.apk build/classes.dex

# 6. Sign
keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey \
  -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Debug, OU=Debug, O=Debug, L=Debug, ST=Debug, C=US"
zipalign -f 4 build/app.apk build/app-aligned.apk
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey build/app-aligned.apk
```

## MCU control — command reference

```bash
# Source
service call coagent.source 1 s16 APP i32 0    # switch to APP
service call coagent.source 1 s16 TUNER i32 0  # switch to radio
service call coagent.source 3                    # get current source

# Audio
service call coagent.settings 11 i32 0   # unmute
service call coagent.settings 11 i32 1   # mute
service call coagent.settings 13         # get mute status
service call coagent.settings 6          # volume up
service call coagent.settings 7          # volume down
service call coagent.settings 22         # get volume

# Diagnostics
dumpsys media.audio_flinger | grep -A5 "active tracks"
dumpsys audio
dumpsys media.audio_policy
```

## Known issues

1. **AUX flicker on APP→TUNER**: When switching from APP to the next source (TUNER), the AUX icon flickers briefly. This is a limitation of the broadcast-based approach — the system first switches to F_AUX (the default), then our receiver intercepts and switches to TUNER.

2. **Distortion after repeated switching**: If you switch source rapidly many times, the MCU may enter a faulty state and audio starts to distort. Fixed by fully turning off the ignition for 30 seconds (a normal Android reboot does not reset the MCU).

3. **HUR audio sink quality**: HUR outputs audio at 48000 Hz, while the head unit's mixer runs at 44100 Hz. Resampling on the underpowered i.MX6 may cause artifacts.

## Head unit Source IDs (SourceConstantsDef.SourceID)

| Source | Byte | Description |
|--------|------|-------------|
| TUNER | 0 | FM/AM radio |
| USB | 10 | USB drive |
| F_AUX | 12 | Front AUX |
| APP | 17 | Android apps (DAC) |
| AVOFF | 18 | Screen off |
| BTAUDIO | 25 | Bluetooth A2DP |

## Files

- `Makefile` — development shortcuts
- `dist/` — (ignored) directory for build artifacts
- `src/main/AndroidManifest.xml` — manifest
- `src/main/java/com/hur/sourceswitcher/MainActivity.java` — activity used to activate the app
- `src/main/java/com/hur/sourceswitcher/SourceReceiver.java` — BroadcastReceiver, intercepts SOURCE_CHANGED
- `src/main/java/com/hur/sourceswitcher/SourceService.java` — service that switches source via shell
- `src/main/java/com/hur/sourceswitcher/KeyReceiver.java` — Receiver for steering wheel buttons
- `src/main/java/com/hur/sourceswitcher/KeyMonitorService.java` — Service for monitoring key events
- `src/main/java/com/hur/sourceswitcher/UsbReceiver.java` — Receiver for USB events
- `build.gradle` / `settings.gradle` — Gradle configuration

## Disclaimer

**USE AT YOUR OWN RISK.** This app interacts with the head unit's MCU via system services. The author is not responsible for any damage to your hardware, loss of data, or accidents caused by using this software. RAPID SWITCHING may cause the MCU to enter a temporary faulty state (see Known Issues).

## License

This project is licensed under the MIT License.
