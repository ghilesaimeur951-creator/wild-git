package com.example.sportchrono;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import java.util.Calendar;

final class AlarmScheduler {
    static final String DAILY = "com.example.sportchrono.DAILY_ALARM";
    static final String SNOOZE = "com.example.sportchrono.SNOOZE_ALARM";

    static boolean exactAllowed(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        return Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms();
    }
    static PendingIntent pending(Context context, String action, int id) {
        return PendingIntent.getBroadcast(context, id,
                new Intent(context, AlarmReceiver.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static void cancel(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        manager.cancel(pending(context, DAILY, 101));
        manager.cancel(pending(context, SNOOZE, 102));
    }
    static long nextTime(Context context) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, Signals.prefs(context).getInt("hour", 7));
        c.set(Calendar.MINUTE, Signals.prefs(context).getInt("minute", 0));
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }
    static void scheduleDaily(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        PendingIntent pending = pending(context, DAILY, 101);
        manager.cancel(pending);
        if (!Signals.prefs(context).getBoolean("alarm_enabled", false)) return;
        schedule(context, nextTime(context), pending);
    }
    static void snooze(Context context) {
        if (!Signals.prefs(context).getBoolean("alarm_enabled", false)) return;
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        PendingIntent pending = pending(context, SNOOZE, 102);
        manager.cancel(pending);
        schedule(context, System.currentTimeMillis() + 5 * 60_000L, pending);
    }
    private static void schedule(Context context, long time, PendingIntent pending) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (exactAllowed(context)) {
            PendingIntent show = PendingIntent.getActivity(context, 103,
                    new Intent(context, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(time, show), pending);
        } else {
            // The alarm still works without special access, but may be delayed by Android.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending);
        }
    }
    static Intent permissionSettings(Context context) {
        return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(android.net.Uri.parse("package:" + context.getPackageName()));
    }
}
