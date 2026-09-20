package app.doskies;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Renders ForecastWidget offline, for every size variant and for the three data states (waiting,
 * error-with-snapshot kept, and normal). No network call is ever made: every snapshot here is
 * either empty or a labeled fixture serialized to the Open-Meteo response shape (mirroring
 * Weather.demo()'s values) so it round-trips through Weather.parse exactly like a live fetch would.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ForecastWidgetTest {

    // DEMO fixture: mirrors Weather.demo()'s current + 7 days, serialized to the real contract shape.
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

    private static View measure(Context c, ForecastWidget.Variant v, int widthDp, int heightDp) {
        View widget = ForecastWidget.variant(c, v).apply(c, new FrameLayout(c));
        float density = c.getResources().getDisplayMetrics().density;
        int width = Math.round(widthDp * density), height = Math.round(heightDp * density);
        widget.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        widget.layout(0, 0, width, height);
        return widget;
    }

    private static String text(View widget, int id) { return ((TextView) widget.findViewById(id)).getText().toString(); }

    // ---- per-variant smoke test: renders the demo forecast without crashing, key fields populated ----

    @Test public void stripRendersDemoWithoutCrashing() {
        new Store(c).setSnapshot(DEMO_SNAPSHOT);
        View widget = measure(c, ForecastWidget.Variant.STRIP, 110, 40);
        assertFalse("current_temp should not be empty", text(widget, R.id.current_temp).trim().isEmpty());
        assertNotEquals("--", text(widget, R.id.current_temp));
    }

    @Test public void mediumRendersDemoWithoutCrashing() {
        new Store(c).setSnapshot(DEMO_SNAPSHOT);
        View widget = measure(c, ForecastWidget.Variant.MEDIUM, 180, 110);
        assertFalse(text(widget, R.id.current_temp).trim().isEmpty());
        assertFalse(text(widget, R.id.current_cond).trim().isEmpty());
    }

    @Test public void largeRendersDemoWithDayRowsPresent() {
        new Store(c).setSnapshot(DEMO_SNAPSHOT);
        View widget = measure(c, ForecastWidget.Variant.LARGE, 180, 250);
        assertFalse(text(widget, R.id.current_temp).trim().isEmpty());

        int[] rows = {R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6};
        int[] dows = {R.id.day_0_dow, R.id.day_1_dow, R.id.day_2_dow, R.id.day_3_dow, R.id.day_4_dow, R.id.day_5_dow, R.id.day_6_dow};
        int[] his  = {R.id.day_0_hi, R.id.day_1_hi, R.id.day_2_hi, R.id.day_3_hi, R.id.day_4_hi, R.id.day_5_hi, R.id.day_6_hi};
        int[] los  = {R.id.day_0_lo, R.id.day_1_lo, R.id.day_2_lo, R.id.day_3_lo, R.id.day_4_lo, R.id.day_5_lo, R.id.day_6_lo};
        int[] pops = {R.id.day_0_precip, R.id.day_1_precip, R.id.day_2_precip, R.id.day_3_precip, R.id.day_4_precip,
            R.id.day_5_precip, R.id.day_6_precip};
        for (int i = 0; i < 7; i++) {
            assertEquals("day_" + i + " row should be visible", View.VISIBLE, widget.findViewById(rows[i]).getVisibility());
            assertFalse("day_" + i + " dow should not be empty", text(widget, dows[i]).trim().isEmpty());
            assertFalse("day_" + i + " hi should not be empty", text(widget, his[i]).trim().isEmpty());
            assertFalse("day_" + i + " lo should not be empty", text(widget, los[i]).trim().isEmpty());
            assertFalse("day_" + i + " precip should not be empty", text(widget, pops[i]).trim().isEmpty());
        }
        assertEquals("Today", text(widget, R.id.day_0_dow));
    }

    // ---- the three states ----

    @Test public void noSnapshotShowsWaiting() {
        // Store starts empty (before() clears it): no snapshot at all.
        View widget = measure(c, ForecastWidget.Variant.LARGE, 180, 250);
        assertEquals("--", text(widget, R.id.current_temp));
        assertEquals("Waiting", text(widget, R.id.current_cond));
        for (int id : new int[]{R.id.day_0, R.id.day_3, R.id.day_6}) {
            assertEquals(View.GONE, widget.findViewById(id).getVisibility());
        }
    }

    @Test public void errorWithSnapshotStillShowsData() {
        Store s = new Store(c);
        s.setSnapshot(DEMO_SNAPSHOT);
        s.setError("Could not refresh. Check your connection.");
        View widget = measure(c, ForecastWidget.Variant.LARGE, 180, 250);
        // The forecast is never blanked by an error: the last snapshot still renders.
        assertNotEquals("--", text(widget, R.id.current_temp));
        assertNotEquals("Waiting", text(widget, R.id.current_cond));
        assertEquals(View.VISIBLE, widget.findViewById(R.id.day_0).getVisibility());
        // The error is surfaced on the freshness line, not silently swallowed.
        assertEquals("Could not refresh. Check your connection.", text(widget, R.id.freshness));
    }

    @Test public void normalStateShowsForecastAndFreshness() {
        Store s = new Store(c);
        s.setSnapshot(DEMO_SNAPSHOT);
        assertTrue(s.error().isEmpty());
        View widget = measure(c, ForecastWidget.Variant.LARGE, 180, 250);
        assertEquals("71°F", text(widget, R.id.current_temp));
        assertEquals("Partly cloudy", text(widget, R.id.current_cond));
        assertFalse(text(widget, R.id.freshness).trim().isEmpty());
        assertNotEquals("Could not refresh. Check your connection.", text(widget, R.id.freshness));
    }

    @Test public void tapTargetsAreWired() {
        new Store(c).setSnapshot(DEMO_SNAPSHOT);
        View widget = measure(c, ForecastWidget.Variant.LARGE, 180, 250);
        assertTrue(widget.findViewById(R.id.card).isClickable());
        assertTrue(widget.findViewById(R.id.refresh).isClickable());
    }
}
