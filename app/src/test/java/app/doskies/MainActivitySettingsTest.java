package app.doskies;

import android.view.View;
import android.widget.Button;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Drives MainActivity's settings controls directly (its interactive fields are package-visible
 * for exactly this) and asserts each one writes the right Store key, per PLAN.md's T4 spec. No
 * network is used anywhere here: Geocoding.search is never called, only Geocoding.parse (a pure
 * function) against a fixture, and selectResult/showResults are exercised directly.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainActivitySettingsTest {

    @Before public void before() {
        new Store(RuntimeEnvironment.getApplication()).prefs.edit().clear().commit();
    }

    private MainActivity activity() {
        return Robolectric.buildActivity(MainActivity.class).create().get();
    }

    // ---- units ----

    @Test public void unitsToggleUpdatesStore() {
        MainActivity a = activity();
        Store s = new Store(a);
        assertEquals("F", s.units());

        a.unitsSwitch.setChecked(true); // flips to Celsius
        assertEquals("C", s.units());
        assertEquals("Celsius", a.unitsSwitch.getText().toString());

        a.unitsSwitch.setChecked(false); // flips back to Fahrenheit
        assertEquals("F", s.units());
        assertEquals("Fahrenheit", a.unitsSwitch.getText().toString());
    }

    // ---- location mode ----

    @Test public void modeSwitchUpdatesStoreAndRevealsCitySearch() {
        MainActivity a = activity();
        Store s = new Store(a);
        assertEquals("auto", s.mode());
        assertEquals(View.GONE, a.citySearchSection.getVisibility());

        a.modeGroup.check(a.radioFixed.getId());
        assertEquals("fixed", s.mode());
        assertEquals(View.VISIBLE, a.citySearchSection.getVisibility());

        a.modeGroup.check(a.radioAuto.getId());
        assertEquals("auto", s.mode());
        assertEquals(View.GONE, a.citySearchSection.getVisibility());
    }

    // ---- demo ----

    @Test public void demoToggleUpdatesStore() {
        MainActivity a = activity();
        Store s = new Store(a);
        assertFalse(s.demo());

        a.demoSwitch.setChecked(true);
        assertTrue(s.demo());
        assertTrue("status should carry a DEMO marker", a.statusLabel.getText().toString().startsWith("DEMO"));
        assertEquals("Demo data, not live.", a.freshnessLabel.getText().toString());

        a.demoSwitch.setChecked(false);
        assertFalse(s.demo());
        assertFalse(a.statusLabel.getText().toString().startsWith("DEMO"));
    }

    // ---- city search: selecting a geocoding Result ----

    @Test public void selectingAGeocodingResultSetsStoreLocationAndLabel() {
        MainActivity a = activity();
        Store s = new Store(a);
        Geocoding.Result r = new Geocoding.Result(42.3168, -70.9663, "Medford", "Massachusetts", "US");

        a.selectResult(r);

        assertEquals(42.3168, s.lat(), 0.0001);
        assertEquals(-70.9663, s.lon(), 0.0001);
        assertEquals("Medford, Massachusetts", s.placeLabel());
    }

    @Test public void showResultsListsEachCandidateAndPickingOneSelectsIt() throws Exception {
        MainActivity a = activity();
        Store s = new Store(a);
        // Mirrors PLAN.md's own example: a "Medford" search is ambiguous between two real places.
        String fixture = "{\"results\":["
            + "{\"name\":\"Medford\",\"latitude\":39.9007,\"longitude\":-74.8235,\"admin1\":\"New Jersey\",\"country_code\":\"US\"},"
            + "{\"name\":\"Medford\",\"latitude\":42.3168,\"longitude\":-70.9663,\"admin1\":\"Massachusetts\",\"country_code\":\"US\"}"
            + "]}";
        List<Geocoding.Result> results = Geocoding.parse(fixture); // pure parse, no network

        a.showResults(results);
        assertEquals(2, a.searchResults.getChildCount());
        Button second = (Button) a.searchResults.getChildAt(1);
        assertTrue(second.getText().toString().contains("Massachusetts"));

        second.performClick();

        assertEquals(42.3168, s.lat(), 0.0001);
        assertEquals("Medford, Massachusetts", s.placeLabel());
        assertEquals("picking a result clears the list", 0, a.searchResults.getChildCount());
    }

    @Test public void noResultsShowsClearMessageAndNeverCrashes() {
        MainActivity a = activity();
        a.showResults(new ArrayList<>());
        assertEquals("No matching cities found.", a.searchStatus.getText().toString());
        assertEquals(0, a.searchResults.getChildCount());
    }

    @Test public void emptySearchQueryPromptsWithoutSearching() {
        MainActivity a = activity();
        a.performSearch("");
        assertEquals("Type a city name to search.", a.searchStatus.getText().toString());
    }

    // ---- manual refresh ----

    @Test public void refreshButtonShowsBriefAcknowledgement() {
        MainActivity a = activity();
        a.refreshButton.performClick();
        assertEquals("Refreshing...", a.refreshStatus.getText().toString());
    }

    // ---- status reflects reality ----

    @Test public void statusShowsPlaceLabelAndLastError() {
        Store s = new Store(RuntimeEnvironment.getApplication());
        s.setPlaceLabel("Boston");
        s.setError("Could not refresh. Check your connection.");

        MainActivity a = activity();

        assertEquals("Boston", a.statusLabel.getText().toString());
        assertEquals("Could not refresh. Check your connection.", a.freshnessLabel.getText().toString());
    }

    @Test public void statusShowsNotCheckedYetBeforeAnyFetch() {
        MainActivity a = activity();
        assertEquals("Not checked yet", a.freshnessLabel.getText().toString());
    }
}
