package com.example.sportchrono;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class UiKit {
    static final int BG = 0xff101619, CARD = 0xff1b2629, MINT = 0xff9ff0b1;
    static final int TEXT = 0xfff3f7f4, MUTED = 0xffa8b9b3;
    static int dp(Activity a, int size) {
        return (int) (size * a.getResources().getDisplayMetrics().density + .5f);
    }
    static LinearLayout column(Activity a) {
        LinearLayout l = new LinearLayout(a); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    static void add(Activity a, LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(a, top); parent.addView(child, params);
    }
    static GradientDrawable background(Activity a, int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color);
        d.setCornerRadius(dp(a, radius)); return d;
    }
    static TextView text(Activity a, String value, int size, int color, boolean bold) {
        TextView t = new TextView(a); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    static Button button(Activity a, String label, boolean primary, View.OnClickListener click) {
        Button b = new Button(a); b.setText(label); b.setAllCaps(false); b.setTextSize(16);
        b.setTextColor(primary ? BG : TEXT); b.setMinHeight(dp(a, 58));
        b.setPadding(dp(a, 10), 0, dp(a, 10), 0);
        b.setBackground(background(a, primary ? MINT : 0xff304145, 14));
        b.setOnClickListener(click); return b;
    }
    static EditText input(Activity a, LinearLayout panel, String title, String value, boolean numeric) {
        add(a, panel, text(a, title, 15, MUTED, true), 14);
        EditText field = new EditText(a); field.setSingleLine(true); field.setText(value);
        field.setTextColor(TEXT); field.setTextSize(20); field.setSelectAllOnFocus(true);
        field.setHintTextColor(MUTED);
        if (numeric) field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setBackgroundTintList(ColorStateList.valueOf(MINT));
        add(a, panel, field, 2); return field;
    }
    static int integer(EditText edit, int min, int max) {
        try {
            int value = Integer.parseInt(edit.getText().toString().trim());
            if (value >= min && value <= max) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Saisissez un nombre entre " + min + " et " + max + ".");
    }
    static ScrollView screen(Activity a, LinearLayout root) {
        ScrollView scroll = new ScrollView(a); scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG); root.setPadding(dp(a, 18), dp(a, 24), dp(a, 18), dp(a, 36));
        scroll.addView(root); a.setContentView(scroll); return scroll;
    }
}
