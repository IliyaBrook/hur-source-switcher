package com.hur.sourceswitcher;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class SourceService extends Service {
    private static final String TAG = "HURSourceSwitcher";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String targetSource = intent != null ? intent.getStringExtra("target_source") : "APP";
        final boolean launchHur = intent != null && intent.getBooleanExtra("launch_hur", false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                switchSource(targetSource != null ? targetSource : "APP");
                if (launchHur) {
                    launchHUR();
                }
                stopSelf();
            }
        }).start();
        return START_NOT_STICKY;
    }

    private void switchSource(String source) {
        try {
            // Switch source directly, then ensure unmute
            Process sw = Runtime.getRuntime().exec(new String[]{
                "sh", "-c",
                "service call coagent.source 1 s16 " + source + " i32 0 && service call coagent.settings 11 i32 0"
            });
            sw.waitFor();
            Log.i(TAG, "Switched to " + source);
        } catch (Exception e) {
            Log.e(TAG, "Error switching source: " + e.getMessage(), e);
        }
    }

    private void launchHUR() {
        try {
            Intent launchIntent = getPackageManager()
                .getLaunchIntentForPackage("com.andrerinas.headunitrevived");
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
                Log.i(TAG, "Launched HUR");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error launching HUR: " + e.getMessage(), e);
        }
    }
}
