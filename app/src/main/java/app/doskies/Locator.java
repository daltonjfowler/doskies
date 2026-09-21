package app.doskies;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coarse-only location, per PLAN.md/AGENTS.md: ACCESS_COARSE_LOCATION only, never blocks the
 * forecast on a denied permission or a missed fix. No Google Play services or
 * FusedLocationProviderClient anywhere here, and no platform Geocoder either: reverse geocoding
 * was removed because it can make an unspecified network call, which would break the strict
 * Open-Meteo-only privacy promise (see docs/ADVERSARIAL-REVIEW.md). Auto-located forecasts are
 * labeled "Current location" instead (decided at snapshot time; see Repository.snapshotLabel).
 *
 * <p>{@link #resolveCoordinates} is a pure decision function with no Android dependency, so it is
 * unit-tested directly without Robolectric. Everything else here talks to the platform and is
 * exercised through Repository's wiring and, for the permission check, a small Robolectric smoke
 * test.
 */
final class Locator {
    /** An older cached fix than this is not trusted over the stored place. */
    private static final long MAX_AGE_MS = 30 * 60 * 1000L;
    /** Bound on the single fresh-fix attempt; getCoarseFix must never hang the caller past this. */
    private static final long UPDATE_TIMEOUT_MS = 8_000L;

    private static final String[] READ_PROVIDERS = {
        LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER,
        LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER
    };
    /** Providers worth actively requesting a fresh fix from; PASSIVE only ever listens, never requests. */
    private static final String[] REQUEST_PROVIDERS = {
        LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER
    };

    static boolean hasLocationPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Best-effort coarse fix: the freshest acceptable cached location across the enabled coarse
     * providers, or (failing that) one bounded fresh-fix attempt, falling back to a stale cached
     * fix rather than nothing. Returns null only when there is no location at all (no permission,
     * no providers, no cached fix, and the fresh attempt missed); never throws.
     *
     * <p>Blocks the calling thread for up to {@link #UPDATE_TIMEOUT_MS} while waiting on the
     * single-update attempt, so callers must invoke this off the main thread. Repository.refresh
     * already runs on its own executor, so this is safe there.
     */
    static Location getCoarseFix(Context c) {
        if (!hasLocationPermission(c)) return null;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;

        List<String> enabledRead = enabledProviders(lm, READ_PROVIDERS);
        Location best = null;
        for (String provider : enabledRead) {
            try {
                Location fix = lm.getLastKnownLocation(provider);
                if (fix != null && (best == null || fix.getTime() > best.getTime())) best = fix;
            } catch (SecurityException | IllegalArgumentException e) {
                // Provider missing on this build or a permission race; keep looking.
            }
        }
        if (best != null && System.currentTimeMillis() - best.getTime() <= MAX_AGE_MS) return best;

        Location fresh = requestSingleUpdate(lm, enabledProviders(lm, REQUEST_PROVIDERS));
        return fresh != null ? fresh : best; // a stale cached fix still beats nothing
    }

    private static List<String> enabledProviders(LocationManager lm, String[] candidates) {
        List<String> out = new ArrayList<>();
        for (String p : candidates) {
            try {
                if (p != null && lm.isProviderEnabled(p)) out.add(p);
            } catch (Exception e) {
                // Unknown provider name on this build (e.g. no "fused" provider present); skip it.
            }
        }
        return out;
    }

    private static Location requestSingleUpdate(LocationManager lm, List<String> providers) {
        if (providers.isEmpty()) return null;
        String provider = providers.get(0);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Location> ref = new AtomicReference<>();
        LocationListener listener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                ref.set(location);
                latch.countDown();
            }
            @Override public void onProviderDisabled(String p) { latch.countDown(); }
        };
        try {
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper());
            latch.await(UPDATE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (SecurityException e) {
            // Permission revoked between the check above and this call; treat as a miss.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try { lm.removeUpdates(listener); } catch (Exception ignored) {}
        }
        return ref.get();
    }

    /** A minimal, Android-free fix: just enough for the coordinate decision below to be pure. */
    static final class Fix {
        final double lat;
        final double lon;
        Fix(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }

    /**
     * Pure decision: which coordinates a refresh should use. No Android dependency, so this is
     * unit-tested directly.
     *
     * <p>"fixed" mode always uses the stored coordinates, regardless of fix or permission. "auto"
     * mode uses the fix's coordinates when a usable fix exists AND permission is granted;
     * otherwise (no permission, or no fix) it falls back to the stored coordinates.
     */
    static double[] resolveCoordinates(String mode, boolean hasPermission, Fix fix, double storedLat, double storedLon) {
        if ("auto".equals(mode) && hasPermission && fix != null) {
            return new double[] { fix.lat, fix.lon };
        }
        return new double[] { storedLat, storedLon };
    }

    private Locator() {}
}
