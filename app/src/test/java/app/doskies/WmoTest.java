package app.doskies;

import org.junit.Test;
import static org.junit.Assert.*;

public class WmoTest {
    @Test public void clearBucket() { assertEquals(Wmo.Condition.CLEAR, Wmo.condition(0)); }
    @Test public void partlyBucket() {
        assertEquals(Wmo.Condition.PARTLY, Wmo.condition(1));
        assertEquals(Wmo.Condition.PARTLY, Wmo.condition(2));
    }
    @Test public void cloudyBucket() { assertEquals(Wmo.Condition.CLOUDY, Wmo.condition(3)); }
    @Test public void fogBucket() {
        assertEquals(Wmo.Condition.FOG, Wmo.condition(45));
        assertEquals(Wmo.Condition.FOG, Wmo.condition(48));
    }
    @Test public void drizzleBucket() {
        for (int code : new int[]{51, 53, 55, 56, 57}) assertEquals(Wmo.Condition.DRIZZLE, Wmo.condition(code));
    }
    @Test public void rainBucket() {
        for (int code : new int[]{61, 63, 65, 66, 67}) assertEquals(Wmo.Condition.RAIN, Wmo.condition(code));
    }
    @Test public void showersBucket() {
        for (int code : new int[]{80, 81, 82}) assertEquals(Wmo.Condition.SHOWERS, Wmo.condition(code));
    }
    @Test public void snowBucket() {
        for (int code : new int[]{71, 73, 75, 77, 85, 86}) assertEquals(Wmo.Condition.SNOW, Wmo.condition(code));
    }
    @Test public void thunderBucket() {
        for (int code : new int[]{95, 96, 99}) assertEquals(Wmo.Condition.THUNDER, Wmo.condition(code));
    }
    @Test public void unknownCodeFallsBackToCloudyGlyphButKeepsCode() {
        int weirdCode = 4242;
        assertEquals(Wmo.Condition.UNKNOWN, Wmo.condition(weirdCode));
        assertEquals(Wmo.Condition.CLOUDY, Wmo.glyphBucket(weirdCode));
        // "keep the numeric code" is the caller's job (Forecast.Day.code / Current.code store the
        // raw int untouched); Wmo itself never rewrites or discards it.
    }
    @Test public void glyphBucketIsIdentityForEveryKnownBucket() {
        int[] known = {0, 1, 2, 3, 45, 48, 51, 61, 71, 80, 95};
        for (int code : known) assertEquals(Wmo.condition(code), Wmo.glyphBucket(code));
    }
    @Test public void labelsMatchPlanTable() {
        assertEquals("Clear", Wmo.label(0));
        assertEquals("Partly cloudy", Wmo.label(1));
        assertEquals("Cloudy", Wmo.label(3));
        assertEquals("Fog", Wmo.label(45));
        assertEquals("Drizzle", Wmo.label(51));
        assertEquals("Rain", Wmo.label(61));
        assertEquals("Showers", Wmo.label(80));
        assertEquals("Snow", Wmo.label(71));
        assertEquals("Storm", Wmo.label(95));
        assertEquals("Cloudy", Wmo.label(4242)); // unknown shares the CLOUDY glyph's label
    }
}
