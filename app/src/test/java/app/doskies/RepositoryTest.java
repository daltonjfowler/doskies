package app.doskies;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Repository.refresh's demo short-circuit, offline: demo mode must return without making a
 * network call and without touching Store's snapshot/checked/error. The network-fetch path
 * (Repository.fetch) is not exercised here or anywhere in this suite; it needs a live connection
 * and stays outside the offline gate, matching the rest of this project's tests.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RepositoryTest {
    private Context c;

    @Before public void before() {
        c = RuntimeEnvironment.getApplication();
        new Store(c).prefs.edit().clear().commit();
    }

    @Test public void demoModeSkipsFetchAndLeavesStoreUntouched() {
        Store s = new Store(c);
        s.setDemo(true);
        s.setSnapshot("{\"marker\":\"unchanged\"}", 'F', "Medford", 39.9007, -74.8235);
        long checkedBefore = s.checked();

        assertTrue("demo mode should report success without fetching", Repository.refresh(c));

        assertEquals("{\"marker\":\"unchanged\"}", s.snapshot());
        assertEquals(checkedBefore, s.checked());
        assertEquals("", s.error());
    }

    @Test public void demoModeNeverWritesAnError() {
        Store s = new Store(c);
        s.setDemo(true);
        Repository.refresh(c);
        assertEquals("", s.error());
    }

    // ---- fetch URL construction: pure string assertions, no network ----

    @Test public void fetchUrlUsesMphAndInchForFahrenheit() {
        String url = Repository.buildUrl(42.42, -71.11, 'F');
        assertTrue(url.contains("wind_speed_unit=mph"));
        assertTrue(url.contains("precipitation_unit=inch"));
        assertTrue(url.contains("temperature_unit=fahrenheit"));
        assertFalse(url.contains("wind_speed_unit=kmh"));
    }

    @Test public void fetchUrlUsesKmhAndMmForCelsius() {
        String url = Repository.buildUrl(42.42, -71.11, 'C');
        assertTrue(url.contains("wind_speed_unit=kmh"));
        assertTrue(url.contains("precipitation_unit=mm"));
        assertTrue(url.contains("temperature_unit=celsius"));
        assertFalse(url.contains("wind_speed_unit=mph"));
    }

    @Test public void fetchUrlRequestsTheNewOptionalFields() {
        String url = Repository.buildUrl(42.42, -71.11, 'F');
        assertTrue(url.contains("relative_humidity_2m"));
        assertTrue(url.contains("wind_speed_10m"));
        assertTrue(url.contains("wind_direction_10m"));
        assertTrue(url.contains("uv_index_max"));
    }

    // ---- snapshotLabel: the pure decision behind auto mode's "Current location" label
    // ---- (docs/ADVERSARIAL-REVIEW.md's second P1: no invented or stale city name) ----

    @Test public void snapshotLabelInAutoModeIsAlwaysCurrentLocation() {
        assertEquals("Current location", Repository.snapshotLabel("auto", "Medford"));
        assertEquals("Current location", Repository.snapshotLabel("auto", ""));
    }

    @Test public void snapshotLabelInFixedModeIsTheRequestedPlaceLabel() {
        assertEquals("Boston", Repository.snapshotLabel("fixed", "Boston"));
    }
}
