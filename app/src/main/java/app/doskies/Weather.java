package app.doskies;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Parses an Open-Meteo forecast response (see PLAN.md's data contract) into a {@link Forecast}.
 *
 * <p>A response missing a required field, or whose daily arrays are not all exactly seven long
 * (one per {@code Forecast.days} slot), is rejected with a clear exception rather than coerced
 * into zeros: a bad parse must never look like a quiet, confident "0% chance of rain".
 */
public final class Weather {
    private static final int DAYS = 7;

    /** unit ('F' or 'C') is recorded on the Forecast; it must match what the fetch requested. */
    public static Forecast parse(String raw, char unit) throws Exception {
        JSONObject root;
        try {
            root = new JSONObject(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("Forecast response is not valid JSON.");
        }

        JSONObject current = root.optJSONObject("current");
        JSONObject daily = root.optJSONObject("daily");
        if (current == null || daily == null)
            throw new IllegalArgumentException("Forecast response is missing current or daily data.");
        if (!current.has("temperature_2m") || current.isNull("temperature_2m")
                || !current.has("weather_code") || current.isNull("weather_code")
                || !current.has("precipitation") || current.isNull("precipitation"))
            throw new IllegalArgumentException("Forecast response is missing current conditions.");

        int curTemp = round(current.getDouble("temperature_2m"));
        int curCode = current.getInt("weather_code");
        double curPrecip = current.getDouble("precipitation");

        // Optional, best-effort fields: absent or null means UNKNOWN, never a thrown exception
        // and never coerced to 0 (0 is itself a valid humidity/UV/wind reading).
        int humidity = optRoundedInt(current, "relative_humidity_2m");
        int wind = optRoundedInt(current, "wind_speed_10m");
        int windDir = optRoundedInt(current, "wind_direction_10m");
        String windUnit = unit == 'C' ? "km/h" : "mph";

        JSONArray time = daily.optJSONArray("time");
        JSONArray codes = daily.optJSONArray("weather_code");
        JSONArray his = daily.optJSONArray("temperature_2m_max");
        JSONArray los = daily.optJSONArray("temperature_2m_min");
        JSONArray pops = daily.optJSONArray("precipitation_probability_max");
        if (time == null || codes == null || his == null || los == null || pops == null)
            throw new IllegalArgumentException("Forecast response is missing a daily field.");
        int n = time.length();
        if (n != DAYS || codes.length() != n || his.length() != n || los.length() != n || pops.length() != n)
            throw new IllegalArgumentException("Forecast response daily arrays are incomplete.");

        JSONArray uvMaxes = daily.optJSONArray("uv_index_max");
        int uvMax = optRoundedInt(uvMaxes, 0);

        Forecast.Day[] days = new Forecast.Day[n];
        for (int i = 0; i < n; i++) {
            String date = time.getString(i);
            int hi = round(his.getDouble(i));
            int lo = round(los.getDouble(i));
            int pop = clampPct(round(pops.getDouble(i)));
            int code = codes.getInt(i);
            days[i] = new Forecast.Day(date, hi, lo, pop, code);
        }
        Forecast.Current cur = new Forecast.Current(curTemp, curCode, curPrecip, humidity, uvMax, wind, windUnit, windDir);
        return new Forecast(unit, cur, days);
    }

    /**
     * A clearly synthetic forecast for previews and tests. Never a substitute for a failed live
     * fetch: callers must keep the last real snapshot on failure and only reach for this when the
     * demo mode is explicitly on.
     */
    public static Forecast demo() {
        Forecast.Day[] days = new Forecast.Day[] {
            new Forecast.Day("2026-09-20", 72, 58, 10, 1),
            new Forecast.Day("2026-09-21", 70, 55, 20, 2),
            new Forecast.Day("2026-09-22", 66, 52, 70, 61),
            new Forecast.Day("2026-09-23", 64, 50, 85, 63),
            new Forecast.Day("2026-09-24", 78, 60, 0, 0),
            new Forecast.Day("2026-09-25", 72, 59, 90, 95),
            new Forecast.Day("2026-09-26", 40, 29, 40, 71),
        };
        return new Forecast('F', new Forecast.Current(71, 1, 0.0, 78, 5, 8, "mph", 315), days);
    }

    private static int round(double v) { return (int) Math.round(v); }
    private static int clampPct(int v) { return Math.max(0, Math.min(100, v)); }

    /** Reads an optional numeric field from a JSONObject; UNKNOWN (-1) if absent, null, or not a number. */
    private static int optRoundedInt(JSONObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return Forecast.UNKNOWN;
        try {
            return round(obj.getDouble(key));
        } catch (Exception e) {
            return Forecast.UNKNOWN;
        }
    }

    /** Reads an optional numeric entry from a JSONArray (e.g. daily.uv_index_max[0]); UNKNOWN (-1) if absent or null. */
    private static int optRoundedInt(JSONArray arr, int index) {
        if (arr == null || index < 0 || index >= arr.length() || arr.isNull(index)) return Forecast.UNKNOWN;
        try {
            return round(arr.getDouble(index));
        } catch (Exception e) {
            return Forecast.UNKNOWN;
        }
    }

    private Weather() {}
}
