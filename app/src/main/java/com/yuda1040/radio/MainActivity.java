package com.yuda1040.radio;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private RadioEngine radio;
    private TextView freq, band, status;
    private SeekBar seek;
    private boolean fm = true;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        radio = new RadioEngine(this, new RadioEngine.Listener() {
            public void onFrequency(double f, boolean isFm, boolean signal) {
                main.post(() -> {
                    freq.setText(isFm ? String.format(java.util.Locale.US, "%.1f", f) : String.valueOf((int)f));
                    band.setText(isFm ? "FM" : "AM");
                    status.setText(signal ? "מחובר לאנטנה • קליטה טובה" : "אין אות יציב");
                });
            }
            public void onMessage(String s) { main.post(() -> status.setText(s)); }
        });
        radio.open();
    }

    private TextView tv(String text, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(size);
        t.setTextColor(Color.rgb(24,29,45)); t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,30,24,24);
        root.setBackgroundColor(Color.rgb(247,248,252)); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = tv("רדיו מקומי", 28, true);
        root.addView(title, new LinearLayout.LayoutParams(-1,70));

        band = tv("FM", 16, true); root.addView(band, new LinearLayout.LayoutParams(-1,45));

        freq = tv("87.5", 62, true);
        root.addView(freq, new LinearLayout.LayoutParams(-1,150));

        status = tv("מאתחל את מקלט הרדיו…", 15, false);
        root.addView(status, new LinearLayout.LayoutParams(-1,55));

        seek = new SeekBar(this);
        seek.setMax(100);
        root.addView(seek, new LinearLayout.LayoutParams(-1,55));

        LinearLayout bands = new LinearLayout(this); bands.setGravity(Gravity.CENTER);
        Button fmBtn = button("FM"); Button amBtn = button("AM");
        bands.addView(fmBtn, weight()); bands.addView(amBtn, weight());
        root.addView(bands, new LinearLayout.LayoutParams(-1,65));
        fmBtn.setOnClickListener(v -> { fm=true; radio.setBand(true); });
        amBtn.setOnClickListener(v -> { fm=false; radio.setBand(false); });

        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER);
        Button down = button("◀  הקודם"); Button auto = button("חיפוש תדר מקביל"); Button up = button("הבא  ▶");
        controls.addView(down, weight()); controls.addView(auto, weight()); controls.addView(up, weight());
        root.addView(controls, new LinearLayout.LayoutParams(-1,80));
        down.setOnClickListener(v -> radio.step(false));
        up.setOnClickListener(v -> radio.step(true));
        auto.setOnClickListener(v -> radio.findAlternative());

        TextView note = tv("קליטה מקומית בלבד • ללא אינטרנט", 13, false);
        note.setTextColor(Color.DKGRAY); root.addView(note, new LinearLayout.LayoutParams(-1,50));
        setContentView(root);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -1, 1f);
    }
    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setTextSize(13); return b;
    }

    @Override protected void onDestroy() {
        if (radio != null) radio.close(); super.onDestroy();
    }
}
