package com.hur.sourceswitcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Inserts APP into the SOURCE cycle on the steering wheel.
 *
 * Original cycle: TUNER -> [USB] -> [BTAUDIO] -> F_AUX -> TUNER
 * Modified cycle:  TUNER -> [USB] -> [BTAUDIO] -> F_AUX -> APP -> TUNER
 *
 * Two interceptions:
 * 1. F_AUX -> TUNER: intercept, switch to APP instead (insert APP after F_AUX)
 * 2. APP -> F_AUX: intercept, switch to TUNER instead (APP exits to TUNER, not F_AUX)
 *
 * Cooldown prevents infinite loops from our own switches triggering broadcasts.
 */
public class SourceReceiver extends BroadcastReceiver {
    private static final String TAG = "HURSourceSwitcher";
    private static final String ACTION_SOURCE_CHANGED = "com.coagent.intent.action.SOURCE_CHANGED";
    private static final long COOLDOWN_MS = 3000;
    private static long lastSwitchTime = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        if ("android.intent.action.BOOT_COMPLETED".equals(action)) {
            Log.i(TAG, "Boot completed, starting key monitor and keyrelay daemon");
            Intent svc = new Intent(context, KeyMonitorService.class);
            context.startService(svc);
            // Start keyrelay daemon (runs as root from init context)
            try {
                Runtime.getRuntime().exec(new String[]{
                    "sh", "-c",
                    "echo > /data/local/tmp/keyrelay_cmd && chmod 666 /data/local/tmp/keyrelay_cmd && " +
                    "nohup sh -c 'while true; do line=$(cat /data/local/tmp/keyrelay_cmd 2>/dev/null); " +
                    "case \"$line\" in [0-9]*) echo > /data/local/tmp/keyrelay_cmd; input keyevent $line 2>/dev/null;; esac; " +
                    "sleep 1; done' > /dev/null 2>&1 &"
                });
                Log.i(TAG, "Keyrelay daemon started");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start keyrelay: " + e.getMessage());
            }
            return;
        }

        if (!ACTION_SOURCE_CHANGED.equals(action)) return;

        String from = intent.getStringExtra("extra_from");
        String to = intent.getStringExtra("extra_to");
        Log.i(TAG, "Source changed: " + from + " -> " + to);

        long now = System.currentTimeMillis();
        if (now - lastSwitchTime < COOLDOWN_MS) {
            Log.i(TAG, "Cooldown active, ignoring");
            return;
        }

        if ("F_AUX".equals(from) && "TUNER".equals(to)) {
            // Normal cycle end: F_AUX -> TUNER. Insert APP here.
            Log.i(TAG, "Intercepting F_AUX->TUNER, switching to APP");
            lastSwitchTime = now;
            Intent svc = new Intent(context, SourceService.class);
            svc.putExtra("target_source", "APP");
            svc.putExtra("launch_hur", true);
            context.startService(svc);

        } else if ("APP".equals(from) && "F_AUX".equals(to)) {
            // User pressed SOURCE on APP. System defaulted to F_AUX.
            // Skip F_AUX and go straight to TUNER to complete the cycle.
            Log.i(TAG, "Intercepting APP->F_AUX, skipping to TUNER");
            lastSwitchTime = now;
            Intent svc = new Intent(context, SourceService.class);
            svc.putExtra("target_source", "TUNER");
            svc.putExtra("launch_hur", false);
            context.startService(svc);
        }
    }
}
