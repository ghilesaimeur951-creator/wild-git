package com.example.sportchrono;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

public class CatalogActivity extends Activity {
    public static final String SELECT = "select_exercise";
    private static final String[] CATEGORIES = {"Toutes", "Tractions", "Dips", "Pompes",
            "Haut du corps", "Jambes", "Tronc et dos", "Autres"};
    private static final String[] UNITS = {"Répétitions", "Secondes", "Répétitions et secondes"};
    private static final String[] LOADS = {"Aucune", "Charge ajoutée", "Assistance"};
    private LinearLayout rows;
    private EditText search;
    private Spinner category;
    private CheckBox favorites, hidden;
    private boolean selecting;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); UiKit.configureWindow(this);
        selecting = getIntent().getBooleanExtra(SELECT, false);
        render();
    }
    private Spinner spinner(String[] labels, int selected) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        s.setAdapter(adapter); s.setSelection(selected); return s;
    }
    private void render() {
        LinearLayout root = UiKit.column(this); UiKit.screen(this, root);
        UiKit.add(this, root, UiKit.text(this, selecting ? "Choisir un exercice" : "Catalogue street workout",
                26, UiKit.TEXT, true), 0);
        UiKit.add(this, root, UiKit.text(this, "Rechercher, personnaliser et retrouver vos favoris.",
                15, UiKit.MUTED, false), 8);
        UiKit.add(this, root, UiKit.photoHero(this, R.drawable.pullup_photo,
                "MOUVEMENTS", "Votre répertoire.",
                "Des favoris pour composer votre prochaine séance."), 17);
        UiKit.add(this, root, UiKit.button(this, "Ajouter un exercice personnel", true,
                v -> edit(null)), 18);
        search = UiKit.input(this, root, "Recherche", "", false);
        search.setHint("Nom de l’exercice");
        category = spinner(CATEGORIES, 0); UiKit.add(this, root, category, 12);
        favorites = new CheckBox(this); favorites.setText("Favoris uniquement");
        favorites.setTextColor(UiKit.TEXT); UiKit.add(this, root, favorites, 4);
        hidden = new CheckBox(this); hidden.setText("Afficher les exercices masqués");
        hidden.setTextColor(UiKit.TEXT); UiKit.add(this, root, hidden, 0);
        rows = UiKit.column(this); UiKit.add(this, root, rows, 14);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { fill(); }
            public void afterTextChanged(Editable s) { }
        });
        category.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { fill(); }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        favorites.setOnCheckedChangeListener((button, checked) -> fill());
        hidden.setOnCheckedChangeListener((button, checked) -> fill());
        fill();
    }
    private void fill() {
        if (rows == null || search == null || category == null) return;
        rows.removeAllViews();
        JSONArray list = CatalogStore.all(this);
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        String filter = category.getSelectedItem() == null ? "Toutes" : category.getSelectedItem().toString();
        int shown = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i); if (item == null) continue;
            boolean isHidden = item.optBoolean("hidden"), fav = item.optBoolean("favorite");
            String cat = item.optString("category", "Autres");
            if ((isHidden && !hidden.isChecked()) || (favorites.isChecked() && !fav)
                    || (!"Toutes".equals(filter) && !filter.equals(cat)
                    && !("Autres".equals(filter) && !java.util.Arrays.asList(CATEGORIES).contains(cat)))
                    || !item.optString("name").toLowerCase(Locale.ROOT).contains(query)) continue;
            shown++;
            LinearLayout card = UiKit.column(this); card.setPadding(UiKit.dp(this, 15), UiKit.dp(this, 12),
                    UiKit.dp(this, 15), UiKit.dp(this, 12));
            card.setBackground(UiKit.background(this, UiKit.CARD, 14));
            UiKit.add(this, rows, card, 9);
            String label = item.optString("name") + (isHidden ? " · masqué" : "");
            LinearLayout heading = new LinearLayout(this);
            android.widget.Button nameButton = UiKit.button(this, label, selecting && !isHidden, v -> {
                if (selecting && !isHidden) {
                    setResult(RESULT_OK, new Intent().putExtra("exercise_id", item.optString("id")));
                    finish();
                } else edit(item);
            });
            nameButton.setTextSize(15); nameButton.setGravity(android.view.Gravity.CENTER_VERTICAL
                    | android.view.Gravity.START);
            heading.addView(nameButton, new LinearLayout.LayoutParams(0, UiKit.dp(this, 57), 1));
            android.widget.Button star = UiKit.button(this, fav ? "★" : "☆", false, v -> {
                try { item.put("favorite", !fav); CatalogStore.upsert(this, item); fill(); }
                catch (JSONException ignored) { }
            });
            star.setTextSize(24); star.setContentDescription(fav ? "Retirer des favoris" : "Ajouter aux favoris");
            LinearLayout.LayoutParams starSize = new LinearLayout.LayoutParams(UiKit.dp(this, 55),
                    UiKit.dp(this, 57)); starSize.leftMargin = UiKit.dp(this, 6);
            heading.addView(star, starSize);
            UiKit.add(this, card, heading, 0);
            UiKit.add(this, card, UiKit.text(this, cat + " · " + item.optString("unit"),
                    13, UiKit.MUTED, false), 6);
            LinearLayout actions = new LinearLayout(this);
            android.widget.Button edit = UiKit.button(this, "Modifier", false, v -> edit(item));
            actions.addView(edit, new LinearLayout.LayoutParams(0, UiKit.dp(this, 48), 1));
            android.widget.Button remove = UiKit.button(this, item.optBoolean("custom") ? "Supprimer" :
                    isHidden ? "Réactiver" : "Masquer", false, v -> {
                if (isHidden) {
                    try { item.put("hidden", false); CatalogStore.upsert(this, item); fill(); }
                    catch (JSONException ignored) { }
                } else new AlertDialog.Builder(this).setMessage("Retirer « " + item.optString("name") + " » ?")
                        .setNegativeButton("Annuler", null).setPositiveButton("Retirer", (d, which) -> {
                            CatalogStore.delete(this, item); fill();
                        }).show();
            });
            LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, UiKit.dp(this, 48), 1);
            half.leftMargin = UiKit.dp(this, 7); actions.addView(remove, half);
            UiKit.add(this, card, actions, 9);
        }
        if (shown == 0) UiKit.add(this, rows, UiKit.text(this, "Aucun exercice trouvé.",
                16, UiKit.MUTED, false), 12);
    }
    private void edit(JSONObject current) {
        LinearLayout panel = UiKit.column(this); panel.setPadding(UiKit.dp(this, 18), 0, UiKit.dp(this, 18), 0);
        EditText name = UiKit.input(this, panel, "Nom", current == null ? "" : current.optString("name"), false);
        EditText categoryName = UiKit.input(this, panel, "Catégorie", current == null ? "Autres"
                : current.optString("category"), false);
        String unit = current == null ? "REPS" : current.optString("unit", "REPS");
        Spinner units = spinner(UNITS, "SECONDS".equals(unit) ? 1 : "BOTH".equals(unit) ? 2 : 0);
        UiKit.add(this, panel, UiKit.text(this, "Mesure", 15, UiKit.MUTED, true), 14);
        UiKit.add(this, panel, units, 4);
        String load = current == null ? "NONE" : current.optString("load", "NONE");
        Spinner loads = spinner(LOADS, "ADDED".equals(load) ? 1 : "ASSISTED".equals(load) ? 2 : 0);
        UiKit.add(this, panel, UiKit.text(this, "Charge", 15, UiKit.MUTED, true), 14);
        UiKit.add(this, panel, loads, 4);
        CheckBox sides = new CheckBox(this); sides.setText("Compter jambe ou bras gauche et droit séparément");
        sides.setTextColor(UiKit.TEXT); sides.setChecked(current != null && current.optBoolean("perSide"));
        UiKit.add(this, panel, sides, 8);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(current == null ? "Nouvel exercice" : "Modifier l’exercice")
                .setView(scroll).setNegativeButton("Annuler", null).setPositiveButton("Enregistrer", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = name.getText().toString().trim();
            if (title.isEmpty()) { Toast.makeText(this, "Le nom est obligatoire.", Toast.LENGTH_SHORT).show(); return; }
            try {
                JSONObject item = current == null ? new JSONObject() : current;
                item.put("name", title).put("category", categoryName.getText().toString().trim())
                        .put("unit", new String[]{"REPS", "SECONDS", "BOTH"}[units.getSelectedItemPosition()])
                        .put("load", new String[]{"NONE", "ADDED", "ASSISTED"}[loads.getSelectedItemPosition()])
                        .put("perSide", sides.isChecked());
                CatalogStore.upsert(this, item); dialog.dismiss(); fill();
            } catch (JSONException ignored) { }
        }));
        dialog.show();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
}
