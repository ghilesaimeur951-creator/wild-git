package com.example.sportchrono;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import org.json.JSONArray;

public class SessionService extends Service {
    static final String ACTION_START = "com.example.sportchrono.START";
    static final String ACTION_PAUSE = "com.example.sportchrono.PAUSE";
    static final String ACTION_RESUME = "com.example.sportchrono.RESUME";
    static final String ACTION_STOP = "com.example.sportchrono.STOP";
    static final String ACTION_RESTORE = "com.example.sportchrono.RESTORE";
    static final String ACTION_LAP = "com.example.sportchrono.LAP";
    static final String CHANNEL = "session";
    public static SessionEngine current;
    public static JSONArray laps = new JSONArray();
    private long startedWall, startedElapsed;
    private VoiceCoach coach;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private int lastNotificationSecond = -1;
    private final SessionEngine.Listener listener = new SessionEngine.Listener() {
        public void count(int seconds) {
            Signals.beep(SessionService.this, false);
            if (coach != null) coach.say(String.valueOf(seconds));
        }
        public void phaseChanged() {
            Signals.beep(SessionService.this, false);
            announce(); persist(); notifyState(); WidgetProvider.refresh(SessionService.this);
        }
        public void completed() {
            Signals.beep(SessionService.this, true);
            if (coach != null) coach.say("Séance terminée");
            SessionStore.record(SessionService.this, current, startedWall, startedElapsed, true, laps);
            SessionStore.clear(SessionService.this);
            releaseWakeLock();
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.notify(42, new Notification.Builder(SessionService.this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_timer).setContentTitle("Séance terminée")
                    .setContentText("Bravo ! Tous les tours sont finis.")
                    .setContentIntent(openApp()).setAutoCancel(true).build());
            stopForeground(STOP_FOREGROUND_REMOVE);
            current = null;
            WidgetProvider.refresh(SessionService.this);
            stopSelf();
        }
    };
    private final Runnable ticker = new Runnable() {
        public void run() {
            if (current == null) return;
            long now = SystemClock.elapsedRealtime();
            current.tick(now, listener);
            if (current == null) return;
            int second = (int) (current.displayMs(now) / 1000L);
            if (lastNotificationSecond != second) { lastNotificationSecond = second; notifyState(); }
            handler.postDelayed(this, 100);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Séance en cours", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_RESTORE : intent.getAction();
        if (!ACTION_START.equals(action) && current == null && SessionStore.hasSaved(this)) {
            restoreSaved();
        }
        if (ACTION_START.equals(action)) {
            if (WorkoutService.current != null || WorkoutStore.hasActive(this))
                startService(new Intent(this, WorkoutService.class).setAction(WorkoutService.STOP));
            handler.removeCallbacks(ticker);
            String value = intent.getStringExtra("mode");
            SessionEngine.Mode mode;
            try { mode = SessionEngine.Mode.valueOf(value); }
            catch (Exception ignored) { stopSelf(); return START_NOT_STICKY; }
            try {
                current = new SessionEngine(mode, intent.getIntExtra("prep", 0),
                        intent.getIntExtra("work", 1), intent.getIntExtra("rest", 0),
                        intent.getIntExtra("rounds", 1), SystemClock.elapsedRealtime());
            } catch (IllegalArgumentException ignored) { stopSelf(); return START_NOT_STICKY; }
            laps = new JSONArray(); startedWall = System.currentTimeMillis();
            startedElapsed = SystemClock.elapsedRealtime();
            if (coach != null) coach.shutdown();
            coach = new VoiceCoach(this);
            startForeground(41, notification());
            acquireWakeLock();
            persist();
            final VoiceCoach newCoach = coach;
            handler.postDelayed(() -> { if (coach == newCoach && current != null) announce(); }, 650);
            WidgetProvider.refresh(this);
            lastNotificationSecond = -1;
            handler.post(ticker);
        } else if (ACTION_RESTORE.equals(action)) {
            if (current == null) { stopSelf(); return START_NOT_STICKY; }
        } else if (ACTION_STOP.equals(action)) {
            if (current != null && current.mode == SessionEngine.Mode.STOPWATCH)
                SessionStore.record(this, current, startedWall, startedElapsed, true, laps);
            SessionStore.clear(this);
            current = null;
            handler.removeCallbacks(ticker);
            releaseWakeLock();
            stopForeground(STOP_FOREGROUND_REMOVE);
            WidgetProvider.refresh(this);
            stopSelf();
        } else if (current != null && ACTION_PAUSE.equals(action)) {
            current.pause(SystemClock.elapsedRealtime());
            persist(); releaseWakeLock(); notifyState(); WidgetProvider.refresh(this);
        } else if (current != null && ACTION_RESUME.equals(action)) {
            current.resume(SystemClock.elapsedRealtime());
            persist(); acquireWakeLock(); notifyState(); WidgetProvider.refresh(this);
        } else if (current != null && ACTION_LAP.equals(action) && current.mode == SessionEngine.Mode.STOPWATCH) {
            laps.put(current.displayMs(SystemClock.elapsedRealtime()));
            persist();
        }
        return START_STICKY;
    }
    private void restoreSaved() {
        SessionStore.Recovery recovery = SessionStore.load(this);
        if (recovery == null) { SessionStore.clear(this); return; }
        current = recovery.engine; laps = recovery.laps;
        startedWall = recovery.startedWall; startedElapsed = recovery.startedElapsed;
        coach = new VoiceCoach(this);
        startForeground(41, notification());
        if (!current.paused) acquireWakeLock();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        WidgetProvider.refresh(this);
    }
    private void persist() {
        if (current != null) SessionStore.save(this, current, startedWall, startedElapsed, laps);
    }
    private void announce() {
        if (coach == null || current == null || current.finished) return;
        coach.say(current.phase == SessionEngine.Phase.PREPARE ? "Préparez-vous"
                : current.phase == SessionEngine.Phase.REST ? "Repos" : "Effort");
    }
    private void acquireWakeLock() {
        if (wakeLock == null) wakeLock = getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SportChrono:Session");
        if (!wakeLock.isHeld()) wakeLock.acquire();
    }
    private void releaseWakeLock() { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
    private PendingIntent openApp() {
        return PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private PendingIntent action(String type, int id) {
        return PendingIntent.getService(this, id, new Intent(this, SessionService.class).setAction(type),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification() {
        SessionEngine s = current;
        String phase = s.mode == SessionEngine.Mode.STOPWATCH ? "Chronomètre"
                : s.mode == SessionEngine.Mode.TIMER ? "Minuteur"
                : (s.phase == SessionEngine.Phase.PREPARE ? "Préparation"
                : s.phase == SessionEngine.Phase.WORK ? "Effort" : "Repos") + " · tour " + s.round + "/" + s.rounds;
        long ms = s.displayMs(SystemClock.elapsedRealtime());
        long seconds = s.mode == SessionEngine.Mode.STOPWATCH ? ms / 1000 : (ms + 999) / 1000;
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_timer).setContentTitle(phase)
                .setContentText(String.format(java.util.Locale.FRANCE, "%02d:%02d", seconds / 60, seconds % 60)
                        + (s.paused ? " · en pause" : ""))
                .setContentIntent(openApp()).setOnlyAlertOnce(true).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, s.paused ? "Reprendre" : "Pause",
                        action(s.paused ? ACTION_RESUME : ACTION_PAUSE, 2)).build())
                .addAction(new Notification.Action.Builder(null, "Arrêter", action(ACTION_STOP, 3)).build());
        return builder.build();
    }
    private void notifyState() {
        if (current != null) getSystemService(NotificationManager.class).notify(41, notification());
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        releaseWakeLock(); current = null;
        if (coach != null) coach.shutdown();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
