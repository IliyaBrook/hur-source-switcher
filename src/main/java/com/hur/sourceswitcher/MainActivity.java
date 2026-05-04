package com.hur.sourceswitcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("HUR Source Switcher v1.5\n\n" +
            "SOURCE cycle: TUNER > BT > F_AUX > APP(HUR) > TUNER\n\n" +
            "Steering wheel buttons: NEXT, PRE, VR\n\n" +
            "Key monitor service started.");
        tv.setTextSize(18);
        tv.setPadding(40, 40, 40, 40);
        setContentView(tv);

        // Start the key monitor service
        startService(new Intent(this, KeyMonitorService.class));
    }
}
