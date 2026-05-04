package com.hur.sourceswitcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Detects USB device connection. When a phone is plugged in via USB,
 * HUR auto-starts but the MCU source stays on whatever it was (e.g. TUNER).
 * This receiver waits a few seconds for HUR to initialize, then switches
 * MCU source to APP so audio plays through the car speakers.
 */
public class UsbReceiver extends BroadcastReceiver {
    private static final String TAG = "HURSourceSwitcher";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null) return;
        Log.i(TAG, "USB device attached, will switch to APP in 5 seconds");

        // Delay to let HUR start and initialize audio
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);

                    // Check if HUR is running
                    android.app.ActivityManager am = (android.app.ActivityManager)
                        context.getSystemService(Context.ACTIVITY_SERVICE);
                    java.util.List<android.app.ActivityManager.RunningAppProcessInfo> procs =
                        am.getRunningAppProcesses();
                    boolean hurRunning = false;
                    for (android.app.ActivityManager.RunningAppProcessInfo proc : procs) {
                        if ("com.andrerinas.headunitrevived".equals(proc.processName)) {
                            hurRunning = true;
                            break;
                        }
                    }

                    if (hurRunning) {
                        Log.i(TAG, "HUR is running after USB connect, switching to APP");
                        Intent svc = new Intent(context, SourceService.class);
                        svc.putExtra("target_source", "APP");
                        svc.putExtra("launch_hur", false);
                        context.startService(svc);
                    } else {
                        Log.i(TAG, "HUR not running, skipping source switch");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "USB handler error: " + e.getMessage(), e);
                }
            }
        }).start();
    }
}
