package com.yuda1040.radio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.*;

public final class RadioEngine {
    public interface Listener {
        void onFrequency(double f, boolean fm, boolean signal);
        void onMessage(String s);
    }

    private final Context context;
    private final Listener listener;
    private Object tuner;
    private boolean fm = true;
    private double current = 99.5;

    public RadioEngine(Context c, Listener l) { context=c.getApplicationContext(); listener=l; }

    private Class<?> cls(String n) throws Exception { return Class.forName(n); }

    public void open() {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            listener.onMessage("המקלט המקומי דורש Android 10 ומעלה"); return;
        }
        try {
            Class<?> rmClass = cls("android.hardware.radio.RadioManager");
            Object rm = context.getSystemService("broadcastradio");
            if (rm == null) { listener.onMessage("המכשיר אינו חושף מקלט FM/AM למערכת"); return; }

            Method listModules = rmClass.getMethod("listModules");
            Object modules = listModules.invoke(rm);
            if (modules == null || Array.getLength(modules) == 0) {
                listener.onMessage("לא נמצא מקלט FM/AM חומרתי במכשיר"); return;
            }

            Method open = rmClass.getMethod("openTuner", int.class,
                    cls("android.hardware.radio.RadioManager$BandConfig"),
                    boolean.class, cls("android.hardware.radio.RadioTuner$Callback"),
                    Handler.class);
            tuner = open.invoke(rm, 0, null, true, null, new Handler(Looper.getMainLooper()));
            if (tuner == null) listener.onMessage("לא ניתן לפתוח את מקלט הרדיו");
            else {
                listener.onMessage("מקלט חומרתי מוכן");
                tune();
            }
        } catch (SecurityException e) {
            listener.onMessage("גישה למקלט הרדיו חסומה במכשיר הזה");
        } catch (Throwable e) {
            listener.onMessage("המכשיר לא מאפשר שימוש במקלט FM/AM");
        }
    }

    public void setBand(boolean isFm) {
        fm=isFm; current=isFm ? 99.5 : 1000; tune();
    }

    public void step(boolean up) {
        if (tuner == null) { listener.onMessage("אין מקלט חומרתי זמין"); return; }
        try {
            Class<?> rt = cls("android.hardware.radio.RadioTuner");
            Method m = rt.getMethod("step", int.class, boolean.class);
            int dir = rt.getField(up ? "DIRECTION_UP" : "DIRECTION_DOWN").getInt(null);
            m.invoke(tuner, dir, true);
            readInfo();
        } catch (Throwable e) { listener.onMessage("לא ניתן לשנות תדר"); }
    }

    public void findAlternative() {
        if (tuner == null) {
            listener.onMessage("חיפוש תדר מקביל זמין רק כאשר מקלט FM/AM חומרתי פעיל"); return;
        }
        listener.onMessage("מחפש תדר חלופי עם קליטה טובה…");
        try {
            Class<?> rt = cls("android.hardware.radio.RadioTuner");
            int dir = rt.getField("DIRECTION_UP").getInt(null);
            rt.getMethod("scan", int.class, boolean.class).invoke(tuner, dir, true);
            new Handler(Looper.getMainLooper()).postDelayed(this::readInfo, 900);
        } catch (Throwable e) {
            step(true);
        }
    }

    private void tune() {
        if (tuner == null) return;
        try {
            Class<?> ps = cls("android.hardware.radio.ProgramSelector");
            Class<?> rm = cls("android.hardware.radio.RadioManager");
            int khz = fm ? (int)Math.round(current*1000) : (int)Math.round(current);
            int invalid = rm.getField("BAND_INVALID").getInt(null);
            Object selector = ps.getMethod("createAmFmSelector", int.class, int.class)
                    .invoke(null, invalid, khz);
            tuner.getClass().getMethod("tune", ps).invoke(tuner, selector);
            new Handler(Looper.getMainLooper()).postDelayed(this::readInfo, 700);
        } catch (Throwable e) { listener.onMessage("תדר לא זמין"); }
    }

    private void readInfo() {
        if (tuner == null) return;
        try {
            Class<?> rt = cls("android.hardware.radio.RadioTuner");
            Class<?> pi = cls("android.hardware.radio.RadioManager$ProgramInfo");
            Object arr = Array.newInstance(pi, 1);
            Method m = rt.getMethod("getProgramInformation", arr.getClass());
            int result = (Integer)m.invoke(tuner, arr);
            Object info = Array.get(arr, 0);
            if (result == 0 && info != null) {
                Object selector = info.getClass().getMethod("getSelector").invoke(info);
                Class<?> ps = cls("android.hardware.radio.ProgramSelector");
                int type = ps.getField("IDENTIFIER_TYPE_AMFM_FREQUENCY").getInt(null);
                long khz = ((Number)ps.getMethod("getFirstId", int.class).invoke(selector, type)).longValue();
                fm = khz >= 60000; current = fm ? khz/1000.0 : khz;
                int strength = ((Number)info.getClass().getMethod("getSignalStrength").invoke(info)).intValue();
                listener.onFrequency(current, fm, strength > 0);
            }
        } catch (Throwable ignored) {}
    }

    public void close() {
        if (tuner != null) {
            try { tuner.getClass().getMethod("close").invoke(tuner); } catch(Throwable ignored) {}
            tuner=null;
        }
    }
}
