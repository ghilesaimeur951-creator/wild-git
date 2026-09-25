package com.example.sportchrono;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
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
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = 0xff101619, CARD = 0xff1b2629, MINT = 0xff9ff0b1;
    private static final int TEXT = 0xfff3f7f4, MUTED = 0xffa8b9b3;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int tab = 0;
    private LinearLayout root, form;
    private TextView statusTitle, statusTime, statusMeta, alarmStatus, lapList;
    private Button pauseButton, stopButton;
    private int laps = 0;
    private final Runnable update = new Runnable() {
        public void run() { refresh(); handler.postDelayed(this, 100); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
        build();
    }
    @Override protected void onResume() {
        super.onResume();
        if (Signals.prefs(this).getBoolean("alarm_enabled", false)) AlarmScheduler.scheduleDaily(this);
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
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setTextSize(15);
        b.setTextColor(primary ? BG : TEXT);
        b.setBackground(surface(primary ? MINT : 0xff304145, 14));
        b.setOnClickListener(click);
        b.setMinHeight(dp(48));
        return b;
    }
    private void build() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        root = column(); root.setPadding(dp(20), dp(26), dp(20), dp(40));
        scroll.addView(root); setContentView(scroll);
        add(root, text("SPORT CHRONO", 13, MINT, true), 0);
        add(root, text("À votre rythme.", 30, TEXT, true), 8);
        add(root, text("Un entraînement clair, du premier bip au dernier.", 14, MUTED, false), 5);

        LinearLayout status = column(); status.setPadding(dp(20), dp(20), dp(20), dp(20));
        status.setBackground(surface(CARD, 20)); add(root, status, 24);
        statusTitle = text("Prêt à démarrer", 16, MINT, true); add(status, statusTitle, 0);
        statusTime = text("00:00", 52, TEXT, true); add(status, statusTime, 12);
        statusMeta = text("Choisissez un mode ci-dessous", 14, MUTED, false); add(status, statusMeta, 4);
        LinearLayout controls = new LinearLayout(this);
        pauseButton = button("Pause", false, v -> control(SessionService.ACTION_PAUSE));
        stopButton = button("Arrêter", false, v -> control(SessionService.ACTION_STOP));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(48), 1); half.rightMargin = dp(8);
        controls.addView(pauseButton, half);
        controls.addView(stopButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        add(status, controls, 16);

        String[] names = {"Chrono", "Minuteur", "Intervalles", "Alarme", "Réglages"};
        ScrollView tabsScroll = new ScrollView(this); // Horizontal chips are in a wrapped row below.
        LinearLayout nav = column(); add(root, nav, 20);
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            for (int i = row * 3; i < Math.min(names.length, row * 3 + 3); i++) {
                final int selected = i;
                Button b = button(names[i], i == tab, v -> { tab = selected; build(); });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1);
                if (i % 3 != 2) p.rightMargin = dp(7);
                line.addView(b, p);
            }
            add(nav, line, row == 0 ? 0 : 7);
        }
        form = column(); add(root, form, 20);
        switch (tab) {
            case 0: stopwatchForm(); break;
            case 1: timerForm(); break;
            case 2: intervalForm(); break;
            case 3: alarmForm(); break;
            default: settingsForm();
        }
        refresh();
    }
    private void header(String title, String subtitle) {
        add(form, text(title, 23, TEXT, true), 0);
        add(form, text(subtitle, 14, MUTED, false), 7);
    }
    private EditText number(String label, String initial) {
        add(form, text(label, 14, MUTED, true), 18);
        EditText input = new EditText(this); input.setSingleLine(true);
        input.setInputType(2); input.setText(initial); input.setTextColor(TEXT);
        input.setTextSize(20); input.setSelectAllOnFocus(true);
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(MINT));
        add(form, input, 2); return input;
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
                v -> { laps = 0; start(SessionEngine.Mode.STOPWATCH, 0, 1, 0, 1); }), 22);
        add(form, button("Marquer un tour", false, v -> {
            SessionEngine s = SessionService.current;
            if (s != null && s.mode == SessionEngine.Mode.STOPWATCH) {
                laps++;
                lapList.setText("Tour " + laps + "  ·  " + format(s.displayMs(SystemClock.elapsedRealtime()), false)
                        + "\n" + lapList.getText());
            }
        }), 10);
        lapList = text("", 16, MUTED, false); add(form, lapList, 14);
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
        EditText rounds = number("Nombre de tours (1 à 100)", "4");
        EditText prep = number("Préparation · secondes (0 à 3600)", "5");
        EditText work = number("Effort · secondes (1 à 3600)", "30");
        EditText rest = number("Repos · secondes (0 à 3600)", "10");
        add(form, text("Un repos est inclus après chaque tour, même le dernier. Un bip marque chaque transition et les cinq dernières secondes de chaque phase.",
                14, MUTED, false), 20);
        add(form, button("Lancer la séance", true, v -> attempt(() -> start(SessionEngine.Mode.INTERVAL,
                value(prep, 0, 3600), value(work, 1, 3600), value(rest, 0, 3600),
                value(rounds, 1, 100)))), 20);
    }
    private void alarmForm() {
        header("Alarme quotidienne", "Choisissez une heure ; l’alarme sonne chaque jour.");
        SharedPreferences p = Signals.prefs(this);
        Button time = button(String.format(Locale.FRANCE, "Heure  ·  %02d:%02d",
                p.getInt("hour", 7), p.getInt("minute", 0)), false, null);
        time.setOnClickListener(v -> new TimePickerDialog(this, (picker, hour, minute) -> {
            p.edit().putInt("hour", hour).putInt("minute", minute).apply();
            AlarmScheduler.scheduleDaily(this); build();
        }, p.getInt("hour", 7), p.getInt("minute", 0), true).show());
        add(form, time, 20);
        CheckBox enabled = new CheckBox(this); enabled.setText("Activer l’alarme quotidienne");
        enabled.setTextColor(TEXT); enabled.setChecked(p.getBoolean("alarm_enabled", false));
        enabled.setOnCheckedChangeListener((button, checked) -> {
            p.edit().putBoolean("alarm_enabled", checked).apply();
            if (checked) {
                AlarmScheduler.scheduleDaily(this);
                if (!AlarmScheduler.exactAllowed(this)) {
                    Toast.makeText(this, "Autorisez les alarmes exactes pour une sonnerie à l’heure précise.",
                            Toast.LENGTH_LONG).show();
                    try { startActivity(AlarmScheduler.permissionSettings(this)); }
                    catch (RuntimeException ignored) { }
                }
            } else AlarmScheduler.cancel(this);
            refresh();
        });
        add(form, enabled, 12);
        alarmStatus = text("", 14, MUTED, false); add(form, alarmStatus, 12);
        add(form, button("Autoriser les alarmes exactes", false, v -> {
            try { startActivity(AlarmScheduler.permissionSettings(this)); }
            catch (RuntimeException error) { Toast.makeText(this, "Réglage indisponible sur ce téléphone.", Toast.LENGTH_SHORT).show(); }
        }), 18);
        add(form, text("La notification permet d’arrêter la sonnerie ou de la reporter de cinq minutes. L’alarme est reprogrammée après un redémarrage du téléphone.",
                14, MUTED, false), 18);
    }
    private void settingsForm() {
        header("Sons & autorisations", "Les bips utilisent le canal d’alarme du téléphone.");
        SharedPreferences p = Signals.prefs(this);
        CheckBox sound = new CheckBox(this); sound.setText("Sons activés"); sound.setTextColor(TEXT);
        sound.setChecked(p.getBoolean("sound", true));
        sound.setOnCheckedChangeListener((v, checked) -> p.edit().putBoolean("sound", checked).apply());
        add(form, sound, 18);
        CheckBox vibrate = new CheckBox(this); vibrate.setText("Vibration activée"); vibrate.setTextColor(TEXT);
        vibrate.setChecked(p.getBoolean("vibrate", true));
        vibrate.setOnCheckedChangeListener((v, checked) -> p.edit().putBoolean("vibrate", checked).apply());
        add(form, vibrate, 6);
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
        add(form, button("Réglage du volume système", false, v ->
                startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS))), 10);
        add(form, text("Les notifications et les alarmes exactes sont autorisées séparément par Android. Internet est déclaré pour de futures fonctions ; aucune connexion n’est utilisée par ces chronomètres.",
                14, MUTED, false), 20);
        add(form, button("Autoriser les notifications", false, v -> {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
            else Toast.makeText(this, "Gérées par les paramètres Android.", Toast.LENGTH_SHORT).show();
        }), 18);
    }
    private String format(long ms, boolean countdown) {
        long seconds = countdown ? (ms + 999) / 1000 : ms / 1000;
        return String.format(Locale.FRANCE, "%02d:%02d:%02d", seconds / 3600,
                (seconds / 60) % 60, seconds % 60);
    }
    private void refresh() {
        if (statusTitle == null) return;
        SessionEngine s = SessionService.current;
        boolean active = s != null;
        pauseButton.setVisibility(active ? View.VISIBLE : View.GONE);
        stopButton.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) {
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
        } else {
            statusTitle.setText("Prêt à démarrer"); statusTime.setText("00:00:00");
            statusMeta.setText("Choisissez un mode ci-dessous");
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (alarmStatus != null && tab == 3) {
            boolean enabled = Signals.prefs(this).getBoolean("alarm_enabled", false);
            alarmStatus.setText(enabled ? (AlarmScheduler.exactAllowed(this)
                    ? "Alarme active · heure précise autorisée"
                    : "Alarme active · Android peut décaler la sonnerie tant que l’accès exact est refusé")
                    : "Alarme désactivée");
        }
    }
}
