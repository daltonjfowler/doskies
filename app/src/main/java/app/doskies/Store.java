package app.doskies;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Plain SharedPreferences store. DOSkies has no secrets (no token, no login, no Keystore), so
 * unlike the Cloudflare widget's Store there is nothing here to encrypt.
 */
final class Store {
    static final String DEFAULT_UPDATE_URL = "https://doskies-updates.daltonjfowler.workers.dev";

    final SharedPreferences prefs;

    Store(Context context) { prefs = context.getSharedPreferences("doskies", Context.MODE_PRIVATE); }

    String units() { return prefs.getString("units", "F"); }
    void setUnits(String unit) { prefs.edit().putString("units", unit).apply(); }

    /** "auto" (coarse location, saved place on denial/failure) or "fixed" (a chosen city). */
    String mode() { return prefs.getString("mode", "auto"); }
    void setMode(String mode) { prefs.edit().putString("mode", mode).apply(); }

    // Stored as float: plenty of precision for weather (~1m at the equator), and
    // SharedPreferences has no native double. Default is Medford, NJ 08055, matching placeLabel().
    double lat() { return prefs.getFloat("lat", 39.9007f); }
    double lon() { return prefs.getFloat("lon", -74.8235f); }
    void setLocation(double lat, double lon) {
        prefs.edit().putFloat("lat", (float) lat).putFloat("lon", (float) lon).apply();
    }

    String placeLabel() { return prefs.getString("place_label", "Medford"); }
    void setPlaceLabel(String label) { prefs.edit().putString("place_label", label).apply(); }

    String snapshot() { return prefs.getString("snapshot", ""); }

    /**
     * Records a successful fetch as one coherent record: the raw response plus the unit and place
     * it was FETCHED in (not whatever units()/placeLabel() request next). Fixes the adversarial
     * review's P1: a cached forecast must render with the settings it was fetched under, so a unit
     * change or a city change followed by a failed refresh can never relabel or reconvert stale
     * data. Also records checked time and clears the error (if any), same as before.
     */
    void setSnapshot(String raw, char unit, String place, double lat, double lon) {
        prefs.edit()
            .putString("snapshot", raw)
            .putString("snapshot_unit", String.valueOf(unit))
            .putString("snapshot_place", place)
            .putFloat("snapshot_lat", (float) lat)
            .putFloat("snapshot_lon", (float) lon)
            .putLong("checked", System.currentTimeMillis())
            .remove("error")
            .apply();
    }

    /** The unit the current snapshot was fetched in (NOT the requested units()). */
    char snapshotUnit() {
        String stored = prefs.getString("snapshot_unit", null);
        if (stored != null && !stored.isEmpty()) return stored.charAt(0);
        String u = units();
        return u.isEmpty() ? 'F' : u.charAt(0);
    }

    /** The place label the current snapshot was fetched under (NOT the requested placeLabel()). */
    String snapshotPlace() {
        String stored = prefs.getString("snapshot_place", null);
        return stored != null ? stored : placeLabel();
    }

    long checked() { return prefs.getLong("checked", 0); }

    String error() { return prefs.getString("error", ""); }
    /** Records a failed fetch. Deliberately does not touch "snapshot": the last good one stays. */
    void setError(String message) { prefs.edit().putString("error", message).apply(); }
    /** Clears a stale error without discarding the snapshot it was attached to. */
    void clear() { prefs.edit().remove("error").apply(); }

    /**
     * Explicit, user-chosen demo mode: the widget renders Weather.demo() with a visible DEMO
     * marker instead of Store's real snapshot, and Repository.refresh makes no network call while
     * this is on. Defaults off. Never set automatically on a failed fetch; that would turn a real
     * outage into a quiet, confident lie (see AGENTS.md).
     */
    boolean demo() { return prefs.getBoolean("demo", false); }
    void setDemo(boolean on) { prefs.edit().putBoolean("demo", on).apply(); }

    // ---- Phase 2: self-hosted updates -----------------------------------------

    /** The update host base URL; defaults to Dalton's own, no account or device id in it. */
    String updateUrl() {
        String u = prefs.getString("update_url", "");
        return u.isEmpty() ? DEFAULT_UPDATE_URL : u;
    }
    void setUpdateUrl(String url) { prefs.edit().putString("update_url", url == null ? "" : url.trim()).apply(); }

    /** The version_code the daily update check last notified about, so it never re-posts for the
     *  same version. */
    int lastUpdateNotifiedCode() { return prefs.getInt("last_update_notified_code", 0); }
    void setLastUpdateNotifiedCode(int code) { prefs.edit().putInt("last_update_notified_code", code).apply(); }
}
