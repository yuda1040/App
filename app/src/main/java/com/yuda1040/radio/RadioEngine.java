package com.yuda1040.radio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.lang.reflect.*;
import com.android.dx.stock.ProxyBuilder;

public final class RadioEngine {
    public interface Listener {
        void onFrequency(double f, boolean fm, boolean signal);
        void onMessage(String s);
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Object tuner;
    private Object callback;
    private int moduleId = -1;
    private boolean fm = true;
    private double current = 99.5;
    private boolean opening = false;

    public RadioEngine(Context c, Listener l) {
        context = c.getApplicationContext();
        listener = l;
    }

    private Class<?> cls(String n) throws Exception { return Class.forName(n); }

    public void open() {
        if (Build.VERSION.SDK_INT < 29) {
            listener.onMessage("המקלט המקומי דורש Android 10 ומעלה");
            return;
        }
        if (opening || tuner != null) return;
        opening = true;
        try {
            Class<?> rmClass = cls("android.hardware.radio.RadioManager");
            Object rm = context.getSystemService("broadcastradio");
            if (rm == null) {
                opening = false;
                listener.onMessage("המכשיר אינו חושף מקלט FM/AM למערכת");
                return;
            }

            Object modules = rmClass.getMethod("listModules").invoke(rm);
            int count = modules == null ? 0 : Array.getLength(modules);
            if (count == 0) {
                opening = false;
                listener.onMessage("לא נמצא מקלט FM/AM חומרתי במכשיר");
                return;
            }

            Object selected = null;
            Class<?> mp = cls("android.hardware.radio.RadioManager$ModuleProperties");
            for (int i = 0; i < count; i++) {
                Object m = Array.get(modules, i);
                try {
                    int audioSources = ((Number)mp.getMethod("getNumAudioSources").invoke(m)).intValue();
                    int classId = ((Number)mp.getMethod("getClassId").invoke(m)).intValue();
                    int amfm = rmClass.getField("CLASS_AM_FM").getInt(null);
                    if (audioSources > 0 && classId == amfm) { selected = m; break; }
                } catch (Throwable ignored) {}
            }
            if (selected == null) selected = Array.get(modules, 0);

            moduleId = ((Number)mp.getMethod("getId").invoke(selected)).intValue();
            listener.onMessage("נמצא מודול רדיו חומרתי • מפעיל שמע…");

            Class<?> cbClass = cls("android.hardware.radio.RadioTuner$Callback");
            InvocationHandler ih = (proxy, method, args) -> {
                String n = method.getName();
                if ("onTuneFailed".equals(n)) {
                    handler.post(() -> listener.onMessage("הטיונר לא הצליח לנעול את התדר"));
                } else if ("onConfigurationChanged".equals(n)) {
                    handler.postDelayed(this::tune, 150);
                } else if ("onProgramInfoChanged".equals(n)) {
                    handler.post(this::readInfo);
                } else if ("onAntennaState".equals(n)) {
                    if (args != null && args.length > 0 && args[0] instanceof Boolean) {
                        boolean connected = (Boolean) args[0];
                        handler.post(() -> listener.onMessage(
                                connected ? "אנטנה פעילה • מוכן לקליטה" : "אנטנה לא מזוהה"));
                    }
                } else if ("onControlChanged".equals(n)) {
                    handler.post(() -> listener.onMessage("חיבור הרדיו השתנה"));
                }
                Class<?> rt = method.getReturnType();
                if (rt == boolean.class) return false;
                if (rt == int.class) return 0;
                if (rt == long.class) return 0L;
                return null;
            };

            callback = ProxyBuilder.forClass(cbClass)
                    .dexCache(context.getDir("radio_dex", Context.MODE_PRIVATE))
                    .markTrusted()
                    .handler(ih)
                    .build();

            Class<?> bandConfig = cls("android.hardware.radio.RadioManager$BandConfig");
            Method open = rmClass.getMethod("openTuner", int.class, bandConfig, boolean.class,
                    cbClass, Handler.class);
            tuner = open.invoke(rm, moduleId, null, true, callback, handler);

            if (tuner == null) {
                opening = false;
                listener.onMessage("לא ניתן לפתוח את הטיונר החומרתי");
                return;
            }

            opening = false;
            listener.onMessage("מקלט חומרתי פעיל • האוזניות משמשות כאנטנה במכשירים תומכים");
            handler.postDelayed(this::tune, 500);
        } catch (SecurityException e) {
            opening = false;
            listener.onMessage("גישה למקלט הרדיו חסומה במכשיר הזה");
        } catch (Throwable e) {
            opening = false;
            listener.onMessage("הפעלת מקלט הרדיו נכשלה במכשיר הזה");
        }
    }

    public void setBand(boolean isFm) {
        fm = isFm;
        current = isFm ? 99.5 : 1000;
        tune();
    }

    public void step(boolean up) {
        if (tuner == null) {
            listener.onMessage("אין מקלט חומרתי זמין");
            return;
        }
        try {
            Class<?> rt = cls("android.hardware.radio.RadioTuner");
            int dir = rt.getField(up ? "DIRECTION_UP" : "DIRECTION_DOWN").getInt(null);
            int result = ((Number)rt.getMethod("step", int.class, boolean.class)
                    .invoke(tuner, dir, true)).intValue();
            if (result != 0) listener.onMessage("לא ניתן לשנות תדר");
            handler.postDelayed(this::readInfo, 350);
        } catch (Throwable e) {
            listener.onMessage("לא ניתן לשנות תדר");
        }
    }

    public void findAlternative() {
        if (tuner == null) {
            listener.onMessage("אין מקלט חומרתי פעיל");
            return;
        }
        listener.onMessage("מחפש תדר חלופי עם קליטה טובה…");
        try {
            Class<?> rt = cls("android.hardware.radio.RadioTuner");
            int dir = rt.getField("DIRECTION_UP").getInt(null);
            rt.getMethod("scan", int.class, boolean.class).invoke(tuner, dir, true);
            handler.postDelayed(this::readInfo, 1800);
        } catch (Throwable e) {
            step(true);
        }
    }

    private void tune() {
        if (tuner == null) return;
        try {
            Class<?> ps = cls("android.hardware.radio.ProgramSelector");
            Class<?> rm = cls("android.hardware.radio.RadioManager");
            int band = rm.getField(fm ? "BAND_FM" : "BAND_AM").getInt(null);
            int frequencyKhz = fm ? (int)Math.round(current * 1000) : (int)Math.round(current);
            Object selector = ps.getMethod("createAmFmSelector", int.class, int.class)
                    .invoke(null, band, frequencyKhz);
            int result = ((Number)tuner.getClass().getMethod("tune", ps)
                    .invoke(tuner, selector)).intValue();
            if (result != 0) listener.onMessage("הטיונר דחה את התדר");
            handler.postDelayed(this::readInfo, 600);
        } catch (Throwable e) {
            listener.onMessage("תדר לא זמין");
        }
    }

    private void readInfo() {
        if (tuner == null) return;
        try {
            Class<?> rt = cls("android.hardware.radio.RadioTuner");
            Class<?> pi = cls("android.hardware.radio.RadioManager$ProgramInfo");
            Object arr = Array.newInstance(pi, 1);
            int result = ((Number)rt.getMethod("getProgramInformation", arr.getClass())
                    .invoke(tuner, arr)).intValue();
            Object info = Array.get(arr, 0);
            if (result == 0 && info != null) {
                Object selector = info.getClass().getMethod("getSelector").invoke(info);
                Class<?> ps = cls("android.hardware.radio.ProgramSelector");
                int type = ps.getField("IDENTIFIER_TYPE_AMFM_FREQUENCY").getInt(null);
                long khz = ((Number)ps.getMethod("getFirstId", int.class).invoke(selector, type)).longValue();
                fm = khz >= 60000;
                current = fm ? khz / 1000.0 : khz;
                int strength = ((Number)info.getClass().getMethod("getSignalStrength").invoke(info)).intValue();
                listener.onFrequency(current, fm, strength > 0);
            }
        } catch (Throwable ignored) {}
    }

    public void close() {
        handler.removeCallbacksAndMessages(null);
        if (tuner != null) {
            try { tuner.getClass().getMethod("close").invoke(tuner); } catch (Throwable ignored) {}
        }
        tuner = null;
        callback = null;
        moduleId = -1;
    }
}
