package com.example.sportchrono;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Small local-only store. No account or network access is required. */
final class SessionStore {
    private static final String ACTIVE = "active_session", HISTORY = "history", PRESETS = "presets";
    static JSONArray array(Context c, String key) {
        try { return new JSONArray(Signals.prefs(c).getString(key, "[]")); }
        catch (JSONException ignored) { return new JSONArray(); }
    }
    static JSONArray history(Context c) { return array(c, HISTORY); }
    static JSONArray presets(Context c) { return array(c, PRESETS); }
    static void savePresets(Context c, JSONArray list) {
        Signals.prefs(c).edit().putString(PRESETS, list.toString()).apply();
    }
    static void save(Context c, SessionEngine s, long startedWall, long startedElapsed, JSONArray laps) {
        SessionEngine.State state = s.snapshot();
        JSONObject j = new JSONObject();
        try {
            j.put("mode", state.mode).put("phase", state.phase)
                    .put("prep", state.prepSeconds).put("work", state.workSeconds)
                    .put("rest", state.restSeconds).put("rounds", state.rounds)
                    .put("round", state.round).put("phaseEnd", state.phaseEnd)
                    .put("pauseAt", state.pauseAt).put("stopwatchStart", state.stopwatchStart)
                    .put("paused", state.paused).put("savedElapsed", SystemClock.elapsedRealtime())
                    .put("bootWall", System.currentTimeMillis() - SystemClock.elapsedRealtime())
                    .put("startedWall", startedWall).put("startedElapsed", startedElapsed)
                    .put("laps", laps == null ? new JSONArray() : laps);
            Signals.prefs(c).edit().putString(ACTIVE, j.toString()).commit();
        } catch (JSONException ignored) { }
    }
    static final class Recovery {
        SessionEngine engine; long startedWall, startedElapsed; JSONArray laps;
    }
    static Recovery load(Context c) {
        try {
            JSONObject j = new JSONObject(Signals.prefs(c).getString(ACTIVE, ""));
            long now = SystemClock.elapsedRealtime();
            long bootWall = System.currentTimeMillis() - now;
            if (now < j.getLong("savedElapsed") || Math.abs(bootWall - j.getLong("bootWall")) > 120_000)
                return null; // Monotonic timestamps are invalid after a reboot.
            SessionEngine.State s = new SessionEngine.State();
            s.mode = j.getString("mode"); s.phase = j.getString("phase");
            s.prepSeconds = j.getInt("prep"); s.workSeconds = j.getInt("work");
            s.restSeconds = j.getInt("rest"); s.rounds = j.getInt("rounds");
            s.round = j.getInt("round"); s.phaseEnd = j.getLong("phaseEnd");
            s.pauseAt = j.getLong("pauseAt"); s.stopwatchStart = j.getLong("stopwatchStart");
            s.paused = j.getBoolean("paused");
            Recovery r = new Recovery(); r.engine = SessionEngine.restore(s, now);
            r.startedWall = j.getLong("startedWall"); r.startedElapsed = j.getLong("startedElapsed");
            r.laps = j.optJSONArray("laps"); if (r.laps == null) r.laps = new JSONArray();
            return r;
        } catch (JSONException | IllegalArgumentException ignored) { return null; }
    }
    static boolean hasSaved(Context c) { return Signals.prefs(c).contains(ACTIVE); }
    static void clear(Context c) { Signals.prefs(c).edit().remove(ACTIVE).commit(); }
    static void record(Context c, SessionEngine s, long startedWall, long startedElapsed,
                       boolean completed, JSONArray laps) {
        if (s == null) return;
        try {
            JSONObject entry = new JSONObject()
                    .put("mode", s.mode.name()).put("date", startedWall)
                    .put("seconds", Math.max(0, (SystemClock.elapsedRealtime() - startedElapsed) / 1000))
                    .put("rounds", s.round).put("completed", completed)
                    .put("laps", laps == null ? new JSONArray() : laps);
            JSONArray old = history(c), next = new JSONArray(); next.put(entry);
            for (int i = 0; i < Math.min(old.length(), 49); i++) next.put(old.get(i));
            Signals.prefs(c).edit().putString(HISTORY, next.toString()).apply();
        } catch (JSONException ignored) { }
    }
}
