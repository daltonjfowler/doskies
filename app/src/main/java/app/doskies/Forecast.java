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

    /** Current conditions. temp is a rounded int in the forecast's unit. */
    public static final class Current {
        public final int temp;
        public final int code;      // raw WMO weather code; see Wmo
        public final double precip; // current precipitation, in the fetch's precipitation_unit

        public Current(int temp, int code, double precip) {
            this.temp = temp;
            this.code = code;
            this.precip = precip;
        }
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
