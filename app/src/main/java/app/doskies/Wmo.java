package app.doskies;

/**
 * Maps an Open-Meteo WMO weather code to one of the ten glyph buckets DOSkies draws, per
 * PLAN.md's table. A code outside the table is UNKNOWN: it falls back to the CLOUDY glyph for
 * rendering, but the caller keeps the original numeric code (Forecast.Day.code / Current.code)
 * rather than losing it.
 */
public final class Wmo {

    public enum Condition { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SHOWERS, SNOW, THUNDER, UNKNOWN }

    public static Condition condition(int code) {
        switch (code) {
            case 0: return Condition.CLEAR;
            case 1: case 2: return Condition.PARTLY;
            case 3: return Condition.CLOUDY;
            case 45: case 48: return Condition.FOG;
            case 51: case 53: case 55: case 56: case 57: return Condition.DRIZZLE;
            case 61: case 63: case 65: case 66: case 67: return Condition.RAIN;
            case 80: case 81: case 82: return Condition.SHOWERS;
            case 71: case 73: case 75: case 77: case 85: case 86: return Condition.SNOW;
            case 95: case 96: case 99: return Condition.THUNDER;
            default: return Condition.UNKNOWN;
        }
    }

    /** Which glyph to draw for a code: UNKNOWN renders as CLOUDY, every other bucket renders as itself. */
    public static Condition glyphBucket(int code) {
        Condition c = condition(code);
        return c == Condition.UNKNOWN ? Condition.CLOUDY : c;
    }

    /** Short label per PLAN.md's table. UNKNOWN shares CLOUDY's label since it draws the same glyph. */
    public static String label(int code) {
        switch (condition(code)) {
            case CLEAR: return "Clear";
            case PARTLY: return "Partly cloudy";
            case FOG: return "Fog";
            case DRIZZLE: return "Drizzle";
            case RAIN: return "Rain";
            case SHOWERS: return "Showers";
            case SNOW: return "Snow";
            case THUNDER: return "Storm";
            case CLOUDY:
            case UNKNOWN:
            default: return "Cloudy";
        }
    }

    private Wmo() {}
}
