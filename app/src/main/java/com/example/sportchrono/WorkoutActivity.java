package com.example.sportchrono;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WorkoutActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WorkoutPlan draft;
    private boolean editing, runningScreen, summaryScreen;
    private EditText name, prep, warmup, cooldown, sets, reps, cap, restSeries, restExercise;
    private Spinner mode;
    private LinearLayout modePanel, root, controls, actualPanel;
    private final Map<String, EditText> special = new HashMap<>();
    private Spinner direction;
    private CheckBox peak;
    private TextView runTitle, runTime, runMeta, runNext;
    private Button doneButton, skipButton, pauseButton, previousButton;
    private EditText actual, left, right;
    private String displayedStep = "";
    private boolean hadRunning;
    private long pendingStartUntil;
    private final Runnable update = new Runnable() {
        public void run() { refreshRun(); handler.postDelayed(this, 150); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); draft = WorkoutStore.draft(this);
        if (WorkoutService.current != null || WorkoutStore.hasActive(this)) renderRun();
        else renderBuilder();
    }
    @Override protected void onResume() {
        super.onResume();
        if (WorkoutService.current == null && WorkoutStore.hasActive(this))
            startForegroundService(new Intent(this, WorkoutService.class).setAction(WorkoutService.RESTORE));
        handler.post(update);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(update);
        if (editing) capture(false);
        super.onPause();
    }
    private void add(LinearLayout parent, View v, int top) { UiKit.add(this, parent, v, top); }
    private TextView text(String s, int size, int color, boolean bold) {
        return UiKit.text(this, s, size, color, bold);
    }
    private Button button(String title, boolean primary, View.OnClickListener click) {
        return UiKit.button(this, title, primary, click);
    }
    private EditText input(LinearLayout panel, String label, int number) {
        return UiKit.input(this, panel, label, String.valueOf(number), true);
    }
    private Spinner spinner(String[] values, int selected) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        s.setSelection(selected); return s;
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private int parse(EditText field, int min, int max) { return UiKit.integer(field, min, max); }
    private int parseOr(EditText field, int fallback) {
        try { return Integer.parseInt(field.getText().toString().trim()); }
        catch (Exception ignored) { return fallback; }
    }
    private void formRoot(String title, String subtitle) {
        root = UiKit.column(this); UiKit.screen(this, root);
        add(root, text(title, 27, UiKit.TEXT, true), 0);
        add(root, text(subtitle, 15, UiKit.MUTED, false), 7);
    }
    private static String modeName(WorkoutPlan.Mode m) {
        switch (m) {
            case EMOM: return "EMOM";
            case PYRAMID: return "Pyramide";
            case CIRCUIT: return "Circuit";
            case SUPERSET: return "Supersérie";
            case AMRAP: return "AMRAP";
            default: return "Séries classiques";
        }
    }
    private static final String[] MODES = {"Séries classiques", "EMOM", "Pyramide", "Circuit", "Supersérie", "AMRAP"};

    private void renderBuilder() {
        editing = true; runningScreen = false; summaryScreen = false; displayedStep = "";
        formRoot("Créer une séance", "Composez votre entraînement street workout.");
        add(root, button("Mes modèles", false, v -> { capture(false); renderTemplates(); }), 15);
        add(root, button("Catalogue d’exercices", false, v -> {
            capture(false); startActivity(new Intent(this, CatalogActivity.class));
        }), 8);
        name = UiKit.input(this, root, "Nom de la séance", draft.name, false);
        add(root, text("Mode d’entraînement", 15, UiKit.MUTED, true), 17);
        mode = spinner(MODES, draft.mode.ordinal()); add(root, mode, 5);
        modePanel = UiKit.column(this); add(root, modePanel, 5);
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            private boolean initial = true;
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                if (!initial) captureSpecial(false);
                initial = false; draft.mode = WorkoutPlan.Mode.values()[pos]; renderModeFields();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        renderModeFields();
        add(root, text("Paramètres par défaut", 21, UiKit.TEXT, true), 23);
        prep = input(root, "Préparation · secondes", draft.prepSec);
        warmup = input(root, "Échauffement facultatif · secondes", draft.warmupSec);
        cooldown = input(root, "Retour au calme facultatif · secondes", draft.cooldownSec);
        sets = input(root, "Séries par exercice", draft.defaultSets);
        reps = input(root, "Répétitions visées (0 = libre)", draft.defaultReps);
        cap = input(root, "Durée maximale d’une série · secondes (0 = sans limite)", draft.defaultCapSec);
        restSeries = input(root, "Repos entre séries · secondes", draft.restSeriesSec);
        restExercise = input(root, "Repos entre exercices · secondes", draft.restExerciseSec);
        add(root, text("Exercices · dans l’ordre de la séance", 21, UiKit.TEXT, true), 25);
        add(root, button("+ Ajouter un exercice", true, v -> {
            if (capture(true)) startActivityForResult(new Intent(this, CatalogActivity.class)
                    .putExtra(CatalogActivity.SELECT, true), 31);
        }), 10);
        for (int i = 0; i < draft.exercises.size(); i++) addExerciseCard(i);
        add(root, text("Les options d’un exercice remplacent les valeurs par défaut uniquement lorsqu’elles sont cochées.",
                14, UiKit.MUTED, false), 18);
        add(root, button("Enregistrer le modèle", false, v -> {
            if (capture(true)) {
                try { draft.validate(); WorkoutStore.upsertTemplate(this, draft);
                    WorkoutStore.saveDraft(this, draft); toast("Modèle enregistré."); }
                catch (IllegalArgumentException error) { toast(error.getMessage()); }
            }
        }), 17);
        add(root, button("Voir le programme et démarrer", true, v -> {
            if (!capture(true)) return;
            try { draft.validate(); previewAndStart(draft.copy()); }
            catch (IllegalArgumentException error) { toast(error.getMessage()); }
        }), 10);
    }
    private void modeNumber(String key, String label, int value) {
        special.put(key, input(modePanel, label, value));
    }
    private void renderModeFields() {
        if (modePanel == null) return;
        modePanel.removeAllViews(); special.clear(); direction = null; peak = null;
        switch (draft.mode) {
            case EMOM:
                modeNumber("interval", "Durée d’un intervalle · secondes", draft.emomIntervalSec);
                modeNumber("cycles", "Nombre de cycles", draft.emomCycles);
                add(modePanel, text("Les exercices alternent à chaque cycle. Terminé lance le repos restant jusqu’à la minute suivante.",
                        14, UiKit.MUTED, false), 9); break;
            case PYRAMID:
                modeNumber("start", "Répétitions au départ", draft.pyramidStart);
                modeNumber("max", "Sommet · répétitions", draft.pyramidMax);
                modeNumber("step", "Écart entre les marches", draft.pyramidStep);
                modeNumber("rest", "Repos entre marches · secondes", draft.pyramidRestSec);
                add(modePanel, text("Parcours", 15, UiKit.MUTED, true), 13);
                direction = spinner(new String[]{"Montée", "Descente", "Aller-retour"},
                        draft.pyramidDirection == WorkoutPlan.PyramidDirection.UP ? 0
                                : draft.pyramidDirection == WorkoutPlan.PyramidDirection.DOWN ? 1 : 2);
                add(modePanel, direction, 4);
                peak = new CheckBox(this); peak.setText("Répéter le sommet à l’aller-retour");
                peak.setTextColor(UiKit.TEXT); peak.setChecked(draft.repeatPeak);
                add(modePanel, peak, 5);
                add(modePanel, text("La pyramide utilise le premier exercice ; les suivants gardent leurs séries classiques.",
                        14, UiKit.MUTED, false), 8);
                add(modePanel, button("Afficher les marches", false, v -> {
                    if (captureSpecial(true)) {
                        try { draft.validate(); toast(draft.pyramidTargets().toString()); }
                        catch (IllegalArgumentException error) { toast(error.getMessage()); }
                    }
                }), 10); break;
            case CIRCUIT: case SUPERSET:
                modeNumber("rounds", "Nombre de tours", draft.circuitRounds);
                modeNumber("rest", "Repos après chaque tour · secondes", draft.circuitRestSec);
                add(modePanel, text(draft.mode == WorkoutPlan.Mode.SUPERSET
                        ? "Deux exercices s’enchaînent sans repos, puis vient le repos du tour."
                        : "Tous les exercices s’enchaînent sans repos ; repos à la fin du tour.",
                        14, UiKit.MUTED, false), 8); break;
            case AMRAP:
                modeNumber("duration", "Durée totale · secondes", draft.amrapDurationSec);
                add(modePanel, text("Enchaînez les exercices autant de fois que possible jusqu’à la fin du temps.",
                        14, UiKit.MUTED, false), 8); break;
            default: break;
        }
    }
    private boolean captureSpecial(boolean strict) {
        if (special.isEmpty()) return true;
        try {
            switch (draft.mode) {
                case EMOM:
                    draft.emomIntervalSec = strict ? parse(special.get("interval"), 5, 3600)
                            : parseOr(special.get("interval"), draft.emomIntervalSec);
                    draft.emomCycles = strict ? parse(special.get("cycles"), 1, 300)
                            : parseOr(special.get("cycles"), draft.emomCycles); break;
                case PYRAMID:
                    draft.pyramidStart = strict ? parse(special.get("start"), 1, 1000)
                            : parseOr(special.get("start"), draft.pyramidStart);
                    draft.pyramidMax = strict ? parse(special.get("max"), 1, 1000)
                            : parseOr(special.get("max"), draft.pyramidMax);
                    draft.pyramidStep = strict ? parse(special.get("step"), 1, 1000)
                            : parseOr(special.get("step"), draft.pyramidStep);
                    draft.pyramidRestSec = strict ? parse(special.get("rest"), 0, 3600)
                            : parseOr(special.get("rest"), draft.pyramidRestSec);
                    draft.pyramidDirection = new WorkoutPlan.PyramidDirection[]{WorkoutPlan.PyramidDirection.UP,
                            WorkoutPlan.PyramidDirection.DOWN, WorkoutPlan.PyramidDirection.BOTH}[
                            direction.getSelectedItemPosition()];
                    draft.repeatPeak = peak.isChecked(); break;
                case CIRCUIT: case SUPERSET:
                    draft.circuitRounds = strict ? parse(special.get("rounds"), 1, 100)
                            : parseOr(special.get("rounds"), draft.circuitRounds);
                    draft.circuitRestSec = strict ? parse(special.get("rest"), 0, 3600)
                            : parseOr(special.get("rest"), draft.circuitRestSec); break;
                case AMRAP:
                    draft.amrapDurationSec = strict ? parse(special.get("duration"), 5, 86400)
                            : parseOr(special.get("duration"), draft.amrapDurationSec); break;
                default: break;
            }
            return true;
        } catch (IllegalArgumentException error) { toast(error.getMessage()); return false; }
    }
    private boolean capture(boolean strict) {
        if (!editing || name == null) return true;
        try {
            draft.name = name.getText().toString().trim();
            draft.prepSec = strict ? parse(prep, 0, 3600) : parseOr(prep, draft.prepSec);
            draft.warmupSec = strict ? parse(warmup, 0, 3600) : parseOr(warmup, draft.warmupSec);
            draft.cooldownSec = strict ? parse(cooldown, 0, 3600) : parseOr(cooldown, draft.cooldownSec);
            draft.defaultSets = strict ? parse(sets, 1, 100) : parseOr(sets, draft.defaultSets);
            draft.defaultReps = strict ? parse(reps, 0, 1000) : parseOr(reps, draft.defaultReps);
            draft.defaultCapSec = strict ? parse(cap, 0, 3600) : parseOr(cap, draft.defaultCapSec);
            draft.restSeriesSec = strict ? parse(restSeries, 0, 3600) : parseOr(restSeries, draft.restSeriesSec);
            draft.restExerciseSec = strict ? parse(restExercise, 0, 3600) : parseOr(restExercise, draft.restExerciseSec);
            if (!captureSpecial(strict)) return false;
            WorkoutStore.saveDraft(this, draft); return true;
        } catch (IllegalArgumentException error) { toast(error.getMessage()); return false; }
    }
    private void addExerciseCard(int position) {
        WorkoutPlan.Exercise e = draft.exercises.get(position);
        LinearLayout card = UiKit.column(this);
        card.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 12), UiKit.dp(this, 14), UiKit.dp(this, 12));
        card.setBackground(UiKit.background(this, UiKit.CARD, 15)); add(root, card, 10);
        add(card, text((position + 1) + ". " + e.name, 19, UiKit.TEXT, true), 0);
        String hint = (e.sets < 0 ? "Séries par défaut" : e.sets + " séries")
                + " · " + (e.capSec < 0 ? "Durée par défaut" : e.capSec == 0 ? "Sans limite" : e.capSec + " s")
                + (e.load == WorkoutPlan.Load.NONE ? "" : " · " + e.kilograms + " kg "
                + (e.load == WorkoutPlan.Load.ADDED ? "ajoutés" : "d’assistance"));
        add(card, text(hint, 14, UiKit.MUTED, false), 4);
        add(card, button("Configurer", false, v -> editExercise(position)), 9);
        LinearLayout actions = new LinearLayout(this);
        Button up = button("↑", false, v -> move(position, -1));
        Button down = button("↓", false, v -> move(position, 1));
        Button remove = button("Retirer", false, v -> new AlertDialog.Builder(this)
                .setMessage("Retirer « " + e.name + " » de la séance ?")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Retirer", (d, which) -> {
                    capture(false); draft.exercises.remove(position);
                    WorkoutStore.saveDraft(this, draft); renderBuilder();
                }).show());
        actions.addView(up, new LinearLayout.LayoutParams(0, UiKit.dp(this, 54), 1));
        actions.addView(down, new LinearLayout.LayoutParams(0, UiKit.dp(this, 54), 1));
        actions.addView(remove, new LinearLayout.LayoutParams(0, UiKit.dp(this, 54), 2));
        up.setEnabled(position > 0); down.setEnabled(position < draft.exercises.size() - 1);
        add(card, actions, 7);
    }
    private void move(int from, int delta) {
        capture(false);
        WorkoutPlan.Exercise e = draft.exercises.remove(from);
        draft.exercises.add(from + delta, e);
        WorkoutStore.saveDraft(this, draft); renderBuilder();
    }
    private CheckBox option(LinearLayout panel, String label, boolean checked) {
        CheckBox box = new CheckBox(this); box.setText(label); box.setTextColor(UiKit.TEXT);
        box.setChecked(checked); add(panel, box, 10); return box;
    }
    private EditText overrideField(LinearLayout panel, String label, int current, int defaultValue) {
        return input(panel, label + " · " + (current < 0 ? "défaut " + defaultValue : "personnalisé"),
                current < 0 ? defaultValue : current);
    }
    private void editExercise(int position) {
        WorkoutPlan.Exercise e = draft.exercises.get(position);
        LinearLayout panel = UiKit.column(this);
        panel.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 8), UiKit.dp(this, 18), UiKit.dp(this, 8));
        CheckBox setOption = option(panel, "Personnaliser le nombre de séries", e.sets >= 0);
        EditText setValue = overrideField(panel, "Séries", e.sets, draft.defaultSets);
        CheckBox repOption = option(panel, "Définir les répétitions visées", e.reps >= 0);
        EditText repValue = overrideField(panel, "Répétitions (0 = libres)", e.reps, draft.defaultReps);
        CheckBox capOption = option(panel, "Personnaliser la durée maximale", e.capSec >= 0);
        EditText capValue = overrideField(panel, "Secondes (0 = sans limite)", e.capSec, draft.defaultCapSec);
        CheckBox restOption = option(panel, "Personnaliser le repos entre séries", e.restSec >= 0);
        EditText restValue = overrideField(panel, "Repos · secondes", e.restSec, draft.restSeriesSec);
        CheckBox loadOption = option(panel, "Ajouter une charge ou une assistance", e.load != WorkoutPlan.Load.NONE);
        Spinner loadType = spinner(new String[]{"Charge ajoutée", "Assistance"},
                e.load == WorkoutPlan.Load.ASSISTED ? 1 : 0);
        add(panel, loadType, 5);
        add(panel, text("Kilogrammes", 15, UiKit.MUTED, true), 12);
        EditText weight = new EditText(this); weight.setSingleLine(true);
        weight.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weight.setText(String.valueOf(e.kilograms)); weight.setTextColor(UiKit.TEXT);
        weight.setTextSize(20); add(panel, weight, 2);
        CheckBox sides = option(panel, "Compter gauche et droite séparément", e.perSide);
        CheckBox notesOption = option(panel, "Ajouter une note", !e.notes.isEmpty());
        EditText notes = UiKit.input(this, panel, "Note personnelle", e.notes, false);
        setValue.setEnabled(setOption.isChecked()); repValue.setEnabled(repOption.isChecked());
        capValue.setEnabled(capOption.isChecked()); restValue.setEnabled(restOption.isChecked());
        loadType.setEnabled(loadOption.isChecked()); weight.setEnabled(loadOption.isChecked());
        notes.setEnabled(notesOption.isChecked());
        setOption.setOnCheckedChangeListener((v, checked) -> setValue.setEnabled(checked));
        repOption.setOnCheckedChangeListener((v, checked) -> repValue.setEnabled(checked));
        capOption.setOnCheckedChangeListener((v, checked) -> capValue.setEnabled(checked));
        restOption.setOnCheckedChangeListener((v, checked) -> restValue.setEnabled(checked));
        loadOption.setOnCheckedChangeListener((v, checked) -> {
            loadType.setEnabled(checked); weight.setEnabled(checked);
        });
        notesOption.setOnCheckedChangeListener((v, checked) -> notes.setEnabled(checked));
        ScrollView scroll = new ScrollView(this); scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(e.name).setView(scroll)
                .setNegativeButton("Annuler", null).setPositiveButton("Enregistrer", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int newSets = setOption.isChecked() ? parse(setValue, 1, 100) : -1;
                int newReps = repOption.isChecked() ? parse(repValue, 0, 1000) : -1;
                int newCap = capOption.isChecked() ? parse(capValue, 0, 3600) : -1;
                int newRest = restOption.isChecked() ? parse(restValue, 0, 3600) : -1;
                double kg = 0;
                if (loadOption.isChecked()) {
                    kg = Double.parseDouble(weight.getText().toString().trim().replace(',', '.'));
                    if (!Double.isFinite(kg) || kg < 0 || kg > 1000)
                        throw new NumberFormatException();
                }
                e.sets = newSets; e.reps = newReps; e.capSec = newCap; e.restSec = newRest;
                e.load = !loadOption.isChecked() ? WorkoutPlan.Load.NONE
                        : loadType.getSelectedItemPosition() == 1 ? WorkoutPlan.Load.ASSISTED
                        : WorkoutPlan.Load.ADDED;
                e.kilograms = kg; e.perSide = sides.isChecked();
                e.notes = notesOption.isChecked() ? notes.getText().toString().trim() : "";
                capture(false); WorkoutStore.saveDraft(this, draft);
                dialog.dismiss(); renderBuilder();
            } catch (IllegalArgumentException error) { toast("Vérifiez les nombres et la charge en kg."); }
        }));
        dialog.show();
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 31 && resultCode == RESULT_OK && data != null) {
            JSONObject catalog = CatalogStore.find(this, data.getStringExtra("exercise_id"));
            if (catalog != null) {
                draft.exercises.add(CatalogStore.asPlanExercise(catalog));
                WorkoutStore.saveDraft(this, draft); renderBuilder();
            }
        }
    }
    private void renderTemplates() {
        editing = false; runningScreen = false; formRoot("Mes modèles", "Chargez, dupliquez ou lancez une séance.");
        add(root, button("← Retour à la création", false, v -> renderBuilder()), 15);
        JSONArray list = WorkoutStore.templates(this);
        if (list.length() == 0) add(root, text("Aucun modèle enregistré.", 16, UiKit.MUTED, false), 22);
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i); if (item == null) continue;
            WorkoutPlan p = WorkoutStore.fromJson(item);
            LinearLayout card = UiKit.column(this); card.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 12),
                    UiKit.dp(this, 14), UiKit.dp(this, 12));
            card.setBackground(UiKit.background(this, UiKit.CARD, 15)); add(root, card, 12);
            add(card, text(p.name + " · " + modeName(p.mode), 19, UiKit.TEXT, true), 0);
            add(card, text(p.exercises.size() + " exercices", 14, UiKit.MUTED, false), 6);
            add(card, button("Modifier", false, v -> {
                draft = p.copy(); WorkoutStore.saveDraft(this, draft); renderBuilder();
            }), 10);
            add(card, button("Démarrer", true, v -> {
                try { p.validate(); previewAndStart(p); }
                catch (IllegalArgumentException error) { toast(error.getMessage()); }
            }), 7);
            add(card, button("Dupliquer", false, v -> {
                WorkoutPlan copy = p.copy(); copy.id = ""; copy.name += " (copie)";
                WorkoutStore.upsertTemplate(this, copy); renderTemplates();
            }), 7);
            add(card, button("Supprimer", false, v -> new AlertDialog.Builder(this)
                    .setMessage("Supprimer le modèle « " + p.name + " » ?")
                    .setNegativeButton("Annuler", null).setPositiveButton("Supprimer", (d, which) -> {
                        WorkoutStore.deleteTemplate(this, p.id); renderTemplates();
                    }).show()), 7);
        }
    }
    private void previewAndStart(WorkoutPlan p) {
        WorkoutEngine preview = new WorkoutEngine(p, SystemClock.elapsedRealtime());
        StringBuilder detail = new StringBuilder(modeName(p.mode)).append(" · ")
                .append(p.exercises.size()).append(" exercices\n");
        for (int i = 0; i < p.exercises.size(); i++)
            detail.append(i + 1).append(". ").append(p.exercises.get(i).name).append('\n');
        if (p.mode == WorkoutPlan.Mode.PYRAMID)
            detail.append("\nMarches : ").append(p.pyramidTargets()).append('\n');
        else if (p.mode == WorkoutPlan.Mode.EMOM)
            detail.append("\n").append(p.emomCycles).append(" cycles de ")
                    .append(p.emomIntervalSec).append(" secondes\n");
        else if (p.mode == WorkoutPlan.Mode.AMRAP)
            detail.append("\nDurée : ").append(p.amrapDurationSec).append(" secondes\n");
        else detail.append("\n").append(preview.steps.size()).append(" étapes avec repos\n");
        new AlertDialog.Builder(this).setTitle("Vérifier la séance")
                .setMessage(detail.toString()).setNegativeButton("Modifier", null)
                .setPositiveButton("Démarrer", (d, which) -> {
                    try {
                        startService(new Intent(this, SessionService.class).setAction(SessionService.ACTION_STOP));
                        Intent intent = new Intent(this, WorkoutService.class).setAction(WorkoutService.START)
                                .putExtra("plan", WorkoutStore.planJson(p).toString());
                        startForegroundService(intent);
                        hadRunning = true; pendingStartUntil = SystemClock.elapsedRealtime() + 3000;
                        renderRun();
                    } catch (JSONException error) { toast("Impossible de préparer la séance."); }
                }).show();
    }
    private void command(String action) {
        startService(new Intent(this, WorkoutService.class).setAction(action));
        handler.postDelayed(this::refreshRun, 160);
    }
    private void confirm(String title, String message, Runnable action) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Confirmer", (d, which) -> action.run()).show();
    }
    private String time(long ms) {
        long secs = ms / 1000;
        return String.format(Locale.FRANCE, "%02d:%02d:%02d", secs / 3600,
                secs / 60 % 60, secs % 60);
    }
    private void renderRun() {
        editing = false; runningScreen = true; summaryScreen = false; displayedStep = "";
        formRoot("Séance en cours", "Chaque validation lance automatiquement la suite.");
        LinearLayout hero = UiKit.column(this); hero.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 22),
                UiKit.dp(this, 18), UiKit.dp(this, 22));
        hero.setBackground(UiKit.background(this, UiKit.CARD, 20)); add(root, hero, 22);
        runTitle = text("Démarrage…", 25, UiKit.MINT, true); add(hero, runTitle, 0);
        runTime = text("00:00", 53, UiKit.TEXT, true); add(hero, runTime, 14);
        runMeta = text("Préparation de la séance", 18, UiKit.TEXT, false); add(hero, runMeta, 9);
        runNext = text("", 15, UiKit.MUTED, false); add(hero, runNext, 12);
        actualPanel = UiKit.column(this); add(root, actualPanel, 13);
        doneButton = button("Terminé", true, v -> completeOrSkip()); add(root, doneButton, 13);
        pauseButton = button("Pause", false, v -> {
            WorkoutEngine s = WorkoutService.current;
            command(s != null && s.paused() ? WorkoutService.RESUME : WorkoutService.PAUSE);
        }); add(root, pauseButton, 9);
        skipButton = button("Passer cette série", false, v -> confirm("Passer la série",
                "Elle sera notée comme passée, sans répétitions validées.", () -> command(WorkoutService.SKIP)));
        add(root, skipButton, 9);
        previousButton = button("Série précédente", false, v -> confirm("Revenir à la série précédente",
                "Sa dernière validation sera effacée et son chronomètre redémarrera.",
                () -> command(WorkoutService.PREVIOUS)));
        add(root, previousButton, 9);
        add(root, button("Terminer la séance", false, v -> confirm("Terminer la séance",
                "La progression actuelle sera conservée dans l’historique comme séance interrompue.",
                () -> command(WorkoutService.STOP))), 18);
        refreshRun();
    }
    private void setActualInputs(WorkoutEngine engine, WorkoutEngine.Step step) {
        actualPanel.removeAllViews(); actual = left = right = null;
        if (step.kind != WorkoutEngine.Kind.WORK) return;
        WorkoutPlan.Exercise e = engine.plan.exercises.get(step.exerciseIndex);
        if (e.perSide) {
            left = UiKit.input(this, actualPanel, "Répétitions gauche", "0", true);
            right = UiKit.input(this, actualPanel, "Répétitions droite", "0", true);
        } else actual = UiKit.input(this, actualPanel, "Répétitions réellement effectuées",
                String.valueOf(step.targetReps), true);
        add(actualPanel, text("Validez quand votre série est finie : le repos démarre immédiatement.",
                14, UiKit.MUTED, false), 10);
    }
    private void completeOrSkip() {
        WorkoutEngine engine = WorkoutService.current;
        if (engine == null || engine.paused() || engine.step() == null) return;
        WorkoutEngine.Step step = engine.step();
        if (step.kind != WorkoutEngine.Kind.WORK) { command(WorkoutService.SKIP); return; }
        try {
            int count, l = -1, r = -1;
            if (left != null) {
                l = parse(left, 0, 10000); r = parse(right, 0, 10000); count = l + r;
            } else count = parse(actual, 0, 10000);
            startService(new Intent(this, WorkoutService.class).setAction(WorkoutService.DONE)
                    .putExtra("actual", count).putExtra("left", l).putExtra("right", r));
            handler.postDelayed(this::refreshRun, 180);
        } catch (IllegalArgumentException error) { toast(error.getMessage()); }
    }
    private String phaseName(WorkoutEngine.Kind kind) {
        switch (kind) {
            case PREPARE: return "Préparez-vous";
            case WARMUP: return "Échauffement";
            case REST_SERIES: return "Repos entre séries";
            case REST_EXERCISE: return "Repos entre exercices";
            case REST_CIRCUIT: return "Repos du tour";
            case REST_EMOM: return "Repos jusqu’au prochain départ";
            case COOLDOWN: return "Retour au calme";
            default: return "À vous de jouer";
        }
    }
    private String nextDescription(WorkoutEngine e) {
        int index = e.index() + 1;
        if (index >= e.steps.size()) return e.plan.mode == WorkoutPlan.Mode.AMRAP
                ? "Puis : nouveau tour" : "Puis : fin de séance";
        WorkoutEngine.Step next = e.steps.get(index);
        return "Puis : " + (next.kind == WorkoutEngine.Kind.WORK
                ? e.plan.exercises.get(next.exerciseIndex).name : phaseName(next.kind));
    }
    private void refreshRun() {
        if (!runningScreen || runTitle == null) return;
        WorkoutEngine engine = WorkoutService.current;
        if (engine == null) {
            if (SystemClock.elapsedRealtime() < pendingStartUntil || WorkoutStore.hasActive(this)) return;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            runningScreen = false;
            if (hadRunning) renderSummary(); else renderBuilder();
            return;
        }
        hadRunning = true;
        WorkoutEngine.Step step = engine.step();
        if (step == null) return;
        String key = eKey(engine, step);
        if (!key.equals(displayedStep)) {
            displayedStep = key; setActualInputs(engine, step);
        }
        boolean working = step.kind == WorkoutEngine.Kind.WORK;
        runTitle.setText(working ? engine.plan.exercises.get(step.exerciseIndex).name : phaseName(step.kind));
        boolean countingUp = working && step.durationMs == 0;
        long displayed = engine.displayMs(SystemClock.elapsedRealtime());
        runTime.setText(time(countingUp ? displayed : (displayed + 999) / 1000 * 1000));
        if (working) {
            WorkoutPlan.Exercise x = engine.plan.exercises.get(step.exerciseIndex);
            String goals = "Série " + step.series + "/" + step.seriesTotal
                    + (step.targetReps > 0 ? " · objectif " + step.targetReps + " répétitions" : " · répétitions libres")
                    + (x.load == WorkoutPlan.Load.NONE ? "" : "\n" + x.kilograms + " kg "
                    + (x.load == WorkoutPlan.Load.ADDED ? "ajoutés" : "d’assistance"))
                    + (x.notes.isEmpty() ? "" : "\nNote : " + x.notes);
            if (engine.plan.mode == WorkoutPlan.Mode.AMRAP)
                goals += "\nTour " + engine.round() + " · temps global " + time(engine.amrapRemaining(
                        SystemClock.elapsedRealtime()));
            runMeta.setText(goals);
        } else runMeta.setText(engine.plan.name + (engine.plan.mode == WorkoutPlan.Mode.AMRAP
                ? " · tour " + engine.round() : ""));
        runNext.setText(nextDescription(engine));
        doneButton.setText(working ? "✓ Série terminée · lancer le repos" :
                step.kind == WorkoutEngine.Kind.REST_EMOM ? "Départ automatique à la minute suivante"
                : step.isRest() ? "Passer le repos" : "Passer cette étape");
        doneButton.setEnabled(!engine.paused() && step.kind != WorkoutEngine.Kind.REST_EMOM);
        pauseButton.setText(engine.paused() ? "Reprendre" : "Pause");
        skipButton.setVisibility(working ? View.VISIBLE : View.GONE);
        previousButton.setEnabled(!engine.events.isEmpty());
        if (!engine.paused()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private String eKey(WorkoutEngine e, WorkoutEngine.Step step) {
        return e.index() + ":" + e.events.size() + ":" + e.round() + ":" + step.kind;
    }
    private void renderSummary() {
        editing = false; runningScreen = false; summaryScreen = true;
        formRoot("Récapitulatif", "Vos résultats restent sur ce téléphone.");
        add(root, button("Créer une autre séance", true, v -> { hadRunning = false; renderBuilder(); }), 15);
        JSONArray history = WorkoutStore.history(this);
        JSONObject entry = history.optJSONObject(0);
        if (entry == null) { add(root, text("Aucune séance à afficher.", 16, UiKit.MUTED, false), 18); return; }
        WorkoutPlan p = WorkoutStore.fromJson(entry.optJSONObject("plan"));
        add(root, text(p.name + " · " + (entry.optBoolean("completed") ? "terminée" : "interrompue"),
                22, UiKit.MINT, true), 20);
        JSONArray events = entry.optJSONArray("events");
        if (events == null) events = new JSONArray();
        add(root, text("Durée " + time(entry.optLong("durationSec") * 1000)
                + " · " + events.length() + " séries enregistrées"
                + (p.mode == WorkoutPlan.Mode.AMRAP ? " · " + entry.optInt("rounds") + " tours complets" : ""),
                17, UiKit.TEXT, false), 10);
        for (int i = 0; i < events.length(); i++) {
            JSONObject ev = events.optJSONObject(i);
            if (ev == null || ev.optInt("exercise") >= p.exercises.size()) continue;
            WorkoutPlan.Exercise x = p.exercises.get(ev.optInt("exercise"));
            String status = ev.optString("result");
            add(root, text(x.name + " · série " + ev.optInt("series")
                    + " · " + ("DONE".equals(status) ? ev.optInt("actual") + " répétitions"
                    : "EXPIRED".equals(status) ? "temps écoulé, non validée" : "passée")
                    + (ev.optInt("left", -1) >= 0 ? " (G " + ev.optInt("left")
                    + ", D " + ev.optInt("right") + ")" : "")
                    + (x.load == WorkoutPlan.Load.NONE ? "" : " · " + x.kilograms + " kg "
                    + (x.load == WorkoutPlan.Load.ADDED ? "ajoutés" : "d’assistance"))
                    + " · " + time(ev.optLong("elapsed")), 16, UiKit.TEXT, false), 15);
        }
    }
    @Override public void onBackPressed() {
        if (runningScreen) { finish(); return; }
        if (!editing && !summaryScreen) { renderBuilder(); return; }
        super.onBackPressed();
    }
}
