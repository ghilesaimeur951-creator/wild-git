package com.example.sportchrono;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

/** Briefly lowers other audio during a cue; never changes the system volume. */
final class AudioCue {
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final AudioFocusRequest request = new AudioFocusRequest.Builder(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(change -> { }).build();
    private static AudioManager manager;
    private static final Runnable abandon = () -> {
        if (manager != null) manager.abandonAudioFocusRequest(request);
        manager = null;
    };
    static void hold(Context context, long millis) {
        if (!Signals.prefs(context).getBoolean("duck", true)) return;
        if (manager == null) manager = context.getSystemService(AudioManager.class);
        handler.removeCallbacks(abandon);
        manager.requestAudioFocus(request);
        handler.postDelayed(abandon, millis);
    }
    static void release() { handler.removeCallbacks(abandon); abandon.run(); }
}
