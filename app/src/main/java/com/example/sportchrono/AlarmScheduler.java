package com.example.sportchrono;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

final class AlarmScheduler {
    static final String DAILY = "com.example.sportchrono.DAILY_ALARM";
    static final String SNOOZE = "com.example.sportchrono.SNOOZE_ALARM";
    static boolean exactAllowed(Context c) {
        return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }
    private static PendingIntent pending(Context c, String action, int id) {
        return PendingIntent.getBroadcast(c, SNOOZE.equals(action) ? id + 100_000 : id,
                new Intent(c, AlarmReceiver.class).setAction(action).putExtra("alarm_id", id),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static void cancel(Context c, int id) {
        AlarmManager manager = c.getSystemService(AlarmManager.class);
        manager.cancel(pending(c, DAILY, id)); manager.cancel(pending(c, SNOOZE, id));
    }
    static long nextTime(JSONObject alarm) {
        Calendar now = Calendar.getInstance();
        for (int n = 0; n <= 7; n++) {
            Calendar at = (Calendar) now.clone();
            at.add(Calendar.DAY_OF_YEAR, n);
            at.set(Calendar.HOUR_OF_DAY, alarm.optInt("hour", 7));
            at.set(Calendar.MINUTE, alarm.optInt("minute", 0));
            at.set(Calendar.SECOND, 0); at.set(Calendar.MILLISECOND, 0);
            if ((alarm.optInt("days", 127) & (1 << (at.get(Calendar.DAY_OF_WEEK) - 1))) != 0
                    && at.after(now)) return at.getTimeInMillis();
        }
        throw new IllegalArgumentException("Aucun jour sélectionné");
    }
    static void scheduleAll(Context c) {
        JSONArray list = AlarmStore.list(c);
        for (int i = 0; i < list.length(); i++) {
            JSONObject alarm = list.optJSONObject(i);
            if (alarm != null) schedule(c, alarm);
        }
    }
    static void schedule(Context c, JSONObject alarm) {
        int id = alarm.optInt("id");
        AlarmManager manager = c.getSystemService(AlarmManager.class);
        PendingIntent trigger = pending(c, DAILY, id);
        manager.cancel(trigger);
        if (!alarm.optBoolean("enabled", true) || alarm.optInt("days", 0) == 0) return;
        scheduleAt(c, nextTime(alarm), trigger);
    }
    static void snooze(Context c, int id) {
        JSONObject alarm = AlarmStore.find(c, id);
        if (alarm == null || !alarm.optBoolean("enabled", true)) return;
        PendingIntent trigger = pending(c, SNOOZE, id);
        c.getSystemService(AlarmManager.class).cancel(trigger);
        scheduleAt(c, System.currentTimeMillis() + 300_000L, trigger);
    }
    private static void scheduleAt(Context c, long time, PendingIntent trigger) {
        AlarmManager manager = c.getSystemService(AlarmManager.class);
        if (exactAllowed(c)) {
            PendingIntent show = PendingIntent.getActivity(c, 300,
                    new Intent(c, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(time, show), trigger);
        } else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, trigger);
    }
    static Intent permissionSettings(Context c) {
        return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(android.net.Uri.parse("package:" + c.getPackageName()));
    }
}
