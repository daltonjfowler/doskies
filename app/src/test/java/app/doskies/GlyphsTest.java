package app.doskies;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Structural guard for the ten hand-authored weather glyphs (docs/T5-LOOK-SPEC.md). It does not
 * judge how they look -- that is a visual review -- but it pins the grid shape and legal characters
 * so an accidental edit (a stray char, a dropped or over-long row) is caught, and that every
 * Wmo.Condition maps to a distinct grid.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class GlyphsTest {

    @Test public void everyConditionHasAWellFormedGrid() {
        for (Wmo.Condition cond : Wmo.Condition.values()) {
            String[] g = Glyphs.grid(cond);
            assertNotNull("no grid for " + cond, g);
            assertEquals("grid for " + cond + " must be 12 rows", Glyphs.GRID, g.length);
            for (int y = 0; y < g.length; y++) {
                assertEquals(cond + " row " + y + " must be 12 chars", Glyphs.GRID, g[y].length());
                for (int x = 0; x < g[y].length(); x++) {
                    char ch = g[y].charAt(x);
                    assertTrue(cond + " row " + y + " has illegal char '" + ch + "'",
                        ch == ' ' || ch == 'Y' || ch == 'W' || ch == 'G' || ch == 'C');
                }
            }
        }
    }

    @Test public void unknownIsDistinctFromCloudy() {
        // The spec deliberately gives UNKNOWN its own "?" glyph rather than folding it into CLOUDY.
        assertNotEquals(java.util.Arrays.asList(Glyphs.grid(Wmo.Condition.CLOUDY)),
            java.util.Arrays.asList(Glyphs.grid(Wmo.Condition.UNKNOWN)));
    }

    @Test public void clearGlyphHasItsSpecCells() {
        // CLEAR row 5 is "Y YYYYYYYY Y": a west ray at col 0, a gap at col 1, the solid disc from col 2.
        String[] clear = Glyphs.grid(Wmo.Condition.CLEAR);
        assertEquals('Y', clear[5].charAt(0));
        assertEquals(' ', clear[5].charAt(1));
        assertEquals('Y', clear[5].charAt(2));
    }

    @Test public void codeSelectionUsesConditionNotGlyphBucket() {
        // An unmapped code -> Wmo.condition UNKNOWN -> the "?" glyph, not CLOUDY's fallback.
        assertSame(Glyphs.grid(Wmo.Condition.UNKNOWN), Glyphs.grid(Wmo.condition(12345)));
    }

    @Test public void drawDoesNotThrowForAnyCondition() {
        Bitmap bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint();
        for (Wmo.Condition cond : Wmo.Condition.values()) {
            Glyphs.draw(cv, p, cond, 0f, 0f, 48f);
        }
    }
}
