package com.example.sportchrono;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class AlarmRingService extends Service {
    static final String RING = "com.example.sportchrono.RING";
    static final String DISMISS = "com.example.sportchrono.DISMISS";
    static final String SNOOZE = "com.example.sportchrono.SNOOZE";
    private static final String CHANNEL = "alarms";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> stopSelf();
    private Ringtone ringtone;
    private Vibrator vibrator;
    private int alarmId = -1;

    static void channel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Alarmes", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null); // Sound is played by the service at the user's chosen volume.
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    static void showFallback(Context context) {
        channel(context);
        PendingIntent open = PendingIntent.getActivity(context, 201, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        context.getSystemService(NotificationManager.class).notify(72,
                new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_timer)
                        .setContentTitle("Alarme Sport Chrono")
                        .setContentText("Votre alarme est arrivée. Ouvrez l’application.")
                        .setContentIntent(open).setAutoCancel(true).build());
    }
    private PendingIntent action(String name, int code) {
        return PendingIntent.getService(this, code,
                new Intent(this, AlarmRingService.class).setAction(name).putExtra("alarm_id", alarmId),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    @Override public void onCreate() { super.onCreate(); channel(this); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String name = intent == null ? DISMISS : intent.getAction();
        if (SNOOZE.equals(name)) {
            AlarmScheduler.snooze(this, intent.getIntExtra("alarm_id", alarmId));
            stopSelf(); return START_NOT_STICKY;
        }
        if (!RING.equals(name)) { stopSelf(); return START_NOT_STICKY; }
        alarmId = intent.getIntExtra("alarm_id", -1);
        org.json.JSONObject alarm = AlarmStore.find(this, alarmId);
        if (alarm == null || !alarm.optBoolean("enabled", true)) { stopSelf(); return START_NOT_STICKY; }
        PendingIntent open = PendingIntent.getActivity(this, 202, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_timer).setContentTitle("C’est l’heure !")
                .setContentText(alarm.optString("label", "Alarme Sport Chrono"))
                .setContentIntent(open).setCategory(Notification.CATEGORY_ALARM).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Arrêter", action(DISMISS, 203)).build())
                .addAction(new Notification.Action.Builder(null, "Rappel 5 min", action(SNOOZE, 204)).build())
                .build();
        startForeground(71, notification);
        if (Signals.prefs(this).getBoolean("sound", true)) {
            try {
                String uri = alarm.optString("tone", "");
                ringtone = RingtoneManager.getRingtone(this, uri.isEmpty()
                        ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        : android.net.Uri.parse(uri));
                if (ringtone != null) {
                    if (Build.VERSION.SDK_INT >= 28) {
                        ringtone.setLooping(true);
                        ringtone.setVolume(Signals.prefs(this).getInt("volume", 75) / 100f);
                    }
                    ringtone.play();
                } else Signals.beep(this, true);
            } catch (RuntimeException ignored) { Signals.beep(this, true); }
        }
        if (Signals.prefs(this).getBoolean("vibrate", true)) {
            vibrator = getSystemService(Vibrator.class);
            if (vibrator != null) vibrator.vibrate(VibrationEffect.createWaveform(
                    new long[]{0, 500, 500}, 0));
        }
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, 60_000);
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(timeout);
        if (ringtone != null) ringtone.stop();
        if (vibrator != null) vibrator.cancel();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
