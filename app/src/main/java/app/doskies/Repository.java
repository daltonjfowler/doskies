package app.doskies;

import android.content.Context;
import android.location.Geocoder;
import android.location.Location;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches the Open-Meteo forecast (see PLAN.md's data contract) and keeps Store's last good
 * snapshot on any failure. The only network call this app makes.
 */
final class Repository {
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    static final Object LOCK = new Object();

    /** Builds the exact forecast URL from PLAN.md, fetches it, and validates the body parses. */
    static String fetch(double lat, double lon, char unit) throws Exception {
        boolean celsius = unit == 'C';
        String url = String.format(Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
                + "&current=temperature_2m,weather_code,precipitation"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&temperature_unit=%s&precipitation_unit=%s&wind_speed_unit=mph&timezone=auto&forecast_days=7",
            lat, lon, celsius ? "celsius" : "fahrenheit", celsius ? "mm" : "inch");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("GET");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Accept", "application/json");
            int status = c.getResponseCode();
            if (status == 429) throw new IllegalArgumentException("Open-Meteo rate limit reached. Try again later.");
            if (status != 200) throw new IllegalArgumentException("Open-Meteo returned HTTP " + status + ". Last snapshot kept.");
            try (InputStream in = c.getInputStream()) {
                // Avoid readNBytes: unavailable on some supported Android releases.
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (out.size() + n > 8 * 1024 * 1024) throw new IllegalArgumentException("Forecast response is too large.");
                    out.write(buf, 0, n);
                }
                String raw = out.toString(StandardCharsets.UTF_8.name());
                Weather.parse(raw, unit); // validate before it is ever stored as a "good" snapshot
                return raw;
            }
        } finally {
            c.disconnect();
        }
    }

    /**
     * Reads lat/lon/unit from Store and fetches. In auto mode, first best-effort updates Store's
     * coordinates (and label, if reverse geocoding works) from a coarse fix; a location miss of
     * any kind never stops the fetch from using whatever coordinates end up in Store. In fixed
     * mode the stored coordinates are used unchanged. On a successful fetch, stores the new
     * snapshot, checked time, and clears any error. On failure, keeps the last snapshot untouched
     * and records a clear error string; the forecast is never blanked.
     *
     * <p>Demo mode (an explicit, labeled user choice; see Store.demo()) short-circuits all of
     * this: no location lookup, no network call, nothing written to Store. ForecastWidget reads
     * Store.demo() itself and renders Weather.demo() with a DEMO marker; this method's only job
     * when demo is on is to do nothing so the repaint that follows has nothing stale to react to.
     * Demo must never be substituted here for a failed live fetch (AGENTS.md); it is only ever
     * turned on by MainActivity's explicit toggle.
     */
    static boolean refresh(Context context) {
        synchronized (LOCK) {
            Store s = new Store(context);
            if (s.demo()) return true; // no network call in demo mode; the widget renders Weather.demo() itself
            updateLocationIfAuto(context, s);
            try {
                char unit = s.units().length() > 0 ? s.units().charAt(0) : 'F';
                String raw = fetch(s.lat(), s.lon(), unit);
                s.setSnapshot(raw);
                return true;
            } catch (Exception e) {
                String error = e instanceof IllegalArgumentException
                    ? e.getMessage() : "Could not refresh. Check your connection.";
                s.setError(error);
                return false;
            }
        }
    }

    /**
     * Best-effort, auto-mode-only coordinate (and label) update from a coarse fix, run before the
     * fetch above. Every failure path here (no permission, no fix, a reverse-geocode miss, a
     * stray exception) is swallowed so it can never stop the forecast from fetching with whatever
     * coordinates Store already has.
     */
    private static void updateLocationIfAuto(Context context, Store s) {
        if (!"auto".equals(s.mode())) return;
        try {
            boolean hasPermission = Locator.hasLocationPermission(context);
            Location fix = hasPermission ? Locator.getCoarseFix(context) : null;
            Locator.Fix f = fix == null ? null : new Locator.Fix(fix.getLatitude(), fix.getLongitude());
            // The unit-tested decision function is the production path: no hand-rolled branching here.
            double[] coords = Locator.resolveCoordinates(s.mode(), hasPermission, f, s.lat(), s.lon());
            if (coords[0] != s.lat() || coords[1] != s.lon()) s.setLocation(coords[0], coords[1]);
            if (f != null) {
                String reverseLabel = Locator.reverseLabel(context, coords[0], coords[1]);
                String label = Locator.resolveLabel(Geocoder.isPresent(), reverseLabel, s.placeLabel());
                if (!label.equals(s.placeLabel())) s.setPlaceLabel(label);
            }
        } catch (Exception e) {
            // A location failure of any kind must never stop the fetch below.
        }
    }

    private Repository() {}
}
