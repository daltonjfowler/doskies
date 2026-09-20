package app.doskies;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Plain SharedPreferences store. DOSkies has no secrets (no token, no login, no Keystore), so
 * unlike the Cloudflare widget's Store there is nothing here to encrypt.
 */
final class Store {
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
    /** Records a successful fetch: new snapshot, checked time, and the error (if any) is cleared. */
    void setSnapshot(String raw) {
        prefs.edit().putString("snapshot", raw).putLong("checked", System.currentTimeMillis()).remove("error").apply();
    }

    long checked() { return prefs.getLong("checked", 0); }

    String error() { return prefs.getString("error", ""); }
    /** Records a failed fetch. Deliberately does not touch "snapshot": the last good one stays. */
    void setError(String message) { prefs.edit().putString("error", message).apply(); }
    /** Clears a stale error without discarding the snapshot it was attached to. */
    void clear() { prefs.edit().remove("error").apply(); }
}
