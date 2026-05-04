# TODO — HUR Source Switcher

## Open issues

### 1. NEXT/PRE buttons — unstable
**Status:** Partially working, needs improvement

**Current implementation:**
- `KeyReceiver.java` listens to the `com.coagent.intent.action.KEY_CHANGED` broadcast from the MCU
- Problem: the MCU sends the broadcast via `sendBroadcastAsUser(intent, UserHandle.ALL)`, which does not always reach a third-party app on Android 4.4
- On NEXT/PRE press, the keycode is written to the file `/data/local/tmp/keyrelay_cmd`
- A background shell script `keyrelay` (running as root) reads the file and runs `input keyevent <code>` as root (a regular app cannot inject events — that requires `INJECT_EVENTS` permission)
- KEYCODE_MEDIA_NEXT = 87, KEYCODE_MEDIA_PREVIOUS = 88

**Known problems:**
- After switching SOURCE back and forth, the buttons sometimes stop working
- The `KEY_CHANGED` broadcast from the MCU is not always delivered to our receiver (sendBroadcastAsUser limitation)
- The `keyrelay` daemon must be running (started on BOOT_COMPLETED and when MainActivity is opened), but it can die
- Polling the file with `sleep 1` adds up to 1 second of latency

**Things to try:**
- Replace the broadcast receiver with logcat monitoring (KeyMonitorService.java already exists but hasn't been fully tested) — logcat catches all key presses reliably
- Reduce the sleep in keyrelay to 0.2-0.3 sec for lower latency
- Check whether the system is killing the keyrelay daemon
- Consider using `inotifywait` instead of polling

### 2. VR button (voice assistant) — not working
**Status:** Cannot be intercepted with the current approach

**Problem:**
- The VR button on the steering wheel is handled by the MCU **in hardware** as a mute/unmute toggle
- The MCU does not send a `KEY_CHANGED` broadcast for VR — Android never sees it
- No way to intercept via broadcast receiver or logcat

**Possible solutions:**
- Intercept via the UART protocol (requires reverse-engineering the MCU ↔ Android protocol)
- Patch the MCU firmware (risky)
- Use a different physical button on the steering wheel (if one is free)
- Live with it and invoke the assistant via the Android Auto screen

### 3. Phone calls — audio doesn't go to speakers
**Status:** Android Auto architectural limitation

**Problem:**
- Android Auto requires Bluetooth for call audio, even over USB
- HUR issue #409 (OPEN) — the developer is aware of the problem

**Possible solutions:**
- Wait for a fix from the HUR developer
- Use Bluetooth HFP in parallel with USB for calls

### 4. AUX flicker on APP → TUNER switch
**Status:** Cosmetic bug, broadcast-approach limitation

**Problem:**
- When switching from APP to the next source, the AUX icon flickers briefly
- Cause: the system first switches to F_AUX (the default for an unknown source), then our receiver intercepts and switches to TUNER

**Possible solutions:**
- Patch CoagentSettings.apk — add APP directly to `SRCSourceManager.getConnectedDevice()` (proper baksmali/smali, not dex2jar)
- Xposed/LSPosed hook (but Xposed Framework for Android 4.4 is required)

## Technical notes

### Keyrelay daemon
```bash
# Manual launch (as root via ADB):
echo > /data/local/tmp/keyrelay_cmd && chmod 666 /data/local/tmp/keyrelay_cmd
nohup sh -c 'while true; do line=$(cat /data/local/tmp/keyrelay_cmd 2>/dev/null); case "$line" in [0-9]*) echo > /data/local/tmp/keyrelay_cmd; input keyevent $line 2>/dev/null;; esac; sleep 1; done' > /dev/null 2>&1 &
```

### Why keyrelay is needed
- `input keyevent` requires root or `INJECT_EVENTS` permission
- Our app runs as a regular user (u0_a37)
- Installing into `/system/app/` does not grant `INJECT_EVENTS` without a platform-key signature
- Keyrelay runs as root (via BOOT_COMPLETED broadcast → shell exec) and reads commands from a file

### MCU source IDs
| Source | Byte | Description |
|--------|------|-------------|
| TUNER | 0 | FM/AM radio |
| USB | 10 | USB drive |
| F_AUX | 12 | Front AUX |
| APP | 17 | Android apps |
| BTAUDIO | 25 | Bluetooth A2DP |

### Useful ADB commands
```bash
service call coagent.source 1 s16 APP i32 0   # switch to APP
service call coagent.source 3                   # current source
service call coagent.settings 11 i32 0          # unmute
service call coagent.settings 11 i32 1          # mute
input keyevent 87                               # MEDIA_NEXT
input keyevent 88                               # MEDIA_PREVIOUS
```
