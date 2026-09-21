package app.doskies;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Round-trips every Store key through real SharedPreferences (Robolectric), offline. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 35})
public class StoreTest {
    private Context c;

    @Before public void before() {
        c = RuntimeEnvironment.getApplication();
        new Store(c).prefs.edit().clear().commit();
    }

    @Test public void unitsDefaultsToFahrenheit() {
        assertEquals("F", new Store(c).units());
    }

    @Test public void unitsRoundTrip() {
        new Store(c).setUnits("C");
        assertEquals("C", new Store(c).units());
    }

    @Test public void modeDefaultsToAuto() {
        assertEquals("auto", new Store(c).mode());
    }

    @Test public void modeRoundTrip() {
        new Store(c).setMode("fixed");
        assertEquals("fixed", new Store(c).mode());
    }

    @Test public void placeLabelDefaultsToMedford() {
        assertEquals("Medford", new Store(c).placeLabel());
    }

    @Test public void placeLabelRoundTrip() {
        new Store(c).setPlaceLabel("Boston");
        assertEquals("Boston", new Store(c).placeLabel());
    }

    @Test public void locationRoundTrip() {
        new Store(c).setLocation(42.3601, -71.0589);
        Store reread = new Store(c);
        assertEquals(42.3601, reread.lat(), 0.001);
        assertEquals(-71.0589, reread.lon(), 0.001);
    }

    @Test public void snapshotRoundTripSetsCheckedAndClearsError() {
        Store s = new Store(c);
        s.setError("Offline");
        assertEquals("Offline", s.error());

        s.setSnapshot("{\"ok\":true}", 'F', "Medford", 39.9007, -74.8235);
        assertEquals("{\"ok\":true}", s.snapshot());
        assertTrue(s.checked() > 0);
        assertEquals("", s.error());
    }

    @Test public void failedRefreshKeepsLastSnapshot() {
        Store s = new Store(c);
        s.setSnapshot("{\"ok\":true}", 'F', "Medford", 39.9007, -74.8235);
        long checkedAt = s.checked();

        s.setError("Could not refresh. Check your connection.");

        assertEquals("{\"ok\":true}", s.snapshot());               // never blanked
        assertEquals("Could not refresh. Check your connection.", s.error());
        assertEquals(checkedAt, s.checked());                       // "checked" only moves on success
    }

    @Test public void clearRemovesErrorButKeepsSnapshot() {
        Store s = new Store(c);
        s.setSnapshot("{\"ok\":true}", 'F', "Medford", 39.9007, -74.8235);
        s.setError("Offline");

        s.clear();

        assertEquals("", s.error());
        assertEquals("{\"ok\":true}", s.snapshot());
    }

    // ---- snapshot provenance (docs/ADVERSARIAL-REVIEW.md P1): the fetched unit/place, not the
    // ---- live requested settings, is what a cached forecast must render with. ----

    @Test public void snapshotUnitDefaultsToRequestedUnitsWhenNoSnapshotYet() {
        Store s = new Store(c);
        assertEquals('F', s.snapshotUnit()); // units() defaults to "F"
        s.setUnits("C");
        assertEquals('C', s.snapshotUnit());
    }

    @Test public void snapshotPlaceDefaultsToRequestedPlaceLabelWhenNoSnapshotYet() {
        Store s = new Store(c);
        assertEquals("Medford", s.snapshotPlace());
        s.setPlaceLabel("Boston");
        assertEquals("Boston", s.snapshotPlace());
    }

    @Test public void snapshotUnitAndPlaceAreTheFetchedOnesNotTheLiveRequestedSettings() {
        Store s = new Store(c);
        s.setSnapshot("{\"ok\":true}", 'F', "Alpha", 39.9007, -74.8235);

        // The requested settings change after the fact...
        s.setUnits("C");
        s.setPlaceLabel("Beta");

        // ...but the snapshot's own recorded unit/place must not move.
        assertEquals('F', s.snapshotUnit());
        assertEquals("Alpha", s.snapshotPlace());
        // The requested settings themselves are unaffected and read back as set.
        assertEquals("C", s.units());
        assertEquals("Beta", s.placeLabel());
    }

    @Test public void newSnapshotReplacesThePreviousProvenance() {
        Store s = new Store(c);
        s.setSnapshot("{\"first\":true}", 'F', "Alpha", 39.9007, -74.8235);
        s.setSnapshot("{\"second\":true}", 'C', "Beta", 42.36, -71.06);

        assertEquals("{\"second\":true}", s.snapshot());
        assertEquals('C', s.snapshotUnit());
        assertEquals("Beta", s.snapshotPlace());
    }
}
