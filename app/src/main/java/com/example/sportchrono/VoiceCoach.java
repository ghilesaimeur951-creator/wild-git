package com.example.sportchrono;

import android.content.Context;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.os.Handler;
import android.os.Looper;
import java.util.Locale;

final class VoiceCoach {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready;
    private int sequence;
    private String pending;
    VoiceCoach(Context context) {
        this.context = context;
        if (!Signals.prefs(context).getBoolean("voice", true)) return;
        try {
            tts = new TextToSpeech(context.getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    int language = tts.setLanguage(Locale.FRANCE);
                    ready = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED;
                    tts.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) { }
                        @Override public void onDone(String id) { finish(id); }
                        @Override public void onError(String id) { finish(id); }
                    });
                    if (ready && pending != null) {
                        String words = pending; pending = null;
                        main.post(() -> say(words));
                    }
                }
            });
        } catch (RuntimeException ignored) { ready = false; }
    }
    private void finish(String id) {
        main.post(() -> { if (id.equals(String.valueOf(sequence))) AudioCue.release(); });
    }
    void say(String words) {
        if (!Signals.prefs(context).getBoolean("voice", true)) return;
        if (!ready) { pending = words; return; }
        int id = ++sequence;
        AudioCue.hold(context, 5000);
        tts.speak(words, TextToSpeech.QUEUE_FLUSH, null, String.valueOf(id));
    }
    void shutdown() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        AudioCue.release();
    }
}
