package com.example.sportchrono;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class AlarmStore {
    private static final String KEY = "alarm_list";
    static JSONArray list(Context context) {
        migrate(context);
        try { return new JSONArray(Signals.prefs(context).getString(KEY, "[]")); }
        catch (JSONException ignored) { return new JSONArray(); }
    }
    private static void migrate(Context c) {
        if (Signals.prefs(c).getBoolean("alarms_migrated", false)) return;
        JSONArray old = new JSONArray();
        if (Signals.prefs(c).getBoolean("alarm_enabled", false)) {
            try { old.put(new JSONObject().put("id", 1).put("hour", Signals.prefs(c).getInt("hour", 7))
                    .put("minute", Signals.prefs(c).getInt("minute", 0)).put("days", 127)
                    .put("enabled", true).put("label", "Alarme").put("tone", "")); }
            catch (JSONException ignored) { }
        }
        Signals.prefs(c).edit().putString(KEY, old.toString()).putBoolean("alarms_migrated", true).commit();
    }
    static JSONObject find(Context c, int id) {
        JSONArray a = list(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject j = a.optJSONObject(i);
            if (j != null && j.optInt("id") == id) return j;
        }
        return null;
    }
    static int nextId(Context c) {
        int id = Signals.prefs(c).getInt("next_alarm_id", 2);
        Signals.prefs(c).edit().putInt("next_alarm_id", id + 1).apply();
        return id;
    }
    static void upsert(Context c, JSONObject alarm) {
        JSONArray old = list(c), next = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < old.length(); i++) {
            JSONObject j = old.optJSONObject(i);
            if (j != null && j.optInt("id") == alarm.optInt("id")) {
                next.put(alarm); replaced = true;
            } else if (j != null) next.put(j);
        }
        if (!replaced) next.put(alarm);
        Signals.prefs(c).edit().putString(KEY, next.toString()).commit();
    }
    static void delete(Context c, int id) {
        JSONArray old = list(c), next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject j = old.optJSONObject(i);
            if (j != null && j.optInt("id") != id) next.put(j);
        }
        Signals.prefs(c).edit().putString(KEY, next.toString()).commit();
    }
    static String daysLabel(int mask) {
        if (mask == 127) return "Tous les jours";
        String[] labels = {"Di", "Lu", "Ma", "Me", "Je", "Ve", "Sa"};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 7; i++) if ((mask & (1 << i)) != 0) {
            if (result.length() > 0) result.append(" · ");
            result.append(labels[i]);
        }
        return result.toString();
    }
}
