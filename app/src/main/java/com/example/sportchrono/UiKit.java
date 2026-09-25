package com.example.sportchrono;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class UiKit {
    static final int BG = 0xff0d1516, CARD = 0xff1a2727, MINT = 0xffb8f078;
    static final int TEXT = 0xfff5f7f1, MUTED = 0xffb3c4bd;
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
        t.setLineSpacing(dp(a, 2), 1.0f);
        return t;
    }
    static Button button(Activity a, String label, boolean primary, View.OnClickListener click) {
        Button b = new Button(a); b.setText(label); b.setAllCaps(false); b.setTextSize(16);
        b.setTextColor(primary ? BG : TEXT); b.setMinHeight(dp(a, 56));
        b.setPadding(dp(a, 10), 0, dp(a, 10), 0);
        b.setBackground(background(a, primary ? MINT : 0xff283b3a, 16));
        b.setOnClickListener(click); return b;
    }
    static EditText input(Activity a, LinearLayout panel, String title, String value, boolean numeric) {
        add(a, panel, text(a, title, 15, MUTED, true), 14);
        EditText field = new EditText(a); field.setSingleLine(true); field.setText(value);
        field.setTextColor(TEXT); field.setTextSize(18); field.setSelectAllOnFocus(true);
        field.setHintTextColor(MUTED);
        field.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setBackground(background(a, CARD, 13));
        field.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
        field.setMinHeight(dp(a, 52));
        add(a, panel, field, 7); return field;
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
        scroll.setClipToPadding(true); scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(BG); root.setPadding(dp(a, 18), dp(a, 18), dp(a, 18), dp(a, 48));
        scroll.addView(root); a.setContentView(scroll); insets(a, scroll, null); return scroll;
    }
    static void configureWindow(Activity a) {
        a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        a.getWindow().setStatusBarColor(BG);
        a.getWindow().setNavigationBarColor(BG);
    }
    static void insets(Activity a, View container, View navigation) {
        container.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, imeBottom = 0;
            boolean keyboard;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top; bottom = bars.bottom;
                keyboard = insets.isVisible(WindowInsets.Type.ime());
                if (keyboard) imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
                android.graphics.Rect frame = new android.graphics.Rect();
                v.getWindowVisibleDisplayFrame(frame);
                keyboard = bottom > dp(a, 120)
                        || v.getRootView().getHeight() - frame.bottom > dp(a, 160);
            }
            v.setPadding(0, top, 0, keyboard ? Math.max(bottom, imeBottom) : bottom);
            if (navigation != null) navigation.setVisibility(keyboard ? View.GONE : View.VISIBLE);
            if (keyboard) v.postDelayed(() -> {
                View focused = a.getCurrentFocus();
                ViewParent parent = focused == null ? null : focused.getParent();
                while (parent != null && !(parent instanceof ScrollView)) parent = parent.getParent();
                if (focused != null && parent instanceof ScrollView) {
                    ScrollView scroll = (ScrollView) parent;
                    int[] field = new int[2], area = new int[2];
                    focused.getLocationOnScreen(field); scroll.getLocationOnScreen(area);
                    int safeBottom = area[1] + scroll.getHeight() - scroll.getPaddingBottom()
                            - dp(a, 16);
                    int overlap = field[1] + focused.getHeight() - safeBottom;
                    if (overlap > 0) scroll.scrollBy(0, overlap);
                }
            }, 90);
            return insets;
        });
        container.requestApplyInsets();
    }
    static FrameLayout photoHero(Activity a, int drawable, String eyebrow, String title, String subtitle) {
        LinearLayout caption = column(a);
        FrameLayout frame = new FrameLayout(a) {
            @Override protected void onMeasure(int widthSpec, int ignoredHeightSpec) {
                int height = dp(a, 194);
                super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                int needed = caption.getMeasuredHeight() + dp(a, 18);
                if (needed > height) super.onMeasure(widthSpec,
                        View.MeasureSpec.makeMeasureSpec(needed, View.MeasureSpec.EXACTLY));
            }
        };
        frame.setBackground(background(a, CARD, 22)); frame.setClipToOutline(true);
        ImageView photo = new ImageView(a); photo.setImageResource(drawable);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x4d091516, 0xb3091516, 0xff0d1516});
        View overlay = new View(a); overlay.setBackground(shade);
        frame.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        caption.setPadding(dp(a, 20), dp(a, 18), dp(a, 20), dp(a, 20));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM);
        frame.addView(caption, cp);
        add(a, caption, text(a, eyebrow.toUpperCase(java.util.Locale.FRANCE), 12, MINT, true), 0);
        add(a, caption, text(a, title, 27, TEXT, true), 8);
        add(a, caption, text(a, subtitle, 14, TEXT, false), 7);
        frame.setContentDescription(title + ". " + subtitle);
        return frame;
    }
}
