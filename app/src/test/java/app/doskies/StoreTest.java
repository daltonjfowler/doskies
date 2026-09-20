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

        s.setSnapshot("{\"ok\":true}");
        assertEquals("{\"ok\":true}", s.snapshot());
        assertTrue(s.checked() > 0);
        assertEquals("", s.error());
    }

    @Test public void failedRefreshKeepsLastSnapshot() {
        Store s = new Store(c);
        s.setSnapshot("{\"ok\":true}");
        long checkedAt = s.checked();

        s.setError("Could not refresh. Check your connection.");

        assertEquals("{\"ok\":true}", s.snapshot());               // never blanked
        assertEquals("Could not refresh. Check your connection.", s.error());
        assertEquals(checkedAt, s.checked());                       // "checked" only moves on success
    }

    @Test public void clearRemovesErrorButKeepsSnapshot() {
        Store s = new Store(c);
        s.setSnapshot("{\"ok\":true}");
        s.setError("Offline");

        s.clear();

        assertEquals("", s.error());
        assertEquals("{\"ok\":true}", s.snapshot());
    }
}
