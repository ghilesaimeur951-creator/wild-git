package com.example.sportchrono;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

final class CatalogStore {
    private static final String KEY = "street_exercises";
    private static final String[] INITIAL = {
            "traction_lestee|Traction lestée|Tractions|REPS|ADDED",
            "traction_delestee|Traction délestée|Tractions|REPS|ASSISTED",
            "traction_australienne|Traction australienne|Tractions|REPS|NONE",
            "traction_explosive|Traction explosive|Tractions|REPS|NONE",
            "high_pullup|High pull-up|Tractions|REPS|NONE",
            "traction_pronation_serree|Traction pronation · prise serrée|Tractions|REPS|NONE",
            "traction_pronation_standard|Traction pronation · prise standard|Tractions|REPS|NONE",
            "traction_pronation_large|Traction pronation · prise large|Tractions|REPS|NONE",
            "traction_supination_serree|Traction supination · prise serrée|Tractions|REPS|NONE",
            "traction_supination_standard|Traction supination · prise standard|Tractions|REPS|NONE",
            "traction_supination_large|Traction supination · prise large|Tractions|REPS|NONE",
            "dips_barre_droite|Dips sur barre droite|Dips|REPS|NONE",
            "dips_moyens|Dips sur barres parallèles · largeur moyenne|Dips|REPS|NONE",
            "dips_larges|Dips sur barres parallèles · largeur large|Dips|REPS|NONE",
            "muscle_up|Muscle-up|Haut du corps|REPS|NONE",
            "pompes_serrees|Pompes serrées|Pompes|REPS|NONE",
            "pompes_45|Pompes standard · coudes à 45°|Pompes|REPS|NONE",
            "pompes_ecartees|Pompes écartées|Pompes|REPS|NONE",
            "squats|Squats|Jambes|REPS|NONE",
            "squats_sautes|Squats sautés|Jambes|REPS|NONE",
            "fentes_marchees|Fentes marchées|Jambes|REPS|NONE",
            "hollow_hold|Gainage banane (hollow hold)|Tronc et dos|SECONDS|NONE",
            "extension_lombaire|Extensions au banc à lombaires|Tronc et dos|BOTH|NONE"
    };
    static JSONArray all(Context context) {
        String saved = Signals.prefs(context).getString(KEY, null);
        if (saved != null) try { return new JSONArray(saved); } catch (JSONException ignored) { }
        JSONArray initial = new JSONArray();
        try {
            for (String line : INITIAL) {
                String[] fields = line.split("\\|");
                initial.put(new JSONObject().put("id", fields[0]).put("name", fields[1])
                        .put("category", fields[2]).put("unit", fields[3])
                        .put("load", fields[4]).put("perSide", fields[0].equals("fentes_marchees"))
                        .put("favorite", false).put("hidden", false).put("custom", false));
            }
        } catch (JSONException ignored) { }
        save(context, initial); return initial;
    }
    static void save(Context c, JSONArray list) {
        Signals.prefs(c).edit().putString(KEY, list.toString()).commit();
    }
    static JSONObject find(Context c, String id) {
        JSONArray all = all(c);
        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) return item;
        }
        return null;
    }
    static void upsert(Context c, JSONObject exercise) {
        JSONArray old = all(c), next = new JSONArray(); boolean replaced = false;
        if (exercise.optString("id").isEmpty()) {
            try { exercise.put("id", UUID.randomUUID().toString()).put("custom", true); }
            catch (JSONException ignored) { }
        }
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && item.optString("id").equals(exercise.optString("id"))) {
                next.put(exercise); replaced = true;
            } else if (item != null) next.put(item);
        }
        if (!replaced) next.put(exercise);
        save(c, next);
    }
    static void delete(Context c, JSONObject exercise) {
        if (!exercise.optBoolean("custom")) {
            try { exercise.put("hidden", true); } catch (JSONException ignored) { }
            upsert(c, exercise); return;
        }
        JSONArray old = all(c), next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && !item.optString("id").equals(exercise.optString("id"))) next.put(item);
        }
        save(c, next);
    }
    static WorkoutPlan.Exercise asPlanExercise(JSONObject item) {
        WorkoutPlan.Exercise e = new WorkoutPlan.Exercise();
        e.catalogId = item.optString("id"); e.name = item.optString("name");
        e.category = item.optString("category"); e.perSide = item.optBoolean("perSide");
        try { e.load = WorkoutPlan.Load.valueOf(item.optString("load", "NONE")); }
        catch (IllegalArgumentException ignored) { }
        if ("SECONDS".equals(item.optString("unit"))) { e.capSec = 30; e.reps = 0; }
        return e;
    }
}
