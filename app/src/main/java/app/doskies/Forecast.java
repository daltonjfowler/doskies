package app.doskies;

/**
 * A weather snapshot: current conditions plus a 7-day outlook. Plain data, no framework
 * dependency, so it can be built, passed around, and unit-tested without Android.
 *
 * <p>{@code unit} records which temperature unit {@code current.temp} and each day's hi/lo are
 * already expressed in ('F' or 'C'), so a renderer never has to guess or reconvert.
 */
public final class Forecast {
    public final char unit;
    public final Current current;
    public final Day[] days;

    public Forecast(char unit, Current current, Day[] days) {
        this.unit = unit;
        this.current = current;
        this.days = days;
    }

    /** Sentinel for an optional current-conditions field that was absent or null in the response. */
    public static final int UNKNOWN = -1;

    /** Current conditions. temp is a rounded int in the forecast's unit. */
    public static final class Current {
        public final int temp;
        public final int code;      // raw WMO weather code; see Wmo
        public final double precip; // current precipitation, in the fetch's precipitation_unit
        public final int humidity;  // relative_humidity_2m, percent; UNKNOWN (-1) if absent
        public final int uvMax;     // today's daily.uv_index_max[0], rounded; UNKNOWN (-1) if absent
        public final int wind;      // wind_speed_10m, rounded, in windUnit; UNKNOWN (-1) if absent
        public final String windUnit; // "mph" or "km/h"; matches the fetch's temperature unit
        public final int windDir;   // wind_direction_10m, degrees (0=N, 90=E); UNKNOWN (-1) if absent

        public Current(int temp, int code, double precip, int humidity, int uvMax, int wind, String windUnit, int windDir) {
            this.temp = temp;
            this.code = code;
            this.precip = precip;
            this.humidity = humidity;
            this.uvMax = uvMax;
            this.wind = wind;
            this.windUnit = windUnit;
            this.windDir = windDir;
        }

        /** Renders an optional field for display: "--" when UNKNOWN, else the value as a string. */
        public static String display(int value) {
            return value == UNKNOWN ? "--" : String.valueOf(value);
        }
    }

    /** Compass point ("N", "NE", ... "NW") the wind blows FROM, from a degree bearing; "" if UNKNOWN. */
    public static String windCompass(int deg) {
        if (deg == UNKNOWN) return "";
        String[] points = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };
        int i = (int) Math.round(((deg % 360) + 360) % 360 / 45.0) % 8;
        return points[i];
    }

    /** One forecast day. hi/lo are rounded ints; precipChancePct is clamped to 0..100. */
    public static final class Day {
        public final String date; // ISO yyyy-MM-dd, as Open-Meteo returns it
        public final int hi;
        public final int lo;
        public final int precipChancePct;
        public final int code;    // raw WMO weather code; see Wmo

        public Day(String date, int hi, int lo, int precipChancePct, int code) {
            this.date = date;
            this.hi = hi;
            this.lo = lo;
            this.precipChancePct = precipChancePct;
            this.code = code;
        }
    }
}
