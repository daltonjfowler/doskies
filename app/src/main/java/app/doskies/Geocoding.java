package app.doskies;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Open-Meteo city search (see PLAN.md's data contract), used by fixed-location mode. {@link
 * #search} does the network call; {@link #parse} is a pure function tests exercise directly
 * against a fixture, with no network involved.
 */
public final class Geocoding {
    private static final int MAX_BYTES = 1024 * 1024;

    /** One candidate city. admin1/countryCode are "" when Open-Meteo omits them. */
    public static final class Result {
        public final double lat;
        public final double lon;
        public final String name;
        public final String admin1;
        public final String countryCode;

        public Result(double lat, double lon, String name, String admin1, String countryCode) {
            this.lat = lat;
            this.lon = lon;
            this.name = name;
            this.admin1 = admin1;
            this.countryCode = countryCode;
        }
    }

    /** Calls the Open-Meteo geocoding endpoint (PLAN.md) and returns its matches for query. */
    public static List<Result> search(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + encoded
            + "&count=5&language=en&format=json";
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("GET");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Accept", "application/json");
            int status = c.getResponseCode();
            if (status == 429) throw new IllegalArgumentException("Open-Meteo rate limit reached. Try again later.");
            if (status != 200) throw new IllegalArgumentException("City search returned HTTP " + status + ".");
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (out.size() + n > MAX_BYTES) throw new IllegalArgumentException("City search response is too large.");
                    out.write(buf, 0, n);
                }
                return parse(out.toString(StandardCharsets.UTF_8.name()));
            }
        } finally {
            c.disconnect();
        }
    }

    /**
     * Parses a city-search response body with no network involved, so tests can exercise it
     * directly against a fixture. A response with no "results" key means zero matches (an empty
     * list, exactly what Open-Meteo returns for a query with no hits, not an error). Malformed
     * JSON throws, mirroring Weather.parse's convention: never coerce a bad response into a quiet
     * empty list that looks like a legitimate "no matches".
     */
    public static List<Result> parse(String raw) throws Exception {
        JSONObject root;
        try {
            root = new JSONObject(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("City search response is not valid JSON.");
        }
        JSONArray results = root.optJSONArray("results");
        List<Result> out = new ArrayList<>();
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            if (!r.has("latitude") || !r.has("longitude") || !r.has("name")) continue; // skip an incomplete entry
            double lat = r.getDouble("latitude");
            double lon = r.getDouble("longitude");
            String name = r.getString("name");
            String admin1 = r.optString("admin1", "");
            String countryCode = r.optString("country_code", "");
            out.add(new Result(lat, lon, name, admin1, countryCode));
        }
        return out;
    }

    private Geocoding() {}
}
