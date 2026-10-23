package de.local.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Schlichter Home-Screen im Apple-TV-Stil fuer Fire TV:
 * dunkler Verlauf, grosse Uhr, eine Reihe grosser Kacheln, die beim Fokus wachsen,
 * und ein eigener schwarzer Bildschirmschoner mit grosser Uhr.
 */
public class MainActivity extends Activity {

    // ---- Apps im Dock (Reihenfolge = Reihenfolge auf dem Bildschirm) ----
    private static final AppEntry[] APPS = new AppEntry[] {
            new AppEntry("YouTube",
                    "com.amazon.firetv.youtube", "com.google.android.youtube.tv"),
            new AppEntry("Netflix",
                    "com.netflix.ninja"),
            new AppEntry("Prime Video",
                    "com.amazon.firebat", "com.amazon.avod"),
            new AppEntry("IPTV Smarters",
                    "com.nst.iptvsmarterstvbox"),
    };

    private static final long IDLE_MS = 5 * 60 * 1000L;   // Bildschirmschoner nach 5 Minuten

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private FrameLayout root;
    private LinearLayout row;
    private TextView clockView;
    private TextView dateView;

    private FrameLayout saverLayer;
    private TextView saverClock;
    private boolean saverVisible = false;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.GERMANY);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY);

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 10_000);
        }
    };

    private final Runnable idle = new Runnable() {
        @Override
        public void run() {
            showSaver();
        }
    };

    // =====================================================================
    // Lebenszyklus
    // =====================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSaver();
        fillRow();
        updateClock();
        handler.removeCallbacks(tick);
        handler.post(tick);
        resetIdle();
        focusFirstTile();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(idle);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home-Taste gedrueckt, waehrend der Launcher schon offen ist
        hideSaver();
        resetIdle();
        focusFirstTile();
    }

    @Override
    public void onBackPressed() {
        // Ein Home-Screen wird nicht per Zurueck-Taste verlassen
    }

    // =====================================================================
    // Eingaben / Bildschirmschoner
    // =====================================================================

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (saverVisible) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                hideSaver();
                resetIdle();
            }
            return true; // Taste nur zum Aufwecken verwenden
        }
        resetIdle();
        return super.dispatchKeyEvent(event);
    }

    private void resetIdle() {
        handler.removeCallbacks(idle);
        handler.postDelayed(idle, IDLE_MS);
    }

    private void showSaver() {
        if (saverVisible) return;
        saverVisible = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        saverLayer.setVisibility(View.VISIBLE);
        saverLayer.bringToFront();
        moveSaverClock();
        updateClock();
    }

    private void hideSaver() {
        saverVisible = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (saverLayer != null) saverLayer.setVisibility(View.GONE);
    }

    /** Uhr jede Minute leicht verschieben, damit sich nichts in den Bildschirm einbrennt. */
    private void moveSaverClock() {
        int w = root.getWidth();
        int h = root.getHeight();
        if (w == 0 || h == 0) return;
        float maxX = Math.max(0, w - saverClock.getWidth()) / 2f;
        float maxY = Math.max(0, h - saverClock.getHeight()) / 2f;
        saverClock.setTranslationX((random.nextFloat() * 2 - 1) * maxX * 0.8f);
        saverClock.setTranslationY((random.nextFloat() * 2 - 1) * maxY * 0.8f);
    }

    // =====================================================================
    // Uhr
    // =====================================================================

    private String lastMinute = "";

    private void updateClock() {
        Date now = new Date();
        String time = timeFormat.format(now);
        clockView.setText(time);
        dateView.setText(dateFormat.format(now));
        saverClock.setText(time);
        if (saverVisible && !time.equals(lastMinute)) {
            moveSaverClock();
        }
        lastMinute = time;
    }

    // =====================================================================
    // Oberflaeche
    // =====================================================================

    private void buildUi() {
        root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF1B1D2A, 0xFF0B0B10, 0xFF000000});
        root.setBackground(bg);

        // Uhr und Datum oben rechts
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.END);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        hp.setMargins(0, dp(40), dp(64), 0);

        clockView = new TextView(this);
        clockView.setTextColor(Color.WHITE);
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        clockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockView.setGravity(Gravity.END);

        dateView = new TextView(this);
        dateView.setTextColor(0x99FFFFFF);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        dateView.setGravity(Gravity.END);

        header.addView(clockView);
        header.addView(dateView);
        root.addView(header, hp);

        // Kachel-Reihe in der Mitte
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        rp.setMargins(0, dp(40), 0, 0);
        root.addView(row, rp);

        // Bildschirmschoner: komplett schwarz, grosse Uhr
        saverLayer = new FrameLayout(this);
        saverLayer.setBackgroundColor(Color.BLACK);
        saverLayer.setVisibility(View.GONE);
        saverClock = new TextView(this);
        saverClock.setTextColor(0xFFB0B0B0);
        saverClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 150);
        saverClock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        saverClock.setIncludeFontPadding(false);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        saverLayer.addView(saverClock, sp);
        root.addView(saverLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void fillRow() {
        row.removeAllViews();
        List<View> tiles = new ArrayList<>();
        for (AppEntry app : APPS) {
            tiles.add(buildTile(app.label, loadBanner(app), app.isFullBanner, launcherFor(app)));
        }
        // Einstellungen
        tiles.add(buildTile("Einstellungen", loadSettingsIcon(), false, new Runnable() {
            @Override
            public void run() {
                openSettings();
            }
        }));
        for (View t : tiles) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(18), 0, dp(18), 0);
            row.addView(t, lp);
        }
    }

    private View buildTile(String label, Drawable image, boolean fullBanner, final Runnable action) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);

        final FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipChildren(true);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF24242A);
        cardBg.setCornerRadius(dp(18));
        card.setBackground(cardBg);
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setClipToOutline(true);

        final GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(dp(18));
        ring.setStroke(dp(3), Color.TRANSPARENT);
        card.setForeground(ring);

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(image);
        if (fullBanner) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(84), dp(84), Gravity.CENTER);
            card.addView(iv, ip);
        }

        final TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setAlpha(0f);
        text.setGravity(Gravity.CENTER);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float scale = hasFocus ? 1.14f : 1f;
                v.animate().scaleX(scale).scaleY(scale)
                        .translationZ(hasFocus ? dp(14) : 0)
                        .setDuration(180).start();
                ring.setStroke(dp(3), hasFocus ? 0xE6FFFFFF : Color.TRANSPARENT);
                text.animate().alpha(hasFocus ? 1f : 0f).setDuration(180).start();
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });

        col.addView(card, new LinearLayout.LayoutParams(dp(250), dp(141)));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(22);
        col.addView(text, tp);
        return col;
    }

    private void focusFirstTile() {
        if (row.getChildCount() == 0) return;
        View first = ((ViewGroup) row.getChildAt(0)).getChildAt(0);
        first.requestFocus();
    }

    // =====================================================================
    // Apps starten / Icons laden
    // =====================================================================

    private String installedPackage(AppEntry app) {
        PackageManager pm = getPackageManager();
        for (String pkg : app.packages) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return null;
    }

    private Runnable launcherFor(final AppEntry app) {
        return new Runnable() {
            @Override
            public void run() {
                String pkg = installedPackage(app);
                if (pkg == null) {
                    Toast.makeText(MainActivity.this,
                            app.label + " ist nicht installiert", Toast.LENGTH_LONG).show();
                    return;
                }
                PackageManager pm = getPackageManager();
                Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (i == null) i = pm.getLaunchIntentForPackage(pkg);
                if (i == null) {
                    Toast.makeText(MainActivity.this,
                            app.label + " laesst sich nicht starten", Toast.LENGTH_LONG).show();
                    return;
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        };
    }

    private Drawable loadBanner(AppEntry app) {
        String pkg = installedPackage(app);
        app.isFullBanner = false;
        if (pkg == null) return null;
        PackageManager pm = getPackageManager();
        try {
            Drawable banner = pm.getApplicationBanner(pkg);
            if (banner != null) {
                app.isFullBanner = true;
                return banner;
            }
            return pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private Drawable loadSettingsIcon() {
        try {
            return getPackageManager().getApplicationIcon("com.amazon.tv.settings.v2");
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void openSettings() {
        PackageManager pm = getPackageManager();
        try {
            Intent i = pm.getLeanbackLaunchIntentForPackage("com.amazon.tv.settings.v2");
            if (i == null) i = pm.getLaunchIntentForPackage("com.amazon.tv.settings.v2");
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            }
            Intent s = new Intent(Settings.ACTION_SETTINGS);
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(s);
        } catch (Exception e) {
            Toast.makeText(this, "Einstellungen lassen sich nicht oeffnen", Toast.LENGTH_LONG).show();
        }
    }

    // =====================================================================
    // Hilfsklassen
    // =====================================================================

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    private static class AppEntry {
        final String label;
        final String[] packages;
        boolean isFullBanner = false;

        AppEntry(String label, String... packages) {
            this.label = label;
            this.packages = packages;
        }
    }
}
