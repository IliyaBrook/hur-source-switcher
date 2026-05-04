package com.hur.sourceswitcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Intercepts steering wheel key events (NEXT, PRE, VR) and translates them
 * to standard Android media/input key events for HUR/Android Auto.
 *
 * Only active when current source is APP.
 */
public class KeyReceiver extends BroadcastReceiver {
    private static final String TAG = "HURSourceSwitcher";
    private static final String ACTION_KEY_CHANGED = "com.coagent.intent.action.KEY_CHANGED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_KEY_CHANGED.equals(intent.getAction())) return;

        String keyState = intent.getStringExtra("Key_state");
        String keyCode = intent.getStringExtra("Key_code");

        if (keyCode == null) return;
        if (keyState != null && !"NONE".equals(keyState) && !"DOWN".equals(keyState)) return;

        if ("NEXT".equals(keyCode)) {
            Log.i(TAG, "NEXT key -> MEDIA_NEXT");
            sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT);
        } else if ("PRE".equals(keyCode)) {
            Log.i(TAG, "PRE key -> MEDIA_PREVIOUS");
            sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        } else if ("VR".equals(keyCode)) {
            Log.i(TAG, "VR key -> Google Assistant");
            launchAssistant(context);
        }
    }

    private void sendMediaKey(final Context context, final int keyCode) {
        // Write keycode to file, root daemon picks it up and executes input keyevent
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/data/local/tmp/keyrelay_cmd");
                    fw.write(String.valueOf(keyCode));
                    fw.close();
                    Log.i(TAG, "Sent keycode " + keyCode + " via keyrelay file");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to write keyrelay: " + e.getMessage());
                }
            }
        }).start();
    }

    private void launchAssistant(final Context context) {
        // MCU handles VR as mute toggle at hardware level.
        // We need to undo the mute first, then trigger assistant.
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Unmute MCU (undo hardware mute from VR key)
                    Runtime.getRuntime().exec(new String[]{
                        "sh", "-c", "service call coagent.settings 11 i32 0"
                    }).waitFor();
                    Thread.sleep(100);
                    // Send KEYCODE_SEARCH to trigger assistant
                    java.io.FileWriter fw = new java.io.FileWriter("/data/local/tmp/keyrelay_cmd");
                    fw.write("84");
                    fw.close();
                    Log.i(TAG, "VR: unmuted + sent KEYCODE_SEARCH");
                } catch (Exception e) {
                    Log.e(TAG, "VR handler error: " + e.getMessage());
                }
            }
        }).start();
    }
}
