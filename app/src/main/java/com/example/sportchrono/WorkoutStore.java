package com.example.sportchrono;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

final class WorkoutStore {
    private static final String DRAFT = "workout_draft", TEMPLATES = "workout_templates";
    private static final String ACTIVE = "workout_active", HISTORY = "workout_history";
    static JSONObject planJson(WorkoutPlan p) throws JSONException {
        JSONObject j = new JSONObject().put("id", p.id).put("name", p.name).put("mode", p.mode.name())
                .put("prep", p.prepSec).put("warmup", p.warmupSec).put("cooldown", p.cooldownSec)
                .put("sets", p.defaultSets).put("reps", p.defaultReps).put("cap", p.defaultCapSec)
                .put("restSeries", p.restSeriesSec).put("restExercise", p.restExerciseSec)
                .put("emomInterval", p.emomIntervalSec).put("emomCycles", p.emomCycles)
                .put("pyramidStart", p.pyramidStart).put("pyramidMax", p.pyramidMax)
                .put("pyramidStep", p.pyramidStep).put("pyramidRest", p.pyramidRestSec)
                .put("pyramidDirection", p.pyramidDirection.name()).put("repeatPeak", p.repeatPeak)
                .put("circuitRounds", p.circuitRounds).put("circuitRest", p.circuitRestSec)
                .put("amrapDuration", p.amrapDurationSec);
        JSONArray exercises = new JSONArray();
        for (WorkoutPlan.Exercise e : p.exercises) exercises.put(new JSONObject()
                .put("catalogId", e.catalogId).put("name", e.name).put("category", e.category)
                .put("notes", e.notes).put("sets", e.sets).put("reps", e.reps)
                .put("cap", e.capSec).put("rest", e.restSec).put("perSide", e.perSide)
                .put("load", e.load.name()).put("kg", e.kilograms));
        return j.put("exercises", exercises);
    }
    static WorkoutPlan fromJson(JSONObject j) {
        WorkoutPlan p = new WorkoutPlan();
        p.id = j.optString("id", ""); p.name = j.optString("name", "Ma séance");
        try { p.mode = WorkoutPlan.Mode.valueOf(j.optString("mode", "STANDARD")); }
        catch (IllegalArgumentException ignored) { }
        p.prepSec = j.optInt("prep", 5); p.warmupSec = j.optInt("warmup");
        p.cooldownSec = j.optInt("cooldown"); p.defaultSets = j.optInt("sets", 4);
        p.defaultReps = j.optInt("reps"); p.defaultCapSec = j.optInt("cap", 30);
        p.restSeriesSec = j.optInt("restSeries", 60); p.restExerciseSec = j.optInt("restExercise", 120);
        p.emomIntervalSec = j.optInt("emomInterval", 60); p.emomCycles = j.optInt("emomCycles", 10);
        p.pyramidStart = j.optInt("pyramidStart", 2); p.pyramidMax = j.optInt("pyramidMax", 10);
        p.pyramidStep = j.optInt("pyramidStep", 2); p.pyramidRestSec = j.optInt("pyramidRest", 60);
        try { p.pyramidDirection = WorkoutPlan.PyramidDirection.valueOf(
                j.optString("pyramidDirection", "BOTH")); } catch (IllegalArgumentException ignored) { }
        p.repeatPeak = j.optBoolean("repeatPeak"); p.circuitRounds = j.optInt("circuitRounds", 4);
        p.circuitRestSec = j.optInt("circuitRest", 90); p.amrapDurationSec = j.optInt("amrapDuration", 600);
        JSONArray list = j.optJSONArray("exercises");
        if (list != null) for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i); if (item == null) continue;
            WorkoutPlan.Exercise e = new WorkoutPlan.Exercise();
            e.catalogId = item.optString("catalogId"); e.name = item.optString("name");
            e.category = item.optString("category"); e.notes = item.optString("notes");
            e.sets = item.optInt("sets", -1); e.reps = item.optInt("reps", -1);
            e.capSec = item.optInt("cap", -1); e.restSec = item.optInt("rest", -1);
            e.perSide = item.optBoolean("perSide");
            try { e.load = WorkoutPlan.Load.valueOf(item.optString("load", "NONE")); }
            catch (IllegalArgumentException ignored) { }
            e.kilograms = item.optDouble("kg", 0);
            p.exercises.add(e);
        }
        return p;
    }
    static JSONArray array(Context c, String key) {
        try { return new JSONArray(Signals.prefs(c).getString(key, "[]")); }
        catch (JSONException ignored) { return new JSONArray(); }
    }
    static void saveDraft(Context c, WorkoutPlan p) {
        try { Signals.prefs(c).edit().putString(DRAFT, planJson(p).toString()).apply(); }
        catch (JSONException ignored) { }
    }
    static WorkoutPlan draft(Context c) {
        try { return fromJson(new JSONObject(Signals.prefs(c).getString(DRAFT, ""))); }
        catch (JSONException ignored) { return new WorkoutPlan(); }
    }
    static JSONArray templates(Context c) { return array(c, TEMPLATES); }
    static void upsertTemplate(Context c, WorkoutPlan p) {
        if (p.id.isEmpty()) p.id = UUID.randomUUID().toString();
        try {
            JSONArray old = templates(c), next = new JSONArray(); boolean replaced = false;
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item != null && p.id.equals(item.optString("id"))) {
                    next.put(planJson(p)); replaced = true;
                } else if (item != null) next.put(item);
            }
            if (!replaced) next.put(planJson(p));
            Signals.prefs(c).edit().putString(TEMPLATES, next.toString()).commit();
        } catch (JSONException ignored) { }
    }
    static void deleteTemplate(Context c, String id) {
        JSONArray old = templates(c), next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && !id.equals(item.optString("id"))) next.put(item);
        }
        Signals.prefs(c).edit().putString(TEMPLATES, next.toString()).commit();
    }
    static JSONArray history(Context c) { return array(c, HISTORY); }
    static JSONObject eventJson(WorkoutEngine.Event e) throws JSONException {
        return new JSONObject().put("step", e.stepIndex).put("cycle", e.cycle)
                .put("exercise", e.exerciseIndex).put("series", e.series)
                .put("target", e.target).put("actual", e.actual)
                .put("left", e.left).put("right", e.right)
                .put("elapsed", e.elapsedMs).put("result", e.result.name());
    }
    static WorkoutEngine.Event eventFromJson(JSONObject j) {
        WorkoutEngine.Event e = new WorkoutEngine.Event();
        e.stepIndex = j.optInt("step"); e.cycle = j.optInt("cycle", 1);
        e.exerciseIndex = j.optInt("exercise"); e.series = j.optInt("series");
        e.target = j.optInt("target"); e.actual = j.optInt("actual", -1);
        e.left = j.optInt("left", -1); e.right = j.optInt("right", -1);
        e.elapsedMs = j.optLong("elapsed");
        try { e.result = WorkoutEngine.Result.valueOf(j.optString("result")); }
        catch (IllegalArgumentException ignored) { e.result = WorkoutEngine.Result.SKIPPED; }
        return e;
    }
    static void saveActive(Context c, WorkoutEngine engine, long startedWall, long startedElapsed) {
        WorkoutEngine.State s = engine.snapshot();
        try {
            JSONArray events = new JSONArray();
            for (WorkoutEngine.Event e : s.events) events.put(eventJson(e));
            JSONObject data = new JSONObject().put("plan", planJson(engine.plan))
                    .put("index", s.index).put("round", s.round)
                    .put("phaseStart", s.phaseStart).put("deadline", s.deadline)
                    .put("pausedAt", s.pausedAt).put("amrapEnd", s.amrapEnd)
                    .put("emomBoundary", s.emomBoundary).put("paused", s.paused)
                    .put("specialCooldown", s.specialCooldown).put("events", events)
                    .put("startedWall", startedWall).put("startedElapsed", startedElapsed)
                    .put("savedElapsed", SystemClock.elapsedRealtime())
                    .put("bootWall", System.currentTimeMillis() - SystemClock.elapsedRealtime());
            Signals.prefs(c).edit().putString(ACTIVE, data.toString()).commit();
        } catch (JSONException ignored) { }
    }
    static final class Recovery {
        WorkoutEngine engine; long startedWall, startedElapsed;
    }
    static Recovery recover(Context c) {
        try {
            JSONObject data = new JSONObject(Signals.prefs(c).getString(ACTIVE, ""));
            long now = SystemClock.elapsedRealtime();
            if (now < data.getLong("savedElapsed") || Math.abs(
                    System.currentTimeMillis() - now - data.getLong("bootWall")) > 120_000)
                return null;
            WorkoutEngine.State s = new WorkoutEngine.State();
            s.index = data.getInt("index"); s.round = data.getInt("round");
            s.phaseStart = data.getLong("phaseStart"); s.deadline = data.getLong("deadline");
            s.pausedAt = data.optLong("pausedAt"); s.amrapEnd = data.optLong("amrapEnd");
            s.emomBoundary = data.optLong("emomBoundary"); s.paused = data.optBoolean("paused");
            s.specialCooldown = data.optBoolean("specialCooldown");
            JSONArray entries = data.optJSONArray("events");
            if (entries != null) for (int i = 0; i < entries.length(); i++) {
                JSONObject e = entries.optJSONObject(i);
                if (e != null) s.events.add(eventFromJson(e));
            }
            Recovery r = new Recovery();
            r.engine = WorkoutEngine.restore(fromJson(data.getJSONObject("plan")), s, now);
            r.startedWall = data.getLong("startedWall");
            r.startedElapsed = data.getLong("startedElapsed");
            return r;
        } catch (JSONException | IllegalArgumentException ignored) { return null; }
    }
    static boolean hasActive(Context c) { return Signals.prefs(c).contains(ACTIVE); }
    static void clearActive(Context c) { Signals.prefs(c).edit().remove(ACTIVE).commit(); }
    static void record(Context c, WorkoutEngine e, long wall, long elapsed, boolean completed) {
        try {
            JSONArray entries = new JSONArray();
            for (WorkoutEngine.Event event : e.events) entries.put(eventJson(event));
            JSONObject summary = new JSONObject().put("plan", planJson(e.plan))
                    .put("started", wall).put("durationSec",
                            Math.max(0, (SystemClock.elapsedRealtime() - elapsed) / 1000))
                    .put("completed", completed).put("rounds", e.plan.mode == WorkoutPlan.Mode.AMRAP
                            ? Math.max(0, e.round() - 1) : e.round()).put("events", entries);
            JSONArray old = history(c), next = new JSONArray(); next.put(summary);
            for (int i = 0; i < Math.min(49, old.length()); i++) next.put(old.get(i));
            Signals.prefs(c).edit().putString(HISTORY, next.toString()).commit();
        } catch (JSONException ignored) { }
    }
}
