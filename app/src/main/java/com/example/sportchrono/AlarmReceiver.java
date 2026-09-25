package com.example.sportchrono;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Signals.prefs(context).getBoolean("alarm_enabled", false)) return;
        if (AlarmScheduler.DAILY.equals(intent.getAction())) AlarmScheduler.scheduleDaily(context);
        try {
            context.startForegroundService(new Intent(context, AlarmRingService.class)
                    .setAction(AlarmRingService.RING));
        } catch (RuntimeException ignored) {
            // On devices restricting background starts, show a high-priority notification.
            AlarmRingService.showFallback(context);
        }
    }
}
