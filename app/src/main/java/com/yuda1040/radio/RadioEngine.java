package com.yuda1040.radio;

import android.content.Context;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioTuner;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Method;

public final class RadioEngine {
    public interface Listener {
        void onFrequency(double f, boolean fm, boolean signal);
        void onMessage(String s);
    }

    private final Context context;
    private final Listener listener;
    private RadioTuner tuner;
    private boolean fm = true;
    private double current = 99.5;

    public RadioEngine(Context c, Listener l) { context=c.getApplicationContext(); listener=l; }

    public void open() {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            listener.onMessage("המקלט המקומי דורש Android 10 ומעלה");
            return;
        }
        try {
            RadioManager rm = (RadioManager) context.getSystemService(Context.RADIO_SERVICE);
            if (rm == null) { listener.onMessage("המכשיר אינו חושף מקלט FM/AM למערכת"); return; }

            RadioManager.ModuleProperties[] modules = rm.listModules();
            if (modules == null || modules.length == 0) {
                listener.onMessage("לא נמצא מקלט FM/AM חומרתי במכשיר");
                return;
            }
            RadioManager.BandConfig cfg = null;
            try {
                Method m = modules[0].getClass().getMethod("getBands");
                Object bands = m.invoke(modules[0]);
            } catch (Throwable ignored) {}

            RadioTuner.Callback cb = new RadioTuner.Callback() {
                @Override public void onProgramInfoChanged(RadioManager.ProgramInfo info) {
                    try {
                        ProgramSelector s = info.getSelector();
                        long khz = s.getFirstId(ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY);
                        boolean isFm = khz >= 60000;
                        current = isFm ? khz/1000.0 : khz;
                        fm = isFm;
                        boolean signal = info.getSignalStrength() > 0;
                        listener.onFrequency(current, fm, signal);
                    } catch (Throwable ignored) {}
                }
                @Override public void onAntennaState(boolean connected) {
                    listener.onMessage(connected ? "מחובר לאנטנה" : "האנטנה מנותקת");
                }
                @Override public void onError(int e) { listener.onMessage("שגיאת מקלט: " + e); }
            };

            tuner = rm.openTuner(0, cfg, true, cb, new Handler(Looper.getMainLooper()));
            if (tuner == null) listener.onMessage("לא ניתן לפתוח את מקלט הרדיו");
            else listener.onMessage("מקלט חומרתי מוכן");
        } catch (SecurityException e) {
            listener.onMessage("גישה למקלט הרדיו חסומה במכשיר הזה");
        } catch (Throwable e) {
            listener.onMessage("המכשיר לא מאפשר שימוש במקלט FM/AM");
        }
    }

    public void setBand(boolean isFm) {
        fm=isFm;
        current=isFm ? 99.5 : 1000;
        tune();
    }

    public void step(boolean up) {
        if (tuner == null) { listener.onMessage("אין מקלט חומרתי זמין"); return; }
        try { tuner.step(up ? RadioTuner.DIRECTION_UP : RadioTuner.DIRECTION_DOWN, true); }
        catch (Throwable e) { listener.onMessage("לא ניתן לשנות תדר"); }
    }

    public void findAlternative() {
        if (tuner == null) { listener.onMessage("חיפוש תדר מקביל זמין רק כאשר מקלט FM/AM חומרתי פעיל"); return; }
        listener.onMessage("מחפש תדר חלופי עם קליטה טובה…");
        try {
            tuner.scan(RadioTuner.DIRECTION_UP, true);
        } catch (Throwable e) {
            try { tuner.step(RadioTuner.DIRECTION_UP, true); }
            catch (Throwable ignored) { listener.onMessage("המקלט לא תומך בחיפוש אוטומטי"); }
        }
    }

    private void tune() {
        if (tuner == null) return;
        try {
            int khz = fm ? (int)Math.round(current*1000) : (int)Math.round(current);
            tuner.tune(ProgramSelector.createAmFmSelector(RadioManager.BAND_INVALID, khz));
        } catch (Throwable e) { listener.onMessage("תדר לא זמין"); }
    }

    public void close() { if (tuner != null) { try { tuner.close(); } catch(Throwable ignored) {} tuner=null; } }
}
