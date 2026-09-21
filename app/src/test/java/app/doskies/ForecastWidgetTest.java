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
 * Exercises {@link ForecastWidget#screen(Context)} -- the pure state-building step that feeds
 * {@link CgaRenderer} -- offline. No network call is ever made: every snapshot here is either
 * empty, corrupt, or a labeled fixture serialized to the Open-Meteo response shape (mirroring
 * Weather.demo()'s core values) so it round-trips through Weather.parse exactly like a live fetch
 * would. CgaRenderer's own Canvas drawing is covered separately by CgaRendererTest; this file only
 * checks the Screen state that drawing is built from.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ForecastWidgetTest {

    // Mirrors Weather.demo()'s current + 7 days, serialized to the real contract shape. Deliberately
    // omits the optional current fields (humidity/uv/wind) that a real Open-Meteo response might
    // also omit, so parsing it exercises the same UNKNOWN(-1) fallback Weather.parse guarantees.
    private static final String DEMO_SNAPSHOT = "{"
        + "\"current\":{\"temperature_2m\":71,\"weather_code\":1,\"precipitation\":0.0},"
        + "\"daily\":{"
        + "\"time\":[\"2026-09-20\",\"2026-09-21\",\"2026-09-22\",\"2026-09-23\",\"2026-09-24\",\"2026-09-25\",\"2026-09-26\"],"
        + "\"weather_code\":[1,2,61,63,0,95,71],"
        + "\"temperature_2m_max\":[72,70,66,64,78,72,40],"
        + "\"temperature_2m_min\":[58,55,52,50,60,59,29],"
        + "\"precipitation_probability_max\":[10,20,70,85,0,90,40]"
        + "}}";

    private Context c;

    @Before public void before() {
        c = RuntimeEnvironment.getApplication();
        new Store(c).prefs.edit().clear().commit();
    }

    /** Full field-by-field match against Weather.demo() itself (used when scr.forecast IS that call's result). */
    private static void assertForecastIsWeatherDemo(Forecast f) {
        Forecast demo = Weather.demo();
        assertNotNull(f);
        assertEquals(demo.unit, f.unit);
        assertEquals(demo.current.temp, f.current.temp);
        assertEquals(demo.current.code, f.current.code);
        assertEquals(demo.current.humidity, f.current.humidity);
        assertEquals(demo.current.uvMax, f.current.uvMax);
        assertEquals(demo.current.wind, f.current.wind);
        assertDaysMatchDemo(f);
    }

    /** Core-field match for a forecast parsed from DEMO_SNAPSHOT, whose optional stats are absent. */
    private static void assertForecastIsParsedDemoFixture(Forecast f) {
        assertNotNull(f);
        assertEquals('F', f.unit);
        assertEquals(71, f.current.temp);
        assertEquals(1, f.current.code);
        assertEquals("optional field absent from the fixture stays UNKNOWN, never coerced",
            Forecast.UNKNOWN, f.current.humidity);
        assertEquals(Forecast.UNKNOWN, f.current.uvMax);
        assertEquals(Forecast.UNKNOWN, f.current.wind);
        assertDaysMatchDemo(f);
    }

    private static void assertDaysMatchDemo(Forecast f) {
        Forecast.Day[] days = Weather.demo().days;
        assertEquals(days.length, f.days.length);
        for (int i = 0; i < days.length; i++) {
            assertEquals(days[i].date, f.days[i].date);
            assertEquals(days[i].hi, f.days[i].hi);
            assertEquals(days[i].lo, f.days[i].lo);
            assertEquals(days[i].precipChancePct, f.days[i].precipChancePct);
            assertEquals(days[i].code, f.days[i].code);
        }
    }

    // ---- demo mode: always Weather.demo(), marked, never a substitute for a failure ----

    @Test public void demoOnRendersWeatherDemoAndSetsTheDemoFlag() {
        new Store(c).setDemo(true);
        CgaRenderer.Screen scr = ForecastWidget.screen(c);
        assertTrue(scr.demo);
        assertForecastIsWeatherDemo(scr.forecast);
        assertEquals("Demo data, not live", scr.status);
        assertFalse(scr.statusIsError);
    }

    @Test public void demoIgnoresAnyRealSnapshotOrError() {
        Store s = new Store(c);
        s.setDemo(true);
        s.setSnapshot(DEMO_SNAPSHOT, 'F', "Medford", 39.9007, -74.8235);
        s.setError("Could not refresh. Check your connection.");
        CgaRenderer.Screen scr = ForecastWidget.screen(c);
        assertTrue(scr.demo);
        assertForecastIsWeatherDemo(scr.forecast);
        assertEquals("Demo data, not live", scr.status);
        assertFalse("demo mode must not surface the stored error", scr.statusIsError);
    }

    // ---- no snapshot: waiting state ----

    @Test public void noSnapshotYieldsNullForecastAndAStatus() {
        // Store starts empty (before() clears it): no snapshot, no error, demo off.
        CgaRenderer.Screen scr = ForecastWidget.screen(c);
        assertNull(scr.forecast);
        assertFalse(scr.demo);
        assertEquals("Tap refresh to load", scr.status);
        assertFalse(scr.statusIsError);
    }

    @Test public void noSnapshotWithAnErrorShowsTheErrorNeverWeatherDemo() {
        // AGENTS.md's core rule: a failed fetch must never be papered over with demo data.
        Store s = new Store(c);
        s.setError("Could not refresh. Check your connection.");
        CgaRenderer.Screen scr = ForecastWidget.screen(c);
        assertNull(scr.forecast);
        assertFalse(scr.demo);
        assertEquals("Could not refresh. Check your connection.", scr.status);
        assertTrue(scr.statusIsError);
    }

    // ---- a present snapshot with an error set: the forecast is never blanked ----

    @Test public void errorWithSnapshotKeepsTheForecastAndFlagsTheStatusAsError() {
        Store s = new Store(c);
        s.setSnapshot(DEMO_SNAPSHOT, 'F', "Medford", 39.9007, -74.8235);
        s.setError("Could not refresh. Check your connection.");
        CgaRenderer.Screen scr = ForecastWidget.screen(c);
        assertNotNull("a failed refresh must not blank the last good forecast", scr.forecast);
        assertForecastIsParsedDemoFixture(scr.forecast);
        assertTrue(scr.statusIsError);
        assertEquals("Could not refresh. Check your connection.", scr.status);
        assertFalse(scr.demo);
    }

    // ---- a normal snapshot: parsed forecast, freshness status ----

    @Test public void normalSnapshotParsesForecastAndShowsUpdatedStatus() {
        Store s = new Store(c);
        s.setSnapshot(DEMO_SNAPSHOT, 'F', "Medford", 39.9007, -74.8235);
        assertTrue(s.error().isEmpty());
        CgaRenderer.Screen scr = ForecastWidget.screen(c);
        assertForecastIsParsedDemoFixture(scr.forecast);
        assertFalse(scr.statusIsError);
        assertFalse(scr.demo);
        assertTrue("status should be the 'Updated ...' freshness text, was: " + scr.status,
            scr.status.startsWith("Updated"));
    }

    @Test public void placeLabelIsUppercasedFromStore() {
        new Store(c).setPlaceLabel("Boston");
        CgaRenderer.Screen scr = ForecastWidget.screen(c);
        assertEquals("BOSTON", scr.place);
    }

    // ---- snapshot provenance (docs/ADVERSARIAL-REVIEW.md's first P1): a cached forecast renders
    // ---- with the unit/place it was FETCHED under, never the live requested settings, especially
    // ---- across a failed refresh. ----

    /** Regression A: a unit change followed by a failed refresh must never reconvert or relabel
     * the cached forecast's unit -- 71 stays 'F', never presented as 71 'C'. */
    @Test public void unitChangeAfterAFailedRefreshNeverRelabelsTheCachedForecastsUnit() {
        Store s = new Store(c);
        s.setSnapshot(DEMO_SNAPSHOT, 'F', "Medford", 39.9007, -74.8235); // fetched as F, temp 71
        s.setUnits("C");                                                 // requested settings change
        s.setError("Could not refresh. Check your connection.");        // ...and the refresh fails

        CgaRenderer.Screen scr = ForecastWidget.screen(c);

        assertNotNull(scr.forecast);
        assertEquals("must render the snapshot's own fetched unit, not the live request", 'F', scr.forecast.unit);
        assertEquals(71, scr.forecast.current.temp); // never reconverted to a bogus "71 C"
        assertTrue(scr.statusIsError);
    }

    /** Regression B: selecting a different city followed by a failed refresh must never show the
     * previous city's forecast under the new city's name. */
    @Test public void cityChangeAfterAFailedRefreshNeverRelabelsTheCachedForecastsPlace() {
        Store s = new Store(c);
        s.setSnapshot(DEMO_SNAPSHOT, 'F', "Alpha", 39.9007, -74.8235); // fetched while place was Alpha
        s.setPlaceLabel("Beta");                                       // user picks a new city
        s.setError("Could not refresh. Check your connection.");      // ...and the refresh fails

        CgaRenderer.Screen scr = ForecastWidget.screen(c);

        assertEquals("must show the snapshot's own fetched place, not the newly requested one",
            "ALPHA", scr.place);
    }

    /** Regression C: an auto-mode fetch's snapshot is always labeled "Current location", even
     * when a stale fixed-mode label ("Medford") is still sitting in the requested settings. */
    @Test public void autoModeSnapshotIsLabeledCurrentLocationNeverAStaleFixedCityName() {
        Store s = new Store(c);
        s.setMode("auto");
        s.setPlaceLabel("Medford"); // stale, left over from a previous fixed-mode selection
        String label = Repository.snapshotLabel(s.mode(), s.placeLabel()); // Repository.refresh's own logic
        s.setSnapshot(DEMO_SNAPSHOT, 'F', label, 40.0, -75.0);

        CgaRenderer.Screen scr = ForecastWidget.screen(c);

        assertEquals("CURRENT LOCATION", scr.place);
    }

    // ---- a corrupt stored snapshot must never crash the widget ----

    @Test public void corruptSnapshotYieldsNullForecastInsteadOfCrashing() {
        Store s = new Store(c);
        s.setSnapshot("not json", 'F', "Medford", 39.9007, -74.8235);
        CgaRenderer.Screen scr = ForecastWidget.screen(c);
        assertNull(scr.forecast);
        assertFalse(scr.statusIsError);
    }

    // ---- checkedText wording, shared verbatim with MainActivity's status line ----

    @Test public void checkedTextNeverCheckedYet() {
        assertEquals("Not checked yet", ForecastWidget.checkedText(0));
    }

    @Test public void checkedTextJustNow() {
        assertEquals("Updated just now", ForecastWidget.checkedText(System.currentTimeMillis()));
    }

    @Test public void checkedTextMinutesHoursDays() {
        long now = System.currentTimeMillis();
        assertEquals("Updated 5m ago", ForecastWidget.checkedText(now - 5 * 60_000L));
        assertEquals("Updated 2h ago", ForecastWidget.checkedText(now - 2 * 3_600_000L));
        assertEquals("Updated 3d ago", ForecastWidget.checkedText(now - 3 * 86_400_000L));
    }
}
