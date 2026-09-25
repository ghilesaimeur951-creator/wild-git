package com.example.sportchrono;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class WidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        refresh(context);
    }
    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
        if (ids.length == 0) return;
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
        SessionEngine session = SessionService.current;
        views.setTextViewText(R.id.widget_status, WorkoutService.current != null
                ? "Séance street workout en cours"
                : session == null ? "Prêt à bouger"
                : session.paused ? "Séance en pause" : "Séance en cours · tour " + session.round);
        Intent start = new Intent(context, SessionService.class).setAction(SessionService.ACTION_START)
                .putExtra("mode", SessionEngine.Mode.INTERVAL.name())
                .putExtra("prep", 5).putExtra("work", 30).putExtra("rest", 10).putExtra("rounds", 4);
        views.setOnClickPendingIntent(R.id.widget_start, PendingIntent.getForegroundService(context, 401, start,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Intent chrono = new Intent(context, SessionService.class).setAction(SessionService.ACTION_START)
                .putExtra("mode", SessionEngine.Mode.STOPWATCH.name())
                .putExtra("prep", 0).putExtra("work", 1).putExtra("rest", 0).putExtra("rounds", 1);
        views.setOnClickPendingIntent(R.id.widget_chrono, PendingIntent.getForegroundService(context, 402, chrono,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        views.setOnClickPendingIntent(R.id.widget_open, PendingIntent.getActivity(context, 403,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(ids, views);
    }
}
