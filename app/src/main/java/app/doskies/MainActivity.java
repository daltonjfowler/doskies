package app.doskies;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.List;

/**
 * Settings/host screen (T4). Framework Activity and framework Views only, matching the Cloudflare
 * Usage Widget's MainActivity style: no androidx, no Material Components, no view binding, no new
 * runtime dependency. Plain and functional; the DOS/pixel look is a later, hand-authored pass
 * (see the WIDGET APPEARANCE card below, T5's seam).
 *
 * <p>Every control here writes straight to {@link Store} and then kicks {@link RefreshJob#now}
 * so the widget repaints with the new setting: a units flip needs a live refetch (Open-Meteo
 * returns the forecast already converted), a mode change needs the new coordinates fetched, and a
 * demo flip needs the widget to swap which data source it reads.
 *
 * <p>Fields for the interactive controls are package-visible (not private) so
 * {@code MainActivitySettingsTest} can drive them directly without a runtime dependency on any
 * test-only UI framework (Espresso, view matchers, and the like) that this project deliberately
 * does not take on.
 */
public final class MainActivity extends Activity {
    private static final int LOCATION_PERMISSION_REQUEST = 1;

    /** Update-notification tap sets this so onCreate scrolls to the Updates card. */
    public static final String EXTRA_SHOW_UPDATES = "app.doskies.SHOW_UPDATES";

    private final int ink = Color.rgb(239, 245, 238);
    private final int muted = Color.rgb(173, 187, 178);
    private final int orange = Color.rgb(255, 186, 122);
    private final int bg = Color.rgb(17, 25, 22);

    Switch unitsSwitch;
    RadioGroup modeGroup;
    RadioButton radioAuto;
    RadioButton radioFixed;
    LinearLayout citySearchSection;
    EditText cityQuery;
    Button searchButton;
    LinearLayout searchResults;
    TextView searchStatus;
    Switch demoSwitch;
    TextView statusLabel;
    TextView freshnessLabel;
    Button refreshButton;
    TextView refreshStatus;

    private ScrollView scrollRoot;
    private LinearLayout updatesCard;
    TextView updateStatus;
    Button updateButton;
    EditText updateUrlInput;
    private Updater.Info pendingUpdate;
    private boolean updateBusy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Notifications.createChannels(this);
        RefreshJob.scheduleUpdateCheck(this);
        render();
        maybeScrollToUpdates();

        Store store = new Store(this);
        if ("auto".equals(store.mode()) && !Locator.hasLocationPermission(this)) {
            requestPermissions(new String[] { Manifest.permission.ACCESS_COARSE_LOCATION }, LOCATION_PERMISSION_REQUEST);
        } else {
            RefreshJob.now(this);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            // Granted or denied, kick a refresh either way; denial just means Repository falls
            // back to the stored coordinates. Never block on the user's choice.
            RefreshJob.now(this);
        }
    }

    // ---- layout --------------------------------------------------------------

    private void render() {
        Store s = new Store(this);

        ScrollView scroll = new ScrollView(this);
        scrollRoot = scroll;
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(20), dp(24), dp(32));
        scroll.addView(page);
        setContentView(scroll);

        text(page, getString(R.string.app_name), 26, ink, true);
        text(page, "Private weather, no tracking.", 14, muted, false);

        buildStatusCard(page, s);
        buildUnitsCard(page, s);
        buildLocationCard(page, s);
        buildDemoCard(page, s);
        buildAppearanceSeam(page);
        buildUpdatesCard(page, s);
        buildAboutCard(page);
    }

    private void buildStatusCard(LinearLayout page, Store s) {
        LinearLayout box = card(page);
        text(box, "STATUS", 11, orange, false);
        statusLabel = text(box, "", 16, ink, true);
        freshnessLabel = text(box, "", 13, muted, false);
        refreshStatus = text(box, "", 13, muted, false);
        refreshButton = button(box, "Refresh now", () -> {
            refreshStatus.setText("Refreshing...");
            RefreshJob.now(this);
        });
        renderStatus();
    }

    private void buildUnitsCard(LinearLayout page, Store s) {
        LinearLayout box = card(page);
        text(box, "UNITS", 11, orange, false);
        LinearLayout row = row(box);
        TextView label = new TextView(this);
        label.setText("Temperature unit");
        label.setTextSize(14);
        label.setTextColor(ink);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        unitsSwitch = new Switch(this);
        boolean celsius = "C".equals(s.units());
        unitsSwitch.setChecked(celsius);
        unitsSwitch.setText(celsius ? "Celsius" : "Fahrenheit");
        unitsSwitch.setTextColor(ink);
        unitsSwitch.setContentDescription("Switch between Fahrenheit and Celsius");
        row.addView(unitsSwitch);
        unitsSwitch.setOnCheckedChangeListener((btn, checked) -> {
            unitsSwitch.setText(checked ? "Celsius" : "Fahrenheit");
            onUnitsChanged(checked);
        });
    }

    private void buildLocationCard(LinearLayout page, Store s) {
        LinearLayout box = card(page);
        text(box, "LOCATION", 11, orange, false);

        modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.VERTICAL);
        radioAuto = radio(modeGroup, "Use my location (auto)");
        radioFixed = radio(modeGroup, "Choose a city (fixed)");
        box.addView(modeGroup);
        boolean fixed = "fixed".equals(s.mode());
        modeGroup.check(fixed ? radioFixed.getId() : radioAuto.getId());
        modeGroup.setOnCheckedChangeListener((group, checkedId) -> onModeChanged(checkedId == radioFixed.getId()));

        citySearchSection = new LinearLayout(this);
        citySearchSection.setOrientation(LinearLayout.VERTICAL);
        citySearchSection.setVisibility(fixed ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(10);
        box.addView(citySearchSection, sp);

        text(citySearchSection, "Search for a city", 13, muted, false);
        LinearLayout searchRow = row(citySearchSection);
        cityQuery = new EditText(this);
        cityQuery.setSingleLine(true);
        cityQuery.setTextColor(ink);
        cityQuery.setHint("City name");
        cityQuery.setHintTextColor(muted);
        cityQuery.setContentDescription("City name to search for");
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        searchRow.addView(cityQuery, qp);
        searchButton = new Button(this);
        searchButton.setText("Search");
        searchButton.setAllCaps(false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMarginStart(dp(8));
        searchRow.addView(searchButton, bp);
        searchButton.setOnClickListener(v -> performSearch(cityQuery.getText().toString().trim()));

        searchStatus = text(citySearchSection, "", 12, muted, false);
        searchResults = new LinearLayout(this);
        searchResults.setOrientation(LinearLayout.VERTICAL);
        citySearchSection.addView(searchResults);
    }

    private void buildDemoCard(LinearLayout page, Store s) {
        LinearLayout box = card(page);
        text(box, "DEMO MODE", 11, orange, false);
        text(box, "Shows a made-up sample forecast, clearly marked DEMO, so you can preview the "
            + "widget without a live fetch. This is an explicit choice you turn on here; it is "
            + "never used to paper over a failed refresh. A real outage still shows as an error, "
            + "with your last good forecast kept on screen.", 12, muted, false);
        LinearLayout row = row(box);
        TextView demoLabel = new TextView(this);
        demoLabel.setText("Demo forecast");
        demoLabel.setTextSize(14);
        demoLabel.setTextColor(ink);
        row.addView(demoLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        demoSwitch = new Switch(this);
        demoSwitch.setChecked(s.demo());
        demoSwitch.setContentDescription("Toggle demo forecast mode");
        row.addView(demoSwitch);
        demoSwitch.setOnCheckedChangeListener((btn, checked) -> onDemoChanged(checked));
    }

    /** SEAM (T5): the DOS/pixel font, hand-drawn weather glyphs, palette, and widget composition
     * are authored by hand in the T5 art pass, never by a build agent (AGENTS.md). Nothing here
     * configures that look yet; this card is a placeholder for whatever knobs T5 decides it wants
     * (or none, if the palette ends up fixed). Do not add art-pass logic above this comment. */
    private void buildAppearanceSeam(LinearLayout page) {
        Store s = new Store(this);
        LinearLayout box = card(page);
        text(box, "WIDGET APPEARANCE", 11, orange, false);

        LinearLayout row = row(box);
        TextView label = new TextView(this);
        label.setText("Background opacity");
        label.setTextSize(14);
        label.setTextColor(ink);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView value = new TextView(this);
        value.setTextSize(14);
        value.setTextColor(muted);
        value.setText(s.widgetOpacity() + "%");
        row.addView(value);

        SeekBar bar = new SeekBar(this);
        bar.setMin(10);
        bar.setMax(100);
        bar.setProgress(s.widgetOpacity());
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int v = Math.max(10, progress);
                value.setText(v + "%");
                new Store(MainActivity.this).setWidgetOpacity(v);
                ForecastWidget.updateAll(MainActivity.this); // live preview on the home screen
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        box.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        text(box, "Lower it to let the wallpaper show through the panel.", 12, muted, false);
    }

    private void buildAboutCard(LinearLayout page) {
        LinearLayout box = card(page);
        text(box, "ABOUT", 11, orange, false);
        text(box, getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME, 14, ink, false);
        text(box, "No trackers, no ads, no account. Your location and settings stay on this device.", 12, muted, false);
        text(box, "Weather data by Open-Meteo, CC BY 4.0. See CREDITS.md.", 12, muted, false);
    }

    // ---- Phase 2: self-hosted updates -----------------------------------------

    /** Mirrors the Cloudflare Usage Widget's update card: current version, a manual check, and
     * (once available) a one-tap update that downloads, verifies, then hands off to the platform
     * installer. Nothing downloads or installs without a tap here. */
    private void buildUpdatesCard(LinearLayout page, Store s) {
        pendingUpdate = null;
        LinearLayout box = card(page);
        updatesCard = box;
        text(box, "UPDATES", 11, orange, false);
        updateStatus = text(box, getString(R.string.update_you_have, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), 13, muted, false);
        button(box, getString(R.string.update_check), this::checkForUpdate);
        updateButton = button(box, getString(R.string.update_button), this::startUpdate);
        updateButton.setVisibility(View.GONE);
        text(box, getString(R.string.update_source_label), 13, muted, false);
        updateUrlInput = new EditText(this);
        updateUrlInput.setSingleLine(true);
        updateUrlInput.setTextColor(ink);
        updateUrlInput.setTextSize(13);
        updateUrlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        updateUrlInput.setHint(R.string.update_source_hint);
        updateUrlInput.setHintTextColor(muted);
        updateUrlInput.setContentDescription("Update source URL");
        updateUrlInput.setText(s.updateUrl());
        box.addView(updateUrlInput, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** Runs Updater.check off the main thread (Repository's shared executor), never in a test and
     * never blocking the UI thread. Saves an edited update URL before checking. */
    void checkForUpdate() {
        pendingUpdate = null;
        updateButton.setVisibility(View.GONE);
        final String url = updateUrlInput.getText().toString().trim();
        updateStatus.setText(getString(R.string.update_checking));
        updateBusy = true;
        Repository.IO.execute(() -> {
            if (!url.isEmpty()) new Store(this).setUpdateUrl(url);
            final String base = new Store(this).updateUrl();
            final Updater.CheckResult r = Updater.check(BuildConfig.VERSION_CODE, base, new Updater.HttpSource());
            runOnUiThread(() -> { updateBusy = false; if (isDestroyed()) return; renderCheck(r); });
        });
    }

    void renderCheck(Updater.CheckResult r) {
        switch (r.status) {
            case AVAILABLE:
                pendingUpdate = r.info;
                String size = Updater.formatSize(r.info.size);
                if (r.info.notes == null || r.info.notes.isEmpty()) {
                    updateStatus.setText(getString(R.string.update_available_no_notes, r.info.versionName, r.info.versionCode, size));
                } else {
                    updateStatus.setText(getString(R.string.update_available, r.info.versionName, r.info.versionCode, size, r.info.notes));
                }
                updateButton.setEnabled(true);
                updateButton.setVisibility(View.VISIBLE);
                break;
            case UP_TO_DATE: updateStatus.setText(R.string.update_latest); break;
            case NO_NETWORK: updateStatus.setText(R.string.update_no_connection); break;
            default: updateStatus.setText(R.string.update_bad_shape); break;
        }
    }

    /** Tapping Update. GrapheneOS (and stock Android) gates installs from other apps; send the
     * user to turn that on first when it is not yet allowed. */
    void startUpdate() {
        if (pendingUpdate == null) return;
        if (!Updater.canInstall(this)) {
            updateStatus.setText(R.string.update_needs_permission);
            openUnknownSources();
            return;
        }
        final Updater.Info info = pendingUpdate;
        final String base = new Store(this).updateUrl();
        updateBusy = true;
        updateButton.setEnabled(false);
        updateStatus.setText(getString(R.string.update_downloading, 0));
        Repository.IO.execute(() -> {
            final Updater.DownloadResult dr = Updater.download(getApplicationContext(), base, info, new Updater.HttpSource(),
                pct -> runOnUiThread(() -> { if (!isDestroyed()) updateStatus.setText(getString(R.string.update_downloading, pct)); }));
            runOnUiThread(() -> { if (isDestroyed()) return; onDownloadDone(dr); });
        });
    }

    private void onDownloadDone(Updater.DownloadResult dr) {
        switch (dr.status) {
            case DONE:
                updateStatus.setText(R.string.update_installing);
                final java.io.File file = dr.file;
                Repository.IO.execute(() -> {
                    try {
                        Updater.install(getApplicationContext(), file);
                        updateBusy = false;
                    } catch (Exception e) {
                        runOnUiThread(() -> { updateBusy = false; if (isDestroyed()) return; updateStatus.setText(R.string.update_bad_shape); updateButton.setEnabled(true); });
                    }
                });
                break;
            case NO_NETWORK: updateBusy = false; updateStatus.setText(R.string.update_no_connection); updateButton.setEnabled(true); break;
            case CHECKSUM_MISMATCH: updateBusy = false; updateStatus.setText(R.string.update_checksum_mismatch); updateButton.setEnabled(true); break;
            default: updateBusy = false; updateStatus.setText(R.string.update_signature_mismatch); updateButton.setEnabled(true); break;
        }
    }

    private void openUnknownSources() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException ignored) { }
    }

    /** The update notification opens here with EXTRA_SHOW_UPDATES set; scroll straight to the card. */
    private void maybeScrollToUpdates() {
        if (!getIntent().getBooleanExtra(EXTRA_SHOW_UPDATES, false)) return;
        final ScrollView scroll = scrollRoot;
        final LinearLayout target = updatesCard;
        if (scroll == null || target == null) return;
        scroll.post(() -> {
            int y = 0;
            View v = target;
            while (v != null && v != scroll) {
                y += v.getTop();
                ViewParent parent = v.getParent();
                v = (parent instanceof View) ? (View) parent : null;
            }
            scroll.smoothScrollTo(0, y);
        });
    }

    // ---- wiring: each control's exact Store key + refresh ---------------------

    /** Units flip. Store.setUnits, then a refetch: Open-Meteo returns temperatures already
     * converted, so the stale unit's numbers must not linger until the next scheduled refresh. */
    void onUnitsChanged(boolean celsius) {
        new Store(this).setUnits(celsius ? "C" : "F");
        RefreshJob.now(this);
        renderStatus();
    }

    /** Mode flip. Store.setMode; auto also requests coarse location if not yet granted (never
     * blocking on the answer); fixed reveals the city search section instead. Either way, a
     * refetch follows so the widget picks up the new coordinates. */
    void onModeChanged(boolean fixed) {
        Store s = new Store(this);
        if (fixed) {
            s.setMode("fixed");
            citySearchSection.setVisibility(View.VISIBLE);
        } else {
            s.setMode("auto");
            citySearchSection.setVisibility(View.GONE);
            if (!Locator.hasLocationPermission(this)) {
                requestPermissions(new String[] { Manifest.permission.ACCESS_COARSE_LOCATION }, LOCATION_PERMISSION_REQUEST);
            }
        }
        RefreshJob.now(this);
        renderStatus();
    }

    /** Demo flip. Store.setDemo, then a repaint: ForecastWidget reads Store.demo() itself, and
     * Repository.refresh (run by RefreshJob.now below) makes no network call while demo is on. */
    void onDemoChanged(boolean on) {
        new Store(this).setDemo(on);
        RefreshJob.now(this);
        renderStatus();
    }

    /**
     * Runs Geocoding.search off the main thread (Repository's shared executor), never in a test
     * and never blocking the UI thread. Empty query is rejected locally with no network call; a
     * zero-result search and a network/parse failure both show a clear message and never crash.
     */
    void performSearch(String query) {
        if (query.isEmpty()) {
            searchStatus.setText("Type a city name to search.");
            return;
        }
        searchResults.removeAllViews();
        searchStatus.setText("Searching...");
        Repository.IO.execute(() -> {
            try {
                List<Geocoding.Result> results = Geocoding.search(query);
                runOnUiThread(() -> { if (!isDestroyed()) showResults(results); });
            } catch (Exception e) {
                String message = e instanceof IllegalArgumentException
                    ? e.getMessage() : "Could not search. Check your connection.";
                runOnUiThread(() -> { if (!isDestroyed()) searchStatus.setText(message); });
            }
        });
    }

    /** Renders a simple list of result buttons, one per candidate; picking one calls selectResult. */
    void showResults(List<Geocoding.Result> results) {
        searchResults.removeAllViews();
        if (results.isEmpty()) {
            searchStatus.setText("No matching cities found.");
            return;
        }
        searchStatus.setText("");
        for (Geocoding.Result r : results) {
            Button b = new Button(this);
            b.setText(resultLabel(r));
            b.setAllCaps(false);
            b.setTextColor(ink);
            b.setBackground(pill(Color.rgb(48, 65, 54)));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(6);
            b.setOnClickListener(v -> selectResult(r));
            searchResults.addView(b, p);
        }
    }

    /**
     * Picking a search result. Store.setLocation + Store.setPlaceLabel from the chosen result,
     * then a refetch so the widget shows the new city's forecast right away.
     */
    void selectResult(Geocoding.Result r) {
        Store s = new Store(this);
        s.setLocation(r.lat, r.lon);
        s.setPlaceLabel(placeLabelFor(r));
        searchResults.removeAllViews();
        cityQuery.setText("");
        searchStatus.setText("Location set to " + s.placeLabel() + ".");
        RefreshJob.now(this);
        renderStatus();
    }

    /** The full disambiguating label ("Medford, New Jersey (US)") shown on each result button. */
    static String resultLabel(Geocoding.Result r) {
        StringBuilder sb = new StringBuilder(r.name);
        if (!r.admin1.isEmpty()) sb.append(", ").append(r.admin1);
        if (!r.countryCode.isEmpty()) sb.append(" (").append(r.countryCode).append(")");
        return sb.toString();
    }

    /** The shorter label ("Medford, New Jersey") stored as Store.placeLabel and shown on the widget. */
    static String placeLabelFor(Geocoding.Result r) {
        return r.admin1.isEmpty() ? r.name : r.name + ", " + r.admin1;
    }

    /** Reflects the real current state: place label (with a DEMO marker when demo mode is on) and
     * the last-updated time or error, exactly as the widget would show them. */
    private void renderStatus() {
        Store s = new Store(this);
        boolean demo = s.demo();
        statusLabel.setText(demo ? "DEMO · " + s.placeLabel() : s.placeLabel());
        if (demo) freshnessLabel.setText("Demo data, not live.");
        else if (!s.error().isEmpty()) freshnessLabel.setText(s.error());
        else freshnessLabel.setText(ForecastWidget.checkedText(s.checked()));
    }

    // ---- small view builders, matching the Cloudflare Usage Widget's plain-framework style -----

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private TextView text(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        parent.addView(t);
        return t;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        box.setBackground(outlinedBox());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(14);
        parent.addView(box, p);
        return box;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(6);
        parent.addView(r, p);
        return r;
    }

    private RadioButton radio(RadioGroup group, String label) {
        RadioButton rb = new RadioButton(this);
        rb.setId(View.generateViewId());
        rb.setText(label);
        rb.setTextColor(ink);
        group.addView(rb);
        return rb;
    }

    private Button button(LinearLayout parent, String value, Runnable action) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextColor(ink);
        b.setBackground(pill(Color.rgb(48, 65, 54)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(8);
        parent.addView(b, p);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private GradientDrawable outlinedBox() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.rgb(23, 35, 29));
        d.setCornerRadius(dp(16));
        d.setStroke(dp(1), Color.rgb(57, 72, 62));
        return d;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(12));
        return d;
    }
}
