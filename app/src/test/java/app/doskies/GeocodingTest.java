package app.doskies;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Parses city-search fixtures with no network involved: Geocoding.search is never called here.
 * Mirrors WeatherTest's shape.
 */
public class GeocodingTest {

    // Mirrors the real schema from PLAN.md: results[]{latitude, longitude, name, admin1, country_code}.
    // A real "Medford" search returns more than one hit, which is exactly the ambiguity fixed-mode
    // search exists to resolve.
    private static final String FIXTURE = "{"
        + "\"results\":["
        + "{\"id\":1,\"name\":\"Medford\",\"latitude\":39.9007,\"longitude\":-74.8235,"
        + "\"admin1\":\"New Jersey\",\"country_code\":\"US\"},"
        + "{\"id\":2,\"name\":\"Medford\",\"latitude\":42.3168,\"longitude\":-70.9663,"
        + "\"admin1\":\"Massachusetts\",\"country_code\":\"US\"}"
        + "],\"generationtime_ms\":0.5}";

    @Test public void parsesEachResult() throws Exception {
        List<Geocoding.Result> results = Geocoding.parse(FIXTURE);
        assertEquals(2, results.size());

        Geocoding.Result nj = results.get(0);
        assertEquals("Medford", nj.name);
        assertEquals(39.9007, nj.lat, 0.0001);
        assertEquals(-74.8235, nj.lon, 0.0001);
        assertEquals("New Jersey", nj.admin1);
        assertEquals("US", nj.countryCode);

        Geocoding.Result ma = results.get(1);
        assertEquals(42.3168, ma.lat, 0.0001);
        assertEquals(-70.9663, ma.lon, 0.0001);
        assertEquals("Massachusetts", ma.admin1);
    }

    @Test public void missingResultsKeyIsEmptyNotAnError() throws Exception {
        // What Open-Meteo actually sends back for a query with zero matches.
        List<Geocoding.Result> results = Geocoding.parse("{\"generationtime_ms\":0.1}");
        assertTrue(results.isEmpty());
    }

    @Test public void emptyResultsArrayIsEmpty() throws Exception {
        List<Geocoding.Result> results = Geocoding.parse("{\"results\":[]}");
        assertTrue(results.isEmpty());
    }

    @Test public void admin1DefaultsToEmptyWhenAbsent() throws Exception {
        String noAdmin1 = "{\"results\":[{\"name\":\"Reykjavik\",\"latitude\":64.15,\"longitude\":-21.94}]}";
        Geocoding.Result r = Geocoding.parse(noAdmin1).get(0);
        assertEquals("Reykjavik", r.name);
        assertEquals("", r.admin1);
        assertEquals("", r.countryCode);
    }

    @Test public void anEntryMissingCoordinatesIsSkippedNotCrashed() throws Exception {
        String partial = "{\"results\":["
            + "{\"name\":\"Nowhere\"},"
            + "{\"name\":\"Somewhere\",\"latitude\":1.0,\"longitude\":2.0}"
            + "]}";
        List<Geocoding.Result> results = Geocoding.parse(partial);
        assertEquals(1, results.size());
        assertEquals("Somewhere", results.get(0).name);
    }

    // ---- malformed JSON throws, deliberately, mirroring Weather.parse's convention ----

    @Test(expected = Exception.class) public void emptyResponseThrows() throws Exception {
        Geocoding.parse("");
    }

    @Test(expected = Exception.class) public void truncatedJsonThrows() throws Exception {
        Geocoding.parse("{\"results\":[");
    }
}
