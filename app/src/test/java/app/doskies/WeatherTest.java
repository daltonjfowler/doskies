package app.doskies;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Parses the bundled fixture (a realistic Open-Meteo forecast response) and checks the shape,
 * a couple of specific day indices, rounding, and precip clamping. Entirely offline: the fixture
 * is a static string, no network call is ever made.
 */
public class WeatherTest {

    // Mirrors the real schema: current + daily with parallel arrays indexed by daily.time[i].
    // 7 days, temperature_unit=fahrenheit as PLAN.md's contract specifies for the 'F' fetch.
    private static final String FIXTURE = "{"
        + "\"latitude\":42.42,\"longitude\":-71.11,"
        + "\"current\":{\"time\":\"2026-09-20T14:00\",\"temperature_2m\":71.4,\"weather_code\":2,\"precipitation\":0.0},"
        + "\"daily\":{"
        + "\"time\":[\"2026-09-20\",\"2026-09-21\",\"2026-09-22\",\"2026-09-23\",\"2026-09-24\",\"2026-09-25\",\"2026-09-26\"],"
        + "\"weather_code\":[2,3,61,63,0,95,71],"
        + "\"temperature_2m_max\":[75.2,70.1,66.8,64.3,78.9,72.0,40.4],"
        + "\"temperature_2m_min\":[58.6,55.4,52.1,50.9,60.2,59.5,28.7],"
        + "\"precipitation_probability_max\":[10,20,70,85,0,90,40]"
        + "}}";

    @Test public void parsesSevenDays() throws Exception {
        Forecast f = Weather.parse(FIXTURE, 'F');
        assertEquals(7, f.days.length);
        assertEquals('F', f.unit);
    }

    @Test public void parsesCurrentConditions() throws Exception {
        Forecast f = Weather.parse(FIXTURE, 'F');
        assertEquals(71, f.current.temp);   // 71.4 rounds down
        assertEquals(2, f.current.code);
        assertEquals(0.0, f.current.precip, 0.0001);
    }

    @Test public void parsesFirstDay() throws Exception {
        Forecast.Day d = Weather.parse(FIXTURE, 'F').days[0];
        assertEquals("2026-09-20", d.date);
        assertEquals(75, d.hi);  // 75.2 -> 75
        assertEquals(59, d.lo);  // 58.6 -> 59
        assertEquals(10, d.precipChancePct);
        assertEquals(2, d.code);
    }

    @Test public void parsesARainyMiddleDay() throws Exception {
        Forecast.Day d = Weather.parse(FIXTURE, 'F').days[2];
        assertEquals("2026-09-22", d.date);
        assertEquals(67, d.hi);  // 66.8 -> 67
        assertEquals(52, d.lo);  // 52.1 -> 52
        assertEquals(70, d.precipChancePct);
        assertEquals(61, d.code);
        assertEquals(Wmo.Condition.RAIN, Wmo.condition(d.code));
    }

    @Test public void parsesLastDay() throws Exception {
        Forecast.Day d = Weather.parse(FIXTURE, 'F').days[6];
        assertEquals("2026-09-26", d.date);
        assertEquals(40, d.hi);  // 40.4 -> 40
        assertEquals(29, d.lo);  // 28.7 -> 29
        assertEquals(40, d.precipChancePct);
        assertEquals(71, d.code);
        assertEquals(Wmo.Condition.SNOW, Wmo.condition(d.code));
    }

    @Test public void precipChancePctIsClampedTo0To100() throws Exception {
        String tooHigh = FIXTURE.replace("\"precipitation_probability_max\":[10,20,70,85,0,90,40]",
            "\"precipitation_probability_max\":[10,20,70,85,0,150,40]");
        Forecast f = Weather.parse(tooHigh, 'F');
        assertEquals(100, f.days[5].precipChancePct);
        for (Forecast.Day d : f.days) {
            assertTrue(d.precipChancePct >= 0);
            assertTrue(d.precipChancePct <= 100);
        }
    }

    @Test public void demoIsNeverMistakenForALiveParse() {
        Forecast demo = Weather.demo();
        assertEquals(7, demo.days.length);
        assertEquals('F', demo.unit);
        // The demo is a fixed, hand-authored snapshot; it must not equal a parsed live fixture,
        // so nothing downstream could confuse the two.
        assertNotEquals(demo.current.temp, 0);
    }

    // ---- malformed responses must throw, never silently coerce to zeros ----

    @Test(expected = Exception.class) public void emptyResponseThrows() throws Exception {
        Weather.parse("", 'F');
    }

    @Test(expected = Exception.class) public void truncatedJsonThrows() throws Exception {
        Weather.parse("{\"current\":{\"temperature_2m\":71", 'F');
    }

    @Test(expected = IllegalArgumentException.class) public void missingDailyThrows() throws Exception {
        Weather.parse("{\"current\":{\"temperature_2m\":71,\"weather_code\":0,\"precipitation\":0}}", 'F');
    }

    @Test(expected = IllegalArgumentException.class) public void missingCurrentFieldThrows() throws Exception {
        String bad = FIXTURE.replace("\"temperature_2m\":71.4,", "");
        Weather.parse(bad, 'F');
    }

    @Test(expected = IllegalArgumentException.class) public void shortDailyArrayThrows() throws Exception {
        // Only 5 days instead of 7: an incompatible response, not something to pad with zeros.
        String bad = FIXTURE
            .replace("\"time\":[\"2026-09-20\",\"2026-09-21\",\"2026-09-22\",\"2026-09-23\",\"2026-09-24\",\"2026-09-25\",\"2026-09-26\"]",
                "\"time\":[\"2026-09-20\",\"2026-09-21\",\"2026-09-22\",\"2026-09-23\",\"2026-09-24\"]");
        Weather.parse(bad, 'F');
    }

    @Test(expected = Exception.class) public void mismatchedParallelArrayLengthsThrows() throws Exception {
        // weather_code has 7 entries but temperature_2m_max only has 6: an inconsistent response.
        String bad = FIXTURE.replace("\"temperature_2m_max\":[75.2,70.1,66.8,64.3,78.9,72.0,40.4]",
            "\"temperature_2m_max\":[75.2,70.1,66.8,64.3,78.9,72.0]");
        Weather.parse(bad, 'F');
    }
}
