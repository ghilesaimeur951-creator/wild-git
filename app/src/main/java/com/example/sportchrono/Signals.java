package com.example.sportchrono;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

final class Signals {
    static final String PREFS = "sport_chrono";
    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    static void beep(Context context, boolean finalCue) {
        SharedPreferences p = prefs(context);
        if (p.getBoolean("sound", true)) {
            AudioCue.hold(context, finalCue ? 1200 : 400);
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_ALARM, p.getInt("volume", 75));
            tone.startTone(finalCue ? ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                    : ToneGenerator.TONE_PROP_BEEP, finalCue ? 900 : 120);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(tone::release,
                    finalCue ? 1100 : 300);
        }
        if (p.getBoolean("vibrate", true)) {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(finalCue ? 600 : 90,
                        VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }
}
