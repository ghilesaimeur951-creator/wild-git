package com.example.sportchrono;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

public class WorkoutService extends Service {
    static final String START = "com.example.sportchrono.WORKOUT_START";
    static final String RESTORE = "com.example.sportchrono.WORKOUT_RESTORE";
    static final String PAUSE = "com.example.sportchrono.WORKOUT_PAUSE";
    static final String RESUME = "com.example.sportchrono.WORKOUT_RESUME";
    static final String DONE = "com.example.sportchrono.WORKOUT_DONE";
    static final String SKIP = "com.example.sportchrono.WORKOUT_SKIP";
    static final String PREVIOUS = "com.example.sportchrono.WORKOUT_PREVIOUS";
    static final String STOP = "com.example.sportchrono.WORKOUT_STOP";
    private static final String CHANNEL = "workout";
    static WorkoutEngine current;
    private long startedWall, startedElapsed;
    private PowerManager.WakeLock wakeLock;
    private VoiceCoach coach;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable delayedStop = () -> {
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    };
    private int notifiedSecond = -1;
    private final WorkoutEngine.Listener listener = new WorkoutEngine.Listener() {
        public void transition(WorkoutEngine.Step step) {
            Signals.beep(WorkoutService.this, false);
            announce(step); persist(); notifyWorkout(); WidgetProvider.refresh(WorkoutService.this);
        }
        public void countdown(int seconds) {
            Signals.beep(WorkoutService.this, false);
            if (coach != null) coach.say(String.valueOf(seconds));
        }
        public void finish(boolean completed) {
            WorkoutEngine ended = current;
            if (ended == null) return;
            WorkoutStore.record(WorkoutService.this, ended, startedWall, startedElapsed, completed);
            WorkoutStore.clearActive(WorkoutService.this);
            if (completed) {
                Signals.beep(WorkoutService.this, true);
                if (coach != null) coach.say("Séance terminée");
                getSystemService(NotificationManager.class).notify(45,
                        new Notification.Builder(WorkoutService.this, CHANNEL)
                                .setSmallIcon(R.drawable.ic_timer)
                                .setContentTitle("Séance terminée")
                                .setContentText(ended.plan.name + " · voir le récapitulatif")
                                .setContentIntent(openApp()).setAutoCancel(true).build());
            }
            current = null;
            handler.removeCallbacks(ticker);
            releaseWake(); WidgetProvider.refresh(WorkoutService.this);
            if (completed) handler.postDelayed(delayedStop, 2200); // Finish the spoken announcement.
            else { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
        }
    };
    private final Runnable ticker = new Runnable() {
        public void run() {
            WorkoutEngine engine = current; if (engine == null) return;
            long now = SystemClock.elapsedRealtime();
            engine.tick(now, listener);
            if (current == null) return;
            int second = (int) (engine.displayMs(now) / 1000);
            if (second != notifiedSecond) { notifiedSecond = second; notifyWorkout(); }
            handler.postDelayed(this, 100);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "Séance street workout", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? RESTORE : intent.getAction();
        if (!START.equals(action) && current == null && WorkoutStore.hasActive(this)) restoreSaved();
        long now = SystemClock.elapsedRealtime();
        if (START.equals(action)) {
            handler.removeCallbacks(delayedStop);
            if (current != null) WorkoutStore.record(this, current, startedWall, startedElapsed, false);
            handler.removeCallbacks(ticker);
            try {
                WorkoutPlan plan = WorkoutStore.fromJson(new JSONObject(intent.getStringExtra("plan")));
                current = new WorkoutEngine(plan, now);
            } catch (JSONException | IllegalArgumentException error) {
                WorkoutStore.clearActive(this); stopSelf(); return START_NOT_STICKY;
            }
            startedWall = System.currentTimeMillis(); startedElapsed = now;
            if (coach != null) coach.shutdown(); coach = new VoiceCoach(this);
            startForeground(44, notification()); acquireWake(); persist();
            final VoiceCoach voice = coach;
            handler.postDelayed(() -> { if (voice == coach && current != null) announce(current.step()); }, 500);
            WidgetProvider.refresh(this); notifiedSecond = -1;
            handler.post(ticker);
        } else if (RESTORE.equals(action)) {
            if (current == null) { stopSelf(); return START_NOT_STICKY; }
        } else if (current == null) { stopSelf(); return START_NOT_STICKY;
        } else if (PAUSE.equals(action)) {
            current.pause(now); persist(); releaseWake(); notifyWorkout();
        } else if (RESUME.equals(action)) {
            current.resume(now); persist(); acquireWake(); notifyWorkout();
        } else if (DONE.equals(action)) {
            current.complete(now, intent.getIntExtra("actual", 0),
                    intent.getIntExtra("left", -1), intent.getIntExtra("right", -1), listener);
        } else if (SKIP.equals(action)) current.skip(now, listener);
        else if (PREVIOUS.equals(action)) { current.previous(now, listener); persist(); }
        else if (STOP.equals(action)) current.stop(listener);
        return START_STICKY;
    }
    private void restoreSaved() {
        WorkoutStore.Recovery recovered = WorkoutStore.recover(this);
        if (recovered == null) { WorkoutStore.clearActive(this); return; }
        current = recovered.engine; startedWall = recovered.startedWall;
        startedElapsed = recovered.startedElapsed;
        coach = new VoiceCoach(this);
        startForeground(44, notification());
        if (!current.paused()) acquireWake();
        handler.removeCallbacks(ticker); handler.post(ticker);
        WidgetProvider.refresh(this);
    }
    private void persist() {
        if (current != null) WorkoutStore.saveActive(this, current, startedWall, startedElapsed);
    }
    private void announce(WorkoutEngine.Step step) {
        if (step == null || coach == null || current == null) return;
        String words;
        switch (step.kind) {
            case WORK:
                words = current.plan.exercises.get(step.exerciseIndex).name
                        + ". Série " + step.series; break;
            case REST_SERIES: case REST_EXERCISE: case REST_CIRCUIT: case REST_EMOM:
                words = "Repos"; break;
            case WARMUP: words = "Échauffement"; break;
            case COOLDOWN: words = "Retour au calme"; break;
            default: words = "Préparez-vous";
        }
        coach.say(words);
    }
    private void acquireWake() {
        if (wakeLock == null) wakeLock = getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SportChrono:StreetWorkout");
        if (!wakeLock.isHeld()) wakeLock.acquire();
    }
    private void releaseWake() { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
    private PendingIntent openApp() {
        return PendingIntent.getActivity(this, 440, new Intent(this, WorkoutActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private PendingIntent control(String type, int request) {
        return PendingIntent.getService(this, request, new Intent(this, WorkoutService.class).setAction(type),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification() {
        WorkoutEngine e = current; WorkoutEngine.Step s = e.step();
        String title = s.kind == WorkoutEngine.Kind.WORK
                ? e.plan.exercises.get(s.exerciseIndex).name + " · " + s.series + "/" + s.seriesTotal
                : s.kind == WorkoutEngine.Kind.REST_EMOM ? "EMOM · repos"
                : s.isRest() ? "Repos" : s.kind == WorkoutEngine.Kind.PREPARE ? "Préparation"
                : s.kind == WorkoutEngine.Kind.WARMUP ? "Échauffement" : "Retour au calme";
        long ms = e.displayMs(SystemClock.elapsedRealtime());
        long secs = s.kind == WorkoutEngine.Kind.WORK && s.durationMs == 0
                ? ms / 1000 : (ms + 999) / 1000;
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(title).setContentText(String.format(Locale.FRANCE, "%02d:%02d",
                        secs / 60, secs % 60) + (e.paused() ? " · pause" : " · ouvrir pour valider"))
                .setContentIntent(openApp()).setOnlyAlertOnce(true).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, e.paused() ? "Reprendre" : "Pause",
                        control(e.paused() ? RESUME : PAUSE, 441)).build())
                .build();
    }
    private void notifyWorkout() {
        if (current != null) getSystemService(NotificationManager.class).notify(44, notification());
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(ticker); handler.removeCallbacks(delayedStop);
        releaseWake(); current = null;
        if (coach != null) coach.shutdown();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
