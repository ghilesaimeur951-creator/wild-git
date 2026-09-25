package com.example.sportchrono;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        int id = intent.getIntExtra("alarm_id", -1);
        org.json.JSONObject alarm = AlarmStore.find(context, id);
        if (alarm == null || !alarm.optBoolean("enabled", true)) return;
        if (AlarmScheduler.DAILY.equals(intent.getAction())) AlarmScheduler.schedule(context, alarm);
        try {
            context.startForegroundService(new Intent(context, AlarmRingService.class)
                    .setAction(AlarmRingService.RING).putExtra("alarm_id", id));
        } catch (RuntimeException ignored) {
            // On devices restricting background starts, show a high-priority notification.
            AlarmRingService.showFallback(context);
        }
    }
}
