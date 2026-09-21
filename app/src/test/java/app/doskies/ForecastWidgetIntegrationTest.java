package app.doskies;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import static org.junit.Assert.*;

/**
 * Restores the assembled-widget integration coverage that fell during the T5 bitmap conversion
 * (docs/ADVERSARIAL-REVIEW.md's P2): applies the REAL {@code forecast_widget_frame} RemoteViews via
 * {@link ForecastWidget#buildForSize} -- not just the {@code Screen} state {@link ForecastWidgetTest}
 * checks -- at one representative dp size per {@link CgaRenderer.Variant}, and checks the bitmap
 * ImageView is bound and both tap regions fire their intended pending intents. CgaRenderer's own
 * pixel drawing is a visual review, not asserted here; this only covers the plumbing around it.
 *
 * <p>Offline: Store starts empty (Waiting state) before every test, so screen() never needs a
 * network call to build.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ForecastWidgetIntegrationTest {
    private Context c;

    @Before public void before() {
        c = RuntimeEnvironment.getApplication();
        new Store(c).prefs.edit().clear().commit();
    }

    // One representative size per CgaRenderer.chooseVariant threshold (see CgaRendererTest for the
    // boundary math): STRIP (h<60), WIDE (w>=250 && h<110), MEDIUM (the fallback), LARGE (h>=200).
    private static final int[][] SIZES = {
        { 250, 40 },   // STRIP
        { 280, 90 },   // WIDE
        { 180, 150 },  // MEDIUM
        { 250, 250 },  // LARGE
    };

    @Test public void everyRepresentativeSizeBuildsAndBindsTheBitmapWithoutCrashing() {
        for (int[] size : SIZES) {
            int wDp = size[0], hDp = size[1];
            RemoteViews rv = ForecastWidget.buildForSize(c, wDp, hDp);
            assertNotNull("buildForSize returned null for " + wDp + "x" + hDp + "dp", rv);

            View root = rv.apply(c, new FrameLayout(c));
            assertNotNull("apply() returned no view for " + wDp + "x" + hDp + "dp", root);

            ImageView canvas = root.findViewById(R.id.canvas);
            assertNotNull("canvas ImageView missing for " + wDp + "x" + hDp + "dp", canvas);
            assertTrue("canvas should be bound to a bitmap drawable at " + wDp + "x" + hDp + "dp",
                canvas.getDrawable() instanceof BitmapDrawable);
            assertNotNull("bound drawable has no bitmap at " + wDp + "x" + hDp + "dp",
                ((BitmapDrawable) canvas.getDrawable()).getBitmap());

            assertNotNull("refresh tap target missing for " + wDp + "x" + hDp + "dp",
                root.findViewById(R.id.refresh));
        }
    }

    @Test public void tappingTheCanvasOpensMainActivity() {
        RemoteViews rv = ForecastWidget.buildForSize(c, 250, 250);
        View root = rv.apply(c, new FrameLayout(c));

        root.findViewById(R.id.canvas).performClick();

        Intent started = Shadows.shadowOf((Application) c).getNextStartedActivity();
        assertNotNull("clicking the canvas should start an activity", started);
        assertEquals(MainActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test public void tappingRefreshBroadcastsTheRefreshAction() {
        RemoteViews rv = ForecastWidget.buildForSize(c, 250, 250);
        View root = rv.apply(c, new FrameLayout(c));

        root.findViewById(R.id.refresh).performClick();

        ShadowApplication shadow = Shadows.shadowOf((Application) c);
        List<Intent> broadcasts = shadow.getBroadcastIntents();
        boolean sawRefresh = false;
        for (Intent i : broadcasts) {
            if ("app.doskies.REFRESH".equals(i.getAction())) sawRefresh = true;
        }
        assertTrue("clicking refresh should broadcast the REFRESH action; saw: " + broadcasts, sawRefresh);
    }
}
