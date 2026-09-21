package app.doskies;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * resolveCoordinates has no Android dependency, so this is plain, fast, offline JUnit: no
 * Robolectric runner needed. Locator.hasLocationPermission (the one bit that does need a real
 * Context) gets its own smoke test in LocatorPermissionTest. Locator no longer resolves a place
 * label at all (the platform Geocoder was removed; see docs/ADVERSARIAL-REVIEW.md and
 * Repository.snapshotLabel, covered in RepositoryTest).
 */
public class LocatorTest {
    private static final Locator.Fix FIX = new Locator.Fix(37.7749, -122.4194); // San Francisco
    private static final double STORED_LAT = 39.9007; // Medford, NJ default
    private static final double STORED_LON = -74.8235;

    // ---- resolveCoordinates: every branch in PLAN.md's T3 spec ----

    @Test public void fixedModeAlwaysUsesStoredCoordinatesEvenWithAPermittedFix() {
        double[] coords = Locator.resolveCoordinates("fixed", true, FIX, STORED_LAT, STORED_LON);
        assertArrayEquals(new double[] { STORED_LAT, STORED_LON }, coords, 0.0);
    }

    @Test public void autoModeWithPermissionAndFixUsesTheFix() {
        double[] coords = Locator.resolveCoordinates("auto", true, FIX, STORED_LAT, STORED_LON);
        assertArrayEquals(new double[] { FIX.lat, FIX.lon }, coords, 0.0);
    }

    @Test public void autoModeWithoutPermissionFallsBackToStoredEvenWithAFix() {
        double[] coords = Locator.resolveCoordinates("auto", false, FIX, STORED_LAT, STORED_LON);
        assertArrayEquals(new double[] { STORED_LAT, STORED_LON }, coords, 0.0);
    }

    @Test public void autoModeWithPermissionButNoFixFallsBackToStored() {
        double[] coords = Locator.resolveCoordinates("auto", true, null, STORED_LAT, STORED_LON);
        assertArrayEquals(new double[] { STORED_LAT, STORED_LON }, coords, 0.0);
    }

    @Test public void fixedModeWithoutPermissionAndNoFixStillUsesStored() {
        double[] coords = Locator.resolveCoordinates("fixed", false, null, STORED_LAT, STORED_LON);
        assertArrayEquals(new double[] { STORED_LAT, STORED_LON }, coords, 0.0);
    }
}
