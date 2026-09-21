package app.doskies;

import android.content.Context;
import android.graphics.Bitmap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Covers the two pure/near-pure pieces of {@link CgaRenderer} that do not require eyeballing
 * pixels -- {@link CgaRenderer#chooseVariant} (a pure function, per docs/T5-LOOK-SPEC.md's
 * "Layouts" thresholds) and the UV color/band table -- plus a Robolectric smoke test that each
 * {@link CgaRenderer.Variant} renders to a correctly sized, non-null Bitmap without throwing, both
 * for a populated (demo) forecast and for the null-forecast "waiting" Screen. The hand-authored
 * chrome and glyph placement themselves are not asserted here; that stays a visual review.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CgaRendererTest {

    // ---- chooseVariant: STRIP h<60 ----

    @Test public void heightUnder60IsStripRegardlessOfWidth() {
        assertEquals(CgaRenderer.Variant.STRIP, CgaRenderer.chooseVariant(300, 59));
        assertEquals(CgaRenderer.Variant.STRIP, CgaRenderer.chooseVariant(80, 0));
    }

    @Test public void height60IsNotStrip() {
        assertNotEquals(CgaRenderer.Variant.STRIP, CgaRenderer.chooseVariant(100, 60));
    }

    // ---- chooseVariant: WIDE w>=250 && h<110 ----

    @Test public void wideAtTheExactWidthAndHeightThresholds() {
        assertEquals(CgaRenderer.Variant.WIDE, CgaRenderer.chooseVariant(250, 109));
        assertEquals(CgaRenderer.Variant.WIDE, CgaRenderer.chooseVariant(250, 60));
    }

    @Test public void justUnderWideWidthFallsToMedium() {
        assertEquals(CgaRenderer.Variant.MEDIUM, CgaRenderer.chooseVariant(249, 60));
    }

    @Test public void wideExtendsUpToTheNewHeightThreshold() {
        // WIDE now covers wide widgets up to h < 170; at h == 170 it becomes LARGE.
        assertEquals(CgaRenderer.Variant.WIDE, CgaRenderer.chooseVariant(250, 169));
        assertEquals(CgaRenderer.Variant.LARGE, CgaRenderer.chooseVariant(250, 170));
    }

    // ---- chooseVariant: LARGE h>=200, takes priority over WIDE's width check ----

    @Test public void height200IsLargeEvenWhenNarrow() {
        assertEquals(CgaRenderer.Variant.LARGE, CgaRenderer.chooseVariant(100, 200));
    }

    @Test public void height200IsLargeEvenWhenWideEnoughForWide() {
        assertEquals(CgaRenderer.Variant.LARGE, CgaRenderer.chooseVariant(300, 200));
    }

    @Test public void narrowJustUnderLargeHeightIsMedium() {
        // Narrow (w < 250): below the 170 height it is MEDIUM, at/above it is LARGE.
        assertEquals(CgaRenderer.Variant.MEDIUM, CgaRenderer.chooseVariant(100, 169));
        assertEquals(CgaRenderer.Variant.LARGE, CgaRenderer.chooseVariant(100, 170));
    }

    // ---- chooseVariant: MEDIUM otherwise ----

    @Test public void mediumIsTheFallbackBetweenStripWideAndLarge() {
        assertEquals(CgaRenderer.Variant.MEDIUM, CgaRenderer.chooseVariant(180, 110));
    }

    // ---- uvColor / uvBand ----

    @Test public void negativeUvIsFaintAndUnlabeled() {
        assertEquals(CgaRenderer.FAINT, CgaRenderer.uvColor(-1));
        assertEquals("", CgaRenderer.uvBand(-1));
    }

    @Test public void uvBandsAtEachBoundary() {
        assertEquals("Low", CgaRenderer.uvBand(0));
        assertEquals("Low", CgaRenderer.uvBand(2));
        assertEquals("Moderate", CgaRenderer.uvBand(3));
        assertEquals("Moderate", CgaRenderer.uvBand(5));
        assertEquals("High", CgaRenderer.uvBand(6));
        assertEquals("High", CgaRenderer.uvBand(7));
        assertEquals("Very High", CgaRenderer.uvBand(8));
        assertEquals("Very High", CgaRenderer.uvBand(10));
        assertEquals("Extreme", CgaRenderer.uvBand(11));
        assertEquals("Extreme", CgaRenderer.uvBand(20));
    }

    @Test public void uvColorsAtEachBoundary() {
        int low = 0xFF55FF55, moderate = 0xFFFFFF55, high = 0xFFFF5555, veryHighOrExtreme = 0xFFFF55FF;
        assertEquals(low, CgaRenderer.uvColor(0));
        assertEquals(low, CgaRenderer.uvColor(2));
        assertEquals(moderate, CgaRenderer.uvColor(3));
        assertEquals(moderate, CgaRenderer.uvColor(5));
        assertEquals(high, CgaRenderer.uvColor(6));
        assertEquals(high, CgaRenderer.uvColor(7));
        assertEquals(veryHighOrExtreme, CgaRenderer.uvColor(8));
        assertEquals(veryHighOrExtreme, CgaRenderer.uvColor(11)); // Extreme shares Very High's color
    }

    // ---- render smoke test: every variant, demo forecast and waiting (null-forecast) Screen ----

    private static CgaRenderer.Screen demoScreen() {
        CgaRenderer.Screen scr = new CgaRenderer.Screen();
        scr.forecast = Weather.demo();
        scr.place = "MEDFORD";
        scr.status = "Updated just now";
        scr.demo = true;
        return scr;
    }

    private static CgaRenderer.Screen waitingScreen() {
        CgaRenderer.Screen scr = new CgaRenderer.Screen();
        scr.forecast = null;
        scr.place = "MEDFORD";
        scr.status = "Tap refresh to load";
        return scr;
    }

    @Test public void everyVariantRendersTheDemoForecastAtTheRequestedSize() {
        Context c = RuntimeEnvironment.getApplication();
        CgaRenderer r = new CgaRenderer(c);
        for (CgaRenderer.Variant v : CgaRenderer.Variant.values()) {
            Bitmap bmp = r.render(180, 220, v, demoScreen());
            assertNotNull("variant " + v + " returned a null bitmap", bmp);
            assertEquals(180, bmp.getWidth());
            assertEquals(220, bmp.getHeight());
        }
    }

    @Test public void everyVariantRendersTheWaitingStateWithoutThrowing() {
        Context c = RuntimeEnvironment.getApplication();
        CgaRenderer r = new CgaRenderer(c);
        for (CgaRenderer.Variant v : CgaRenderer.Variant.values()) {
            Bitmap bmp = r.render(180, 220, v, waitingScreen());
            assertNotNull("variant " + v + " returned a null bitmap", bmp);
            assertEquals(180, bmp.getWidth());
            assertEquals(220, bmp.getHeight());
        }
    }

    @Test public void veryTinyAndVeryWideSizesStillRenderWithoutThrowing() {
        Context c = RuntimeEnvironment.getApplication();
        CgaRenderer r = new CgaRenderer(c);
        Bitmap tiny = r.render(1, 1, CgaRenderer.Variant.STRIP, demoScreen());
        assertNotNull(tiny);
        assertEquals(1, tiny.getWidth());
        assertEquals(1, tiny.getHeight());

        Bitmap wide = r.render(1600, 1600, CgaRenderer.Variant.WIDE, demoScreen());
        assertNotNull(wide);
        assertEquals(1600, wide.getWidth());
        assertEquals(1600, wide.getHeight());
    }
}
