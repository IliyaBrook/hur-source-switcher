package com.hur.sourceswitcher;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Monitors logcat for steering wheel key events from KeyInfoService
 * and sends corresponding media key events to HUR/Android Auto.
 *
 * This approach bypasses the sendBroadcastAsUser limitation on Android 4.4.
 */
public class KeyMonitorService extends Service {
    private static final String TAG = "HURSourceSwitcher";
    private volatile boolean running = false;
    private Thread monitorThread;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            running = true;
            monitorThread = new Thread(new LogcatMonitor());
            monitorThread.start();
            Log.i(TAG, "Key monitor service started");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        super.onDestroy();
    }

    private class LogcatMonitor implements Runnable {
        @Override
        public void run() {
            Process logcatProcess = null;
            try {
                // Clear old logs and start monitoring
                Runtime.getRuntime().exec("logcat -c").waitFor();
                logcatProcess = Runtime.getRuntime().exec(new String[]{
                    "logcat", "-s", "KeyInfoService:*"
                });
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(logcatProcess.getInputStream()));

                String line;
                while (running && (line = reader.readLine()) != null) {
                    if (line.contains("setKeyInfoChange mKeyCode = ") && line.contains("aKeyState = NONE")) {
                        if (line.contains("mKeyCode = NEXT")) {
                            Log.i(TAG, "Logcat: NEXT detected, sending media next");
                            sendMediaKeyViaInstrumentation(87); // KEYCODE_MEDIA_NEXT
                        } else if (line.contains("mKeyCode = PRE")) {
                            Log.i(TAG, "Logcat: PRE detected, sending media previous");
                            sendMediaKeyViaInstrumentation(88); // KEYCODE_MEDIA_PREVIOUS
                        } else if (line.contains("mKeyCode = VR")) {
                            Log.i(TAG, "Logcat: VR detected, sending search key");
                            sendMediaKeyViaInstrumentation(84); // KEYCODE_SEARCH
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Logcat monitor error: " + e.getMessage(), e);
            } finally {
                if (logcatProcess != null) {
                    logcatProcess.destroy();
                }
            }
        }
    }

    private void sendMediaKeyViaInstrumentation(final int keyCode) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Use Instrumentation to send key event (same way CoagentSettings does it)
                    android.app.Instrumentation inst = new android.app.Instrumentation();
                    inst.sendKeyDownUpSync(keyCode);
                    Log.i(TAG, "Sent keyevent " + keyCode + " via Instrumentation");
                } catch (Exception e) {
                    Log.e(TAG, "Instrumentation failed for " + keyCode + ": " + e.getMessage());
                    // Fallback: try am broadcast with media button
                    try {
                        Runtime.getRuntime().exec(new String[]{
                            "sh", "-c",
                            "am broadcast -a android.intent.action.MEDIA_BUTTON --ei android.intent.extra.KEY_EVENT " + keyCode
                        }).waitFor();
                    } catch (Exception e2) {
                        Log.e(TAG, "Broadcast fallback also failed", e2);
                    }
                }
            }
        }).start();
    }
}
