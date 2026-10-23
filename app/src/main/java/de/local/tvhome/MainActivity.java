package de.local.tvhome;

import android.app.Activity;
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
import android.util.DisplayMetrics;
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
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Home-Screen im Apple-TV-Stil fuer Fire TV.
 *
 * Alle Groessen sind auf eine Zeichenflaeche von 1920 x 1080 Pixeln ausgelegt und werden
 * auf die echte Bildschirmbreite umgerechnet. So sieht es auf jedem Fire TV gleich aus,
 * egal mit welcher Aufloesung oder Pixeldichte das System arbeitet.
 */
public class MainActivity extends Activity {

    // ---- Apps im Dock (Reihenfolge = Reihenfolge auf dem Bildschirm) ----
    private static final AppEntry[] APPS = new AppEntry[] {
            new AppEntry("YouTube", 0xFF2A1416,
                    "com.amazon.firetv.youtube", "com.google.android.youtube.tv"),
            new AppEntry("Netflix", 0xFF1A1012,
                    "com.netflix.ninja"),
            new AppEntry("Prime Video", 0xFF0E2233,
                    "com.amazon.firebat", "com.amazon.avod"),
            new AppEntry("IPTV Smarters", 0xFF14223A,
                    "com.nst.iptvsmarterstvbox"),
    };

    private static final long IDLE_MS = 5 * 60 * 1000L;   // Bildschirmschoner nach 5 Minuten

    // Groessen auf der 1920x1080-Flaeche
    private static final int TILE_W = 300;
    private static final int TILE_H = 169;
    private static final int SETTINGS_SIZE = 169;
    private static final int TILE_GAP_HALF = 24;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private float scale = 1f;

    private FrameLayout root;
    private LinearLayout row;
    private TextView clockView;
    private TextView dateView;

    private FrameLayout saverLayer;
    private TextView saverClock;
    private boolean saverVisible = false;

    private String lastMinute = "";

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
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.max(dm.widthPixels, dm.heightPixels);
        scale = w / 1920f;
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
        updateClock();
        saverLayer.post(new Runnable() {
            @Override
            public void run() {
                moveSaverClock();
            }
        });
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
        saverClock.setTranslationX((random.nextFloat() * 2 - 1) * maxX * 0.7f);
        saverClock.setTranslationY((random.nextFloat() * 2 - 1) * maxY * 0.7f);
    }

    // =====================================================================
    // Uhr
    // =====================================================================

    private void updateClock() {
        Date now = new Date();
        String time = timeFormat.format(now);
        clockView.setText(time);
        dateView.setText(dateFormat.format(now));
        saverClock.setText(time);
        if (saverVisible && !time.equals(lastMinute)) {
            saverLayer.post(new Runnable() {
                @Override
                public void run() {
                    moveSaverClock();
                }
            });
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

        // Dunkler Hintergrund mit weichem Lichtschein oben in der Mitte
        GradientDrawable bg = new GradientDrawable();
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bg.setGradientCenter(0.5f, 0.3f);
        bg.setGradientRadius(px(1500));
        bg.setColors(new int[] {0xFF2C3254, 0xFF12131D, 0xFF050508});
        root.setBackground(bg);

        // Titel oben links
        TextView title = new TextView(this);
        title.setText("Zuhause  ·  v1.1");
        title.setTextColor(0x80FFFFFF);
        setPx(title, 34);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        tp.setMargins(px(110), px(72), 0, 0);
        root.addView(title, tp);

        // Uhr und Datum oben rechts
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.END);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        hp.setMargins(0, px(56), px(110), 0);

        clockView = new TextView(this);
        clockView.setTextColor(Color.WHITE);
        setPx(clockView, 64);
        clockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockView.setGravity(Gravity.END);
        clockView.setIncludeFontPadding(false);

        dateView = new TextView(this);
        dateView.setTextColor(0x99FFFFFF);
        setPx(dateView, 28);
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
        rp.setMargins(0, px(70), 0, 0);
        root.addView(row, rp);

        // Bildschirmschoner: komplett schwarz, grosse Uhr
        saverLayer = new FrameLayout(this);
        saverLayer.setBackgroundColor(Color.BLACK);
        saverLayer.setVisibility(View.GONE);
        saverClock = new TextView(this);
        saverClock.setTextColor(0xFFB0B0B0);
        setPx(saverClock, 340);
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

        for (AppEntry app : APPS) {
            Drawable image = loadBanner(app);
            View tile = buildTile(app.label, image, app.isFullBanner, app.color,
                    TILE_W, TILE_H, launcherFor(app));
            addToRow(tile, 0);
        }

        // Einstellungen: quadratische Kachel mit etwas mehr Abstand
        View settings = buildTile("Einstellungen", loadSettingsIcon(), false, 0xFF22242E,
                SETTINGS_SIZE, SETTINGS_SIZE, new Runnable() {
                    @Override
                    public void run() {
                        openSettings();
                    }
                });
        addToRow(settings, 24);
    }

    private void addToRow(View tile, int extraLeftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(px(TILE_GAP_HALF + extraLeftMargin), 0, px(TILE_GAP_HALF), 0);
        row.addView(tile, lp);
    }

    private View buildTile(String label, Drawable image, boolean fullBanner, int color,
                           int w, int h, final Runnable action) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);

        final FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(color);
        cardBg.setCornerRadius(px(22));
        card.setBackground(cardBg);
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setClipToOutline(true);

        final GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(px(22));
        ring.setStroke(px(4), Color.TRANSPARENT);
        card.setForeground(ring);

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(image);
        if (fullBanner) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int icon = Math.round(h * 0.56f);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                    px(icon), px(icon), Gravity.CENTER);
            card.addView(iv, ip);
        }

        final TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(Color.WHITE);
        setPx(text, 28);
        text.setAlpha(0f);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(true);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.12f : 1f;
                v.animate().scaleX(s).scaleY(s)
                        .translationZ(hasFocus ? px(14) : 0)
                        .setDuration(180).start();
                ring.setStroke(px(4), hasFocus ? 0xF2FFFFFF : Color.TRANSPARENT);
                text.animate().alpha(hasFocus ? 1f : 0f).setDuration(180).start();
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });

        col.addView(card, new LinearLayout.LayoutParams(px(w), px(h)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(30);
        col.addView(text, lp);
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
    // Hilfen
    // =====================================================================

    /** Wert von der 1920x1080-Zeichenflaeche auf echte Pixel umrechnen. */
    private int px(float value) {
        return Math.round(value * scale);
    }

    private void setPx(TextView view, float size) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * scale);
    }

    private static class AppEntry {
        final String label;
        final int color;
        final String[] packages;
        boolean isFullBanner = false;

        AppEntry(String label, int color, String... packages) {
            this.label = label;
            this.color = color;
            this.packages = packages;
        }
    }
}
