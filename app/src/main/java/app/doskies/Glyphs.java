package app.doskies;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * The ten hand-authored 12x12 pixel weather glyphs (see docs/T5-LOOK-SPEC.md). Each grid is the
 * source of truth for the DOS/CGA look; they are drawn as scaled solid rects so they stay crisp at
 * any widget size. Char codes: Y = yellow, W = white, G = light gray, C = bright cyan, space = empty.
 *
 * <p>Selection uses {@link Wmo#condition(int)} (which returns UNKNOWN for an unmapped code), so an
 * unknown code draws the distinct "?" glyph rather than being folded into CLOUDY.
 */
final class Glyphs {
    static final int GRID = 12;

    private static final int Y = 0xFFFFFF55, W = 0xFFFFFFFF, G = 0xFFAAAAAA, C = 0xFF55FFFF;

    private static int color(char ch) {
        switch (ch) {
            case 'Y': return Y;
            case 'W': return W;
            case 'G': return G;
            case 'C': return C;
            default:  return 0; // empty
        }
    }

    static String[] grid(Wmo.Condition cond) {
        switch (cond) {
            case CLEAR:   return CLEAR;
            case PARTLY:  return PARTLY;
            case CLOUDY:  return CLOUDY;
            case FOG:     return FOG;
            case DRIZZLE: return DRIZZLE;
            case RAIN:    return RAIN;
            case SHOWERS: return SHOWERS;
            case SNOW:    return SNOW;
            case THUNDER: return THUNDER;
            case UNKNOWN:
            default:      return UNKNOWN;
        }
    }

    /** Draws the glyph for {@code code} filling a {@code size}x{@code size} box at (left, top). */
    static void draw(Canvas cv, Paint p, int code, float left, float top, float size) {
        draw(cv, p, Wmo.condition(code), left, top, size);
    }

    static void draw(Canvas cv, Paint p, Wmo.Condition cond, float left, float top, float size) {
        String[] g = grid(cond);
        float cell = size / GRID;
        boolean priorAa = p.isAntiAlias();
        p.setAntiAlias(false); // crisp pixel edges
        for (int y = 0; y < g.length; y++) {
            String row = g[y];
            for (int x = 0; x < row.length(); x++) {
                int col = color(row.charAt(x));
                if (col == 0) continue;
                p.setColor(col);
                float px = left + x * cell, py = top + y * cell;
                // +cell*0.04 overlap avoids hairline seams between adjacent cells when scaled.
                cv.drawRect(px, py, px + cell + cell * 0.06f, py + cell + cell * 0.06f, p);
            }
        }
        p.setAntiAlias(priorAa);
    }

    // ---- the ten grids (12 rows x 12 chars), transcribed verbatim from docs/T5-LOOK-SPEC.md ----

    // Pulled in by a cell all round (rays no longer touch the edges) so the sun reads a touch
    // smaller and sits better beside the cloud glyphs. Kept symmetric; disc cols 3-8.
    private static final String[] CLEAR = {
        "            ", "     YY     ", "   Y    Y   ", "    YYYY    ", "   YYYYYY   ",
        " Y YYYYYY Y ", " Y YYYYYY Y ", "   YYYYYY   ", "    YYYY    ", "   Y    Y   ",
        "     YY     ", "            "
    };
    private static final String[] PARTLY = {
        " YY         ", "Y YY Y      ", " YYYY       ", "YYYYYY WWW  ", " YYYY WWWWW ",
        "  Y WWWWWWWW", "   WWWWWWWWW", "  WWWWWWWWWW", "  WGGGGGGGGW", "   GGGGGGGG ",
        "            ", "            "
    };
    private static final String[] CLOUDY = {
        "            ", "            ", "    WWWW    ", "  WWWWWWWW  ", " WWWWWWWWWW ",
        "WWWWWWWWWWWW", "WWWWWWWWWWWW", "WGGGGGGGGGGW", " GGGGGGGGGG ", "            ",
        "            ", "            "
    };
    private static final String[] FOG = {
        "            ", "   WWWWWW   ", " WWWWWWWWWW ", "WWWWWWWWWWWW", " GGGGGGGGGG ",
        "            ", "GGGGGGGGGGGG", "            ", " GGGGGGGGGG ", "            ",
        "GGGGGGGGGGGG", "            "
    };
    private static final String[] DRIZZLE = {
        "            ", "    WWWW    ", "  WWWWWWWW  ", " WWWWWWWWWW ", "WWWWWWWWWWWW",
        "WGGGGGGGGGGW", " GGGGGGGGGG ", "   C   C    ", "            ", "  C   C     ",
        "            ", "            "
    };
    private static final String[] RAIN = {
        "            ", "    WWWW    ", "  WWWWWWWW  ", " WWWWWWWWWW ", "WWWWWWWWWWWW",
        "WGGGGGGGGGGW", " GGGGGGGGGG ", "  C   C   C ", " C   C   C  ", "  C   C   C ",
        " C   C   C  ", "            "
    };
    private static final String[] SHOWERS = {
        "YY          ", " Y   WWWW   ", "Y   WWWWWWW ", "   WWWWWWWWW", "  WWWWWWWWWW",
        "  WGGGGGGGGW", "   GGGGGGGG ", "  C  C  C   ", " C  C  C    ", "  C  C  C   ",
        " C  C  C    ", "            "
    };
    private static final String[] SNOW = {
        "            ", "    WWWW    ", "  WWWWWWWW  ", " WWWWWWWWWW ", "WWWWWWWWWWWW",
        "WGGGGGGGGGGW", " GGGGGGGGGG ", "  W   W   W ", " W   W   W  ", "  W   W   W ",
        " W   W   W  ", "            "
    };
    private static final String[] THUNDER = {
        "            ", "    WWWW    ", "  WWWWWWWW  ", " WWWWWWWWWW ", "WWWWWWWWWWWW",
        "WGGGGGGGGGGW", " GGGGGGGGGG ", "     YY     ", "    YY      ", "   YYYY     ",
        "     YY     ", "    YY      "
    };
    private static final String[] UNKNOWN = {
        "            ", "   WWWWW    ", "  W     W   ", "  W     W   ", "        W   ",
        "       W    ", "     WW     ", "     W      ", "            ", "     W      ",
        "     W      ", "            "
    };

    private Glyphs() {}
}
