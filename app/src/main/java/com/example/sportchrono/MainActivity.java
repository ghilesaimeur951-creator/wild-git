package com.example.sportchrono;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.media.RingtoneManager;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = UiKit.BG, CARD = UiKit.CARD, MINT = UiKit.MINT;
    private static final int TEXT = UiKit.TEXT, MUTED = UiKit.MUTED;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int tab = 0;
    private LinearLayout root, form;
    private TextView statusTitle, statusTime, statusMeta, alarmStatus, lapList;
    private Button pauseButton, stopButton;
    private int pickingToneId = -1;
    private int shownLaps = -1;
    private final Runnable update = new Runnable() {
        public void run() { refresh(); handler.postDelayed(this, 100); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        UiKit.configureWindow(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
        build();
    }
    @Override protected void onResume() {
        super.onResume();
        AlarmScheduler.scheduleAll(this);
        if (SessionService.current == null && SessionStore.hasSaved(this)) {
            startForegroundService(new Intent(this, SessionService.class).setAction(SessionService.ACTION_RESTORE));
        }
        refresh(); handler.post(update);
    }
    @Override protected void onPause() { handler.removeCallbacks(update); super.onPause(); }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable surface(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top);
        parent.addView(child, p);
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private Button button(String title, boolean primary, View.OnClickListener click) {
        Button b = UiKit.button(this, title, primary, click);
        b.setTextSize(15);
        return b;
    }
    private void build() {
        statusTitle = statusTime = statusMeta = alarmStatus = lapList = null;
        LinearLayout shell = column(); shell.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false); scroll.setClipToPadding(false);
        root = column(); root.setPadding(dp(18), dp(18), dp(18), dp(38));
        scroll.addView(root);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(8), dp(7), dp(8), dp(7)); nav.setBackgroundColor(CARD);
        String[] destinations = {"Chronos", "Séances", "Alarmes", "Journal", "Réglages"};
        int[] indexes = {0, 3, 4, 5, 6};
        for (int i = 0; i < destinations.length; i++) {
            final int selected = indexes[i];
            boolean current = i == 0 ? tab <= 2 : tab == selected;
            Button b = button(destinations[i], current, v -> { tab = selected; build(); });
            b.setTextSize(11); b.setMinHeight(dp(53)); b.setPadding(dp(2), 0, dp(2), 0);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(53), 1);
            if (i > 0) p.leftMargin = dp(3);
            nav.addView(b, p);
        }
        shell.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        setContentView(shell); UiKit.insets(this, shell, nav);
        add(root, text("SPORT CHRONO  /  " + (tab < 3 ? "CHRONOS" : destinations[tab - 2]), 12, MINT, true), 0);
        if (tab <= 2) {
            add(root, UiKit.photoHero(this, R.drawable.running_photo,
                    "BOUGEZ À VOTRE RYTHME", "Chaque seconde compte.",
                    "Un temps clair, un geste simple, et vous avancez."), 16);
            LinearLayout status = column(); status.setPadding(dp(20), dp(18), dp(20), dp(20));
            status.setBackground(surface(CARD, 20)); add(root, status, 15);
            statusTitle = text("Prêt à démarrer", 15, MINT, true); add(status, statusTitle, 0);
            statusTime = text("00:00:00", 46, TEXT, true); add(status, statusTime, 8);
            statusTime.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            statusTime.setAutoSizeTextTypeUniformWithConfiguration(29, 46, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP);
            statusMeta = text("Choisissez un mode ci-dessous", 14, MUTED, false); add(status, statusMeta, 4);
            LinearLayout controls = new LinearLayout(this);
            pauseButton = button("Pause", false, v -> control(SessionService.ACTION_PAUSE));
            stopButton = button("Arrêter", false, v -> control(SessionService.ACTION_STOP));
            LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(56), 1);
            half.rightMargin = dp(8); controls.addView(pauseButton, half);
            controls.addView(stopButton, new LinearLayout.LayoutParams(0, dp(56), 1));
            add(status, controls, 14);
            LinearLayout modes = new LinearLayout(this);
            String[] names = {"Chrono", "Minuteur", "Intervalles"};
            for (int i = 0; i < 3; i++) {
                final int choice = i;
                Button b = button(names[i], i == tab, v -> { tab = choice; build(); });
                b.setTextSize(13);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1);
                if (i > 0) p.leftMargin = dp(6);
                modes.addView(b, p);
            }
            add(root, modes, 18);
        } else if (tab == 3) {
            add(root, UiKit.photoHero(this, R.drawable.pullup_photo,
                    "STREET WORKOUT", "Votre séance, vos règles.",
                    "Construisez, entraînez-vous et suivez vos progrès."), 16);
        }
        form = column(); add(root, form, 20);
        switch (tab) {
            case 0: stopwatchForm(); break;
            case 1: timerForm(); break;
            case 2: intervalForm(); break;
            case 3: workoutForm(); break;
            case 4: alarmForm(); break;
            case 5: historyForm(); break;
            default: settingsForm();
        }
        refresh();
    }
    private void header(String title, String subtitle) {
        add(form, text(title, 23, TEXT, true), 0);
        add(form, text(subtitle, 14, MUTED, false), 7);
    }
    private EditText number(String label, String initial) {
        return UiKit.input(this, form, label, initial, true);
    }
    private EditText name(String label, String initial) {
        return UiKit.input(this, form, label, initial, false);
    }
    private int value(EditText input, int min, int max) {
        try {
            int n = Integer.parseInt(input.getText().toString().trim());
            if (n >= min && n <= max) return n;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Entrez une valeur entre " + min + " et " + max + ".");
    }
    private void start(SessionEngine.Mode mode, int prep, int work, int rest, int rounds) {
        Intent intent = new Intent(this, SessionService.class).setAction(SessionService.ACTION_START)
                .putExtra("mode", mode.name()).putExtra("prep", prep).putExtra("work", work)
                .putExtra("rest", rest).putExtra("rounds", rounds);
        startForegroundService(intent); refresh();
    }
    private void attempt(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
    }
    private void control(String action) {
        startService(new Intent(this, SessionService.class).setAction(action));
        handler.postDelayed(this::refresh, 150);
    }
    private void stopwatchForm() {
        header("Chronomètre", "Le temps monte jusqu’à ce que vous l’arrêtiez.");
        add(form, button("Démarrer le chrono", true,
                v -> start(SessionEngine.Mode.STOPWATCH, 0, 1, 0, 1)), 22);
        add(form, button("Marquer un tour", false, v -> {
            SessionEngine s = SessionService.current;
            if (s != null && s.mode == SessionEngine.Mode.STOPWATCH) {
                control(SessionService.ACTION_LAP);
                handler.postDelayed(this::showLaps, 180);
            }
        }), 10);
        lapList = text("", 16, MUTED, false); add(form, lapList, 14);
        shownLaps = -1;
        showLaps();
    }
    private void showLaps() {
        if (lapList == null || tab != 0) return;
        if (SessionService.current == null || SessionService.current.mode != SessionEngine.Mode.STOPWATCH) {
            if (shownLaps != 0) { lapList.setText(""); shownLaps = 0; }
            return;
        }
        StringBuilder list = new StringBuilder(); JSONArray laps = SessionService.laps;
        if (shownLaps == laps.length()) return;
        for (int i = laps.length() - 1; i >= 0; i--)
            list.append("Tour ").append(i + 1).append(" · ")
                    .append(format(laps.optLong(i), false)).append('\n');
        lapList.setText(list.toString());
        shownLaps = laps.length();
    }
    private void timerForm() {
        header("Minuteur", "Une durée simple, puis un signal à la fin.");
        EditText minutes = number("Minutes (0 à 999)", "5");
        EditText seconds = number("Secondes (0 à 59)", "0");
        add(form, button("Lancer le minuteur", true, v -> attempt(() -> {
            int duration = value(minutes, 0, 999) * 60 + value(seconds, 0, 59);
            if (duration == 0) throw new IllegalArgumentException("Choisissez au moins une seconde.");
            start(SessionEngine.Mode.TIMER, 0, duration, 0, 1);
        })), 24);
    }
    private void intervalForm() {
        header("Séance par intervalles", "Préparation → effort → repos, sur le nombre de tours choisi.");
        EditText presetName = name("Nom du programme", "Ma séance");
        EditText rounds = number("Nombre de tours (1 à 100)", "4");
        EditText prep = number("Préparation · secondes (0 à 3600)", "5");
        EditText work = number("Effort · secondes (1 à 3600)", "30");
        EditText rest = number("Repos · secondes (0 à 3600)", "10");
        add(form, text("Un repos est inclus après chaque tour, même le dernier. Un bip marque chaque transition et les cinq dernières secondes de chaque phase.",
                14, MUTED, false), 20);
        add(form, button("Lancer la séance", true, v -> attempt(() -> start(SessionEngine.Mode.INTERVAL,
                value(prep, 0, 3600), value(work, 1, 3600), value(rest, 0, 3600),
                value(rounds, 1, 100)))), 20);
        add(form, button("Enregistrer ce programme", false, v -> attempt(() -> {
            String title = presetName.getText().toString().trim();
            if (title.isEmpty()) throw new IllegalArgumentException("Donnez un nom au programme.");
            JSONArray existing = SessionStore.presets(this);
            if (existing.length() >= 20) throw new IllegalArgumentException("Maximum 20 programmes enregistrés.");
            try {
                existing.put(new JSONObject().put("name", title).put("rounds", value(rounds, 1, 100))
                        .put("prep", value(prep, 0, 3600)).put("work", value(work, 1, 3600))
                        .put("rest", value(rest, 0, 3600)));
                SessionStore.savePresets(this, existing); build();
            } catch (JSONException ignored) { }
        })), 10);
        JSONArray saved = SessionStore.presets(this);
        if (saved.length() > 0) add(form, text("Mes programmes", 20, TEXT, true), 26);
        for (int i = 0; i < saved.length(); i++) {
            final int index = i; JSONObject p = saved.optJSONObject(i);
            if (p == null) continue;
            add(form, button(p.optString("name") + "  ·  " + p.optInt("rounds") + " tours  ·  "
                    + p.optInt("work") + "/" + p.optInt("rest") + " s", false, v -> {
                presetName.setText(p.optString("name")); rounds.setText(String.valueOf(p.optInt("rounds")));
                prep.setText(String.valueOf(p.optInt("prep"))); work.setText(String.valueOf(p.optInt("work")));
                rest.setText(String.valueOf(p.optInt("rest")));
                Toast.makeText(this, "Programme chargé", Toast.LENGTH_SHORT).show();
            }), 10);
            add(form, button("Supprimer « " + p.optString("name") + " »", false, v -> {
                JSONArray old = SessionStore.presets(this), next = new JSONArray();
                for (int n = 0; n < old.length(); n++) if (n != index) next.put(old.opt(n));
                SessionStore.savePresets(this, next); build();
            }), 4);
        }
    }
    private void alarmForm() {
        header("Mes alarmes", "Plusieurs heures, jours et sonneries au choix.");
        add(form, button("Ajouter une alarme", true, v -> editAlarm(null)), 20);
        JSONArray list = AlarmStore.list(this);
        for (int i = 0; i < list.length(); i++) {
            JSONObject alarm = list.optJSONObject(i);
            if (alarm == null) continue;
            int id = alarm.optInt("id");
            LinearLayout card = column(); card.setPadding(dp(16), dp(12), dp(16), dp(16));
            card.setBackground(surface(CARD, 16)); add(form, card, 12);
            add(card, text(String.format(Locale.FRANCE, "%02d:%02d  ·  %s",
                    alarm.optInt("hour"), alarm.optInt("minute"), alarm.optString("label", "Alarme")),
                    21, TEXT, true), 0);
            add(card, text(AlarmStore.daysLabel(alarm.optInt("days", 127)), 14, MUTED, false), 5);
            CheckBox enabled = new CheckBox(this); enabled.setText("Activée");
            enabled.setTextColor(TEXT); enabled.setChecked(alarm.optBoolean("enabled", true));
            enabled.setOnCheckedChangeListener((button, checked) -> {
                try { alarm.put("enabled", checked); } catch (JSONException ignored) { }
                AlarmStore.upsert(this, alarm);
                if (checked) { AlarmScheduler.schedule(this, alarm); requestExactAlarm(); }
                else AlarmScheduler.cancel(this, id);
                refresh();
            });
            add(card, enabled, 5);
            add(card, button("Modifier heure et jours", false, v -> editAlarm(alarm)), 6);
            add(card, button("Choisir la sonnerie", false, v -> pickTone(id)), 6);
            add(card, button("Supprimer l’alarme", false, v -> {
                AlarmScheduler.cancel(this, id); AlarmStore.delete(this, id); build();
            }), 6);
        }
        alarmStatus = text("", 14, MUTED, false); add(form, alarmStatus, 18);
        add(form, button("Autoriser les alarmes exactes", false, v -> {
            try { startActivity(AlarmScheduler.permissionSettings(this)); }
            catch (RuntimeException error) { Toast.makeText(this, "Réglage indisponible sur ce téléphone.", Toast.LENGTH_SHORT).show(); }
        }), 18);
        add(form, text("La notification permet d’arrêter la sonnerie ou de la reporter de cinq minutes. Les alarmes sont reprogrammées après un redémarrage.",
                14, MUTED, false), 18);
    }
    private void workoutForm() {
        header("Créer une séance street workout", "Composez les exercices, séries, repos et modes spéciaux.");
        add(form, button(WorkoutService.current != null || WorkoutStore.hasActive(this)
                ? "Reprendre la séance en cours" : "Créer ou charger une séance", true,
                v -> startActivity(new Intent(this, WorkoutActivity.class))), 20);
        add(form, text("Séries classiques, EMOM, pyramides, circuits, superséries et AMRAP. "
                + "Les modèles et les résultats sont conservés sur cet appareil.",
                15, MUTED, false), 17);
        add(form, button("Voir le catalogue d’exercices", false,
                v -> startActivity(new Intent(this, CatalogActivity.class))), 14);
    }
    private void requestExactAlarm() {
        if (AlarmScheduler.exactAllowed(this)) return;
        Toast.makeText(this, "Autorisez les alarmes exactes pour une sonnerie à l’heure précise.",
                Toast.LENGTH_LONG).show();
        try { startActivity(AlarmScheduler.permissionSettings(this)); }
        catch (RuntimeException ignored) { }
    }
    private void editAlarm(JSONObject existing) {
        final int[] time = {existing == null ? 7 : existing.optInt("hour"),
                existing == null ? 0 : existing.optInt("minute")};
        LinearLayout panel = column(); panel.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText label = new EditText(this); label.setSingleLine(true); label.setHint("Nom de l’alarme");
        label.setText(existing == null ? "Entraînement" : existing.optString("label"));
        label.setTextColor(TEXT); label.setHintTextColor(MUTED); add(panel, label, 0);
        Button when = button(String.format(Locale.FRANCE, "%02d:%02d", time[0], time[1]), false, null);
        when.setOnClickListener(v -> new TimePickerDialog(this, (picker, hour, minute) -> {
            time[0] = hour; time[1] = minute;
            when.setText(String.format(Locale.FRANCE, "%02d:%02d", hour, minute));
        }, time[0], time[1], true).show());
        add(panel, when, 12);
        add(panel, text("Jours de répétition", 16, TEXT, true), 16);
        String[] dayNames = {"Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"};
        CheckBox[] checks = new CheckBox[7];
        int days = existing == null ? 127 : existing.optInt("days", 127);
        for (int i = 0; i < 7; i++) {
            checks[i] = new CheckBox(this); checks[i].setText(dayNames[i]);
            checks[i].setTextColor(TEXT); checks[i].setChecked((days & (1 << i)) != 0);
            add(panel, checks[i], 0);
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(existing == null ? "Nouvelle alarme" : "Modifier l’alarme")
                .setView(scroll).setNegativeButton("Annuler", null).setPositiveButton("Enregistrer", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int mask = 0; for (int i = 0; i < 7; i++) if (checks[i].isChecked()) mask |= 1 << i;
            if (mask == 0) { Toast.makeText(this, "Choisissez au moins un jour.", Toast.LENGTH_SHORT).show(); return; }
            try {
                JSONObject alarm = existing == null ? new JSONObject() : existing;
                int id = existing == null ? AlarmStore.nextId(this) : existing.optInt("id");
                alarm.put("id", id).put("label", label.getText().toString().trim())
                        .put("hour", time[0]).put("minute", time[1]).put("days", mask);
                if (existing == null) alarm.put("enabled", true).put("tone", "");
                AlarmStore.upsert(this, alarm); AlarmScheduler.schedule(this, alarm);
                dialog.dismiss(); build(); requestExactAlarm();
            } catch (JSONException ignored) { }
        }));
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void pickTone(int id) {
        pickingToneId = id;
        Intent picker = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        picker.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        picker.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonnerie de l’alarme");
        picker.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        JSONObject alarm = AlarmStore.find(this, id);
        String saved = alarm == null ? "" : alarm.optString("tone", "");
        if (!saved.isEmpty()) picker.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(saved));
        try { startActivityForResult(picker, 23); }
        catch (RuntimeException error) { Toast.makeText(this, "Choix des sonneries indisponible.", Toast.LENGTH_SHORT).show(); }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 23 && resultCode == RESULT_OK && data != null) {
            JSONObject alarm = AlarmStore.find(this, pickingToneId);
            Uri selected = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (alarm != null && selected != null) {
                try { alarm.put("tone", selected.toString()); AlarmStore.upsert(this, alarm); }
                catch (JSONException ignored) { }
                Toast.makeText(this, "Sonnerie enregistrée", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void historyForm() {
        header("Historique", "Vos séances restent sur ce téléphone.");
        JSONArray workouts = WorkoutStore.history(this);
        add(form, text("Street workout", 20, TEXT, true), 20);
        if (workouts.length() == 0)
            add(form, text("Aucune séance street workout enregistrée.", 15, MUTED, false), 10);
        for (int n = 0; n < workouts.length(); n++) {
            JSONObject workout = workouts.optJSONObject(n);
            if (workout == null || workout.optJSONObject("plan") == null) continue;
            WorkoutPlan plan = WorkoutStore.fromJson(workout.optJSONObject("plan"));
            JSONArray results = workout.optJSONArray("events");
            String date = android.text.format.DateFormat.getDateFormat(this).format(workout.optLong("started"));
            add(form, button(plan.name + " · " + date + "\n"
                    + (workout.optBoolean("completed") ? "Terminée" : "Interrompue")
                    + " · " + (results == null ? 0 : results.length()) + " séries", false,
                    v -> showWorkoutHistory(workout)), 9);
        }
        add(form, text("Chronomètres et intervalles", 20, TEXT, true), 25);
        JSONArray list = SessionStore.history(this);
        if (list.length() == 0) add(form, text("Aucune séance terminée pour le moment.", 16, MUTED, false), 20);
        for (int i = 0; i < list.length(); i++) {
            JSONObject entry = list.optJSONObject(i); if (entry == null) continue;
            String mode = entry.optString("mode");
            String title = "STOPWATCH".equals(mode) ? "Chronomètre" : "TIMER".equals(mode) ? "Minuteur" : "Intervalles";
            String date = android.text.format.DateFormat.getDateFormat(this).format(entry.optLong("date"));
            long seconds = entry.optLong("seconds");
            JSONArray laps = entry.optJSONArray("laps");
            long best = Long.MAX_VALUE, previous = 0;
            if (laps != null) for (int n = 0; n < laps.length(); n++) {
                long split = laps.optLong(n) - previous;
                if (split > 0) best = Math.min(best, split);
                previous = laps.optLong(n);
            }
            LinearLayout entryCard = column();
            entryCard.setPadding(dp(16), dp(14), dp(16), dp(14));
            entryCard.setBackground(surface(CARD, 16));
            add(form, entryCard, 10);
            add(entryCard, text(title + " · " + date, 17, TEXT, true), 0);
            add(entryCard, text(format(seconds * 1000, false)
                    + ("INTERVAL".equals(mode) ? " · " + entry.optInt("rounds") + " tours" : "")
                    + ("STOPWATCH".equals(mode) ? " · "
                        + (laps == null ? 0 : laps.length()) + " repères"
                        + (best == Long.MAX_VALUE ? "" : " · meilleur tour " + format(best, false)) : ""),
                    14, MUTED, false), 5);
        }
    }
    private void showWorkoutHistory(JSONObject entry) {
        JSONObject saved = entry.optJSONObject("plan"); if (saved == null) return;
        WorkoutPlan plan = WorkoutStore.fromJson(saved);
        StringBuilder detail = new StringBuilder("Durée : ")
                .append(format(entry.optLong("durationSec") * 1000, false))
                .append("\nMode : ").append(entry.optBoolean("completed") ? "terminée" : "interrompue")
                .append("\n\n");
        JSONArray events = entry.optJSONArray("events");
        if (events != null) for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i); if (event == null) continue;
            int position = event.optInt("exercise", -1);
            if (position < 0 || position >= plan.exercises.size()) continue;
            WorkoutPlan.Exercise exercise = plan.exercises.get(position);
            detail.append(exercise.name).append(" · série ").append(event.optInt("series"));
            String result = event.optString("result");
            detail.append("DONE".equals(result) ? " : " + event.optInt("actual") + " répétitions"
                    : "EXPIRED".equals(result) ? " : temps écoulé, non validée" : " : passée");
            if (event.optInt("left", -1) >= 0)
                detail.append(" (G ").append(event.optInt("left"))
                        .append(", D ").append(event.optInt("right")).append(")");
            if (exercise.load != WorkoutPlan.Load.NONE)
                detail.append(" · ").append(exercise.kilograms).append(" kg ")
                        .append(exercise.load == WorkoutPlan.Load.ADDED ? "ajoutés" : "d’assistance");
            detail.append(" · ").append(format(event.optLong("elapsed"), false)).append('\n');
        }
        ScrollView scroll = new ScrollView(this);
        TextView body = text(detail.toString(), 16, TEXT, false);
        body.setPadding(dp(20), dp(12), dp(20), dp(16)); scroll.addView(body);
        new AlertDialog.Builder(this).setTitle(plan.name).setView(scroll)
                .setPositiveButton("Fermer", null).show();
    }
    private void settingsForm() {
        header("Sons & autorisations", "Les bips utilisent le canal d’alarme du téléphone.");
        add(form, button("Catalogue d’exercices street workout", true,
                v -> startActivity(new Intent(this, CatalogActivity.class))), 17);
        SharedPreferences p = Signals.prefs(this);
        CheckBox sound = new CheckBox(this); sound.setText("Sons activés"); sound.setTextColor(TEXT);
        sound.setChecked(p.getBoolean("sound", true));
        sound.setOnCheckedChangeListener((v, checked) -> p.edit().putBoolean("sound", checked).apply());
        add(form, sound, 18);
        CheckBox vibrate = new CheckBox(this); vibrate.setText("Vibration activée"); vibrate.setTextColor(TEXT);
        vibrate.setChecked(p.getBoolean("vibrate", true));
        vibrate.setOnCheckedChangeListener((v, checked) -> p.edit().putBoolean("vibrate", checked).apply());
        add(form, vibrate, 6);
        CheckBox voice = new CheckBox(this); voice.setText("Annonces vocales en français"); voice.setTextColor(TEXT);
        voice.setChecked(p.getBoolean("voice", true));
        voice.setOnCheckedChangeListener((v, checked) -> p.edit().putBoolean("voice", checked).apply());
        add(form, voice, 6);
        CheckBox duck = new CheckBox(this); duck.setText("Baisser brièvement la musique pendant les signaux");
        duck.setTextColor(TEXT); duck.setChecked(p.getBoolean("duck", true));
        duck.setOnCheckedChangeListener((v, checked) -> p.edit().putBoolean("duck", checked).apply());
        add(form, duck, 6);
        TextView volume = text("Volume des bips : " + p.getInt("volume", 75) + " %", 15, TEXT, true);
        add(form, volume, 22);
        SeekBar slider = new SeekBar(this); slider.setMax(100); slider.setProgress(p.getInt("volume", 75));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int n, boolean user) {
                volume.setText("Volume des bips : " + n + " %");
                if (user) p.edit().putInt("volume", n).apply();
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { Signals.beep(MainActivity.this, false); }
        });
        add(form, slider, 6);
        add(form, button("Tester le son", false, v -> Signals.beep(this, true)), 10);
        add(form, text("Les annonces vocales utilisent la voix française installée sur le téléphone. Les bips restent disponibles si elle manque.",
                14, MUTED, false), 12);
        add(form, button("Réglage du volume système", false, v ->
                startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS))), 10);
        add(form, text("Les notifications et les alarmes exactes sont autorisées séparément par Android. Internet est déclaré pour de futures fonctions ; aucune connexion n’est utilisée par ces chronomètres.",
                14, MUTED, false), 20);
        add(form, button("Autoriser les notifications", false, v -> {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
            else Toast.makeText(this, "Gérées par les paramètres Android.", Toast.LENGTH_SHORT).show();
        }), 18);
        add(form, text("Photos : Vitaly Gariev et Mathias Reding · Unsplash. Images incluses pour une utilisation hors connexion.",
                13, MUTED, false), 24);
    }
    private String format(long ms, boolean countdown) {
        long seconds = countdown ? (ms + 999) / 1000 : ms / 1000;
        return String.format(Locale.FRANCE, "%02d:%02d:%02d", seconds / 3600,
                (seconds / 60) % 60, seconds % 60);
    }
    private void refresh() {
        SessionEngine s = SessionService.current;
        boolean active = s != null;
        if (statusTitle != null) {
            pauseButton.setVisibility(active ? View.VISIBLE : View.GONE);
            stopButton.setVisibility(active ? View.VISIBLE : View.GONE);
        }
        if (active && statusTitle != null) {
            long now = SystemClock.elapsedRealtime();
            statusTime.setText(format(s.displayMs(now), s.mode != SessionEngine.Mode.STOPWATCH));
            if (s.mode == SessionEngine.Mode.STOPWATCH) statusTitle.setText("Chronomètre");
            else if (s.mode == SessionEngine.Mode.TIMER) statusTitle.setText("Minuteur");
            else statusTitle.setText(s.phase == SessionEngine.Phase.PREPARE ? "Préparez-vous"
                    : s.phase == SessionEngine.Phase.WORK ? "Effort !" : "Repos");
            statusMeta.setText((s.mode == SessionEngine.Mode.INTERVAL ? "Tour " + s.round + " / " + s.rounds + " · " : "")
                    + (s.paused ? "En pause" : "En cours"));
            pauseButton.setText(s.paused ? "Reprendre" : "Pause");
            pauseButton.setOnClickListener(v -> control(s.paused ? SessionService.ACTION_RESUME : SessionService.ACTION_PAUSE));
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else if (statusTitle != null) {
            statusTitle.setText("Prêt à démarrer"); statusTime.setText("00:00:00");
            statusMeta.setText("Choisissez un mode ci-dessous");
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (alarmStatus != null && tab == 4) {
            alarmStatus.setText(AlarmScheduler.exactAllowed(this)
                    ? "Horaire précis autorisé"
                    : "Android peut décaler les alarmes tant que l’accès exact est refusé.");
        }
        if (tab == 0 && active && s.mode == SessionEngine.Mode.STOPWATCH) showLaps();
    }
}
