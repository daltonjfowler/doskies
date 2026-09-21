package app.doskies;

import android.Manifest;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        render();

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
        LinearLayout box = card(page);
        text(box, "WIDGET APPEARANCE", 11, orange, false);
        text(box, "The DOS/pixel look ships in a later, hand-authored pass. Nothing to configure here yet.", 12, muted, false);
    }

    private void buildAboutCard(LinearLayout page) {
        LinearLayout box = card(page);
        text(box, "ABOUT", 11, orange, false);
        text(box, getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME, 14, ink, false);
        text(box, "No trackers, no ads, no account. Your location and settings stay on this device.", 12, muted, false);
        text(box, "Weather data by Open-Meteo, CC BY 4.0. See CREDITS.md.", 12, muted, false);
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
