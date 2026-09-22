package app.doskies;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Draws the DOSkies widget as a single bitmap in the CGA panel look (docs/T5-LOOK-SPEC.md): navy
 * ground, a bright-cyan double border, a title bar, the forecast, and a scanline overlay. Rendering
 * to a bitmap (rather than RemoteViews TextViews) is what lets the pixel font, the hand-drawn
 * glyphs, the double border and the scanlines stay faithful at any widget size.
 *
 * <p>Hand-authored visual code. {@link #chooseVariant(int, int)} is a pure function so the layout
 * decision is unit-testable without a Canvas.
 */
final class CgaRenderer {

    // Palette (docs/T5-LOOK-SPEC.md). ARGB.
    static final int PANEL  = 0xFF101034; // navy ground
    static final int EDGE   = 0xFF55FFFF; // border + title rule + refresh glyph
    static final int TITLE  = 0xFFFFFF55; // title + stat labels + day-of-week
    static final int VALUE  = 0xFFFFFFFF; // temperatures + stat values
    static final int ACCENT = 0xFF55FFFF; // condition + precip + location
    static final int DIM    = 0xFFAAAAAA; // low temp + secondary
    static final int FAINT  = 0xFF555555; // dry precip + "--"
    static final int BAD    = 0xFFFF5555; // error status

    static int uvColor(int uv) {
        if (uv < 0) return FAINT;
        if (uv <= 2) return 0xFF55FF55;   // Low
        if (uv <= 5) return 0xFFFFFF55;   // Moderate
        if (uv <= 7) return 0xFFFF5555;   // High
        return 0xFFFF55FF;                // Very High / Extreme
    }
    static String uvBand(int uv) {
        if (uv < 0) return "";
        if (uv <= 2) return "Low";
        if (uv <= 5) return "Moderate";
        if (uv <= 7) return "High";
        if (uv <= 10) return "Very High";
        return "Extreme";
    }

    enum Variant { WIDE, LARGE, MEDIUM, STRIP }

    /** Pure layout decision from the widget's current size in dp. */
    static Variant chooseVariant(int wDp, int hDp) {
        if (hDp < 60) return Variant.STRIP;
        if (wDp >= 250 && hDp < 170) return Variant.WIDE;  // wide + not tall -> the 7-day row
        if (hDp >= 170) return Variant.LARGE;              // tall -> the stacked list
        return Variant.MEDIUM;
    }

    /** What to draw. {@code forecast} null means the waiting state. */
    static final class Screen {
        Forecast forecast;
        String place = "MEDFORD";
        String status = "";     // freshness or error text (title-adjacent line)
        boolean statusIsError;  // draw status in BAD
        boolean demo;
        int opacity = 100;      // panel background opacity, 10..100 percent
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    CgaRenderer(Context c) {
        Typeface tf;
        try { tf = c.getResources().getFont(R.font.vt323); }
        catch (Throwable t) { tf = Typeface.MONOSPACE; } // never let a font miss crash the widget
        text.setTypeface(tf);
        stroke.setStyle(Paint.Style.STROKE);
        this.d = c.getResources().getDisplayMetrics().density;
    }

    Bitmap render(int wPx, int hPx, Variant v, Screen scr) {
        int w = Math.max(1, wPx), h = Math.max(1, hPx);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        // Panel fill at the chosen opacity; the border and content are drawn opaque on top, so only
        // the navy ground goes translucent and the wallpaper shows through behind it.
        int op = Math.max(10, Math.min(100, scr.opacity));
        int alpha = Math.round(op / 100f * 255f);
        cv.drawColor((alpha << 24) | (PANEL & 0x00FFFFFF));
        drawBorder(cv, w, h);

        float pad = 3f * d + 6f * d; // border thickness + inner margin
        float left = pad, right = w - pad;

        if (v == Variant.STRIP) {
            drawStrip(cv, scr, left, right, h);
        } else {
            float titleBottom = drawTitleBar(cv, scr, left, right, pad);
            float bodyTop = titleBottom + 3f * d;
            switch (v) {
                case WIDE:   drawWide(cv, scr, left, right, bodyTop, h - pad); break;
                case LARGE:  drawStacked(cv, scr, left, right, bodyTop, h - pad, 7); break;
                case MEDIUM: default: drawStacked(cv, scr, left, right, bodyTop, h - pad, 3); break;
            }
        }

        drawScanlines(cv, w, h);
        return bmp;
    }

    // ---- chrome ----

    private void drawBorder(Canvas cv, int w, int h) {
        float sw = Math.max(1f, 1.4f * d);
        stroke.setColor(EDGE);
        stroke.setStrokeWidth(sw);
        float o = sw;             // outer line
        cv.drawRect(o, o, w - o, h - o, stroke);
        float g = sw * 2.4f;      // inner line -> "double" border
        cv.drawRect(g, g, w - g, h - g, stroke);
    }

    private void drawScanlines(Canvas cv, int w, int h) {
        stroke.setColor(0x2A000000); // ~16% black
        stroke.setStrokeWidth(1f);
        float step = Math.max(2f, 3f * d);
        for (float y = 0; y < h; y += step) cv.drawLine(0, y, w, y, stroke);
    }

    private float drawTitleBar(Canvas cv, Screen scr, float left, float right, float pad) {
        float size = clamp(13f * d, 10f, 22f);
        float top = pad;
        float baseline = top - text.getFontMetrics().ascent;
        // left: title (DEMO prefix when in demo mode)
        text.setColor(TITLE);
        drawLeft(cv, scr.demo ? "DOSkies DEMO" : "DOSkies", left, baseline, size);
        // right: refresh glyph in the corner, then the place name to its left
        float iconR = size * 0.42f;
        float iconCx = right - iconR;
        float iconCy = top + size * 0.42f;
        drawRefreshIcon(cv, iconCx, iconCy, iconR);
        float rx = iconCx - iconR - 4f * d;
        text.setColor(VALUE);
        drawRight(cv, scr.place, rx, baseline, size);
        if (scr.statusIsError) { // a compact error marker even where the full message will not fit
            float pw = measure(scr.place, size);
            text.setColor(BAD);
            drawRight(cv, "!", rx - pw - 5f * d, baseline, size);
        }
        // rule beneath
        float ruleY = top + size + 3f * d;
        stroke.setColor(EDGE);
        stroke.setStrokeWidth(Math.max(1f, d));
        cv.drawLine(left, ruleY, right, ruleY, stroke);
        return ruleY;
    }

    private void drawRefreshIcon(Canvas cv, float cx, float cy, float r) {
        stroke.setColor(ACCENT);
        stroke.setStrokeWidth(Math.max(1.4f, 1.6f * d));
        RectF arc = new RectF(cx - r, cy - r, cx + r, cy + r);
        cv.drawArc(arc, 40, 280, false, stroke);
        // arrowhead near the arc's start (about 40 degrees)
        double a = Math.toRadians(40);
        float ex = (float) (cx + r * Math.cos(a));
        float ey = (float) (cy + r * Math.sin(a));
        float hs = r * 0.6f;
        cv.drawLine(ex, ey, ex - hs, ey - hs * 0.2f, stroke);
        cv.drawLine(ex, ey, ex + hs * 0.2f, ey + hs, stroke);
    }

    // ---- STRIP ----

    private void drawStrip(Canvas cv, Screen scr, float left, float right, int h) {
        Forecast f = scr.forecast;
        float size = Math.max(13f, h * 0.5f);
        float gy = h * 0.12f, gsz = h * 0.72f;
        float x = left;
        if (f != null) {
            Glyphs.draw(cv, fill, f.current.code, x, gy, gsz);
            x += gsz + 6f * d;
        }
        float baseline = h / 2f - (text.getFontMetrics().ascent + text.getFontMetrics().descent) / 2f;
        if (f == null) {
            text.setColor(VALUE);
            x = drawLeft(cv, "-- Waiting", x, baseline, size);
        } else {
            text.setColor(VALUE);
            x = drawLeft(cv, f.current.temp + degree(f), x, baseline, size);
            x += 6f * d;
            text.setColor(ACCENT);
            drawLeft(cv, (scr.demo ? "DEMO " : "") + Wmo.label(f.current.code), x, baseline, size * 0.86f);
        }
        if (scr.statusIsError) {
            text.setColor(BAD);
            drawRight(cv, "!", right - size * 0.95f, baseline, size);
        }
        drawRefreshIcon(cv, right - size * 0.5f, h / 2f, size * 0.42f);
    }

    // ---- WIDE: current on the left, 7 day columns across ----

    private void drawWide(Canvas cv, Screen scr, float left, float right, float top, float bottom) {
        Forecast f = scr.forecast;
        float availH = bottom - top;
        float nowW = clamp((right - left) * 0.30f, 84f * d, 150f * d);
        float nowRight = left + nowW;

        // now block (left)
        if (f == null) {
            float s = clamp(14f * d, 12f, 22f);
            text.setColor(VALUE);
            drawLeft(cv, "--", left, top + s, s * 1.4f);
            text.setColor(ACCENT);
            drawLeft(cv, "Waiting", left, top + s * 2.6f, s);
        } else {
            float gsz = clamp(availH * 0.34f, 20f, availH * 0.5f);
            Glyphs.draw(cv, fill, f.current.code, left, top, gsz);
            float tsz = Math.max(22f, availH * 0.28f);
            text.setColor(VALUE);
            drawLeft(cv, f.current.temp + degree(f), left + gsz + 5f * d, top + gsz * 0.82f, tsz);
            float csz = Math.max(13f, availH * 0.14f);
            text.setColor(ACCENT);
            drawLeft(cv, (scr.demo ? "DEMO " : "") + Wmo.label(f.current.code), left, top + gsz + csz, csz);
            drawStatsStacked(cv, f, left, top + gsz + csz + 3f * d, Math.max(15f, availH * 0.15f), nowRight - left);
        }

        // vertical dashed rule
        stroke.setColor(0x66AAAAAA);
        stroke.setStrokeWidth(Math.max(1f, d));
        dashed(cv, nowRight + 3f * d, top, nowRight + 3f * d, bottom, true);

        if (f == null) return;
        // 7 day columns
        float colsLeft = nowRight + 8f * d;
        float colW = (right - colsLeft) / 7f;
        float line = Math.max(11f, availH / 5.2f);
        // Cap the glyph to the vertical room left after the four text rows (DOW + hi + lo + precip),
        // so the precip row is never clipped. drawWide used a fixed 1.7*line glyph and availH/4.8
        // rows, which summed taller than availH on wide panels and pushed precip off the bottom.
        float gsz = Math.min(colW * 0.82f, Math.max(6f, availH - 4.35f * line - 2f * d));
        for (int i = 0; i < 7 && i < f.days.length; i++) {
            Forecast.Day day = f.days[i];
            float cx = colsLeft + colW * (i + 0.5f);
            float y = top;
            text.setColor(TITLE);
            drawCenter(cv, dow(day.date), cx, y + line, line);
            y += line + 1f * d;
            Glyphs.draw(cv, fill, day.code, cx - gsz / 2f, y, gsz);
            y += gsz + 1f * d;
            text.setColor(VALUE);
            drawCenter(cv, day.hi + "°", cx, y + line, line);
            y += line;
            text.setColor(DIM);
            drawCenter(cv, day.lo + "°", cx, y + line, line);
            y += line;
            text.setColor(day.precipChancePct >= 25 ? ACCENT : FAINT);
            drawCenter(cv, day.precipChancePct + "%", cx, y + line, line * 0.92f);
        }
    }

    // ---- LARGE / MEDIUM: current on top, N day rows stacked ----

    private void drawStacked(Canvas cv, Screen scr, float left, float right, float top, float bottom, int rows) {
        Forecast f = scr.forecast;
        if (f == null) {
            float s = clamp(16f * d, 13f, 24f);
            text.setColor(VALUE);
            drawLeft(cv, "--", left, top + s, s * 1.4f);
            text.setColor(ACCENT);
            drawLeft(cv, "Waiting", left, top + s * 2.8f, s);
            if (!scr.status.isEmpty()) {
                text.setColor(scr.statusIsError ? BAD : DIM);
                drawLeft(cv, scr.status, left, bottom - s * 0.2f, clamp(12f * d, 10f, 15f));
            }
            return;
        }
        // current row: glyph + temp, condition beneath. Sizes scale with the panel height H.
        float H = bottom - top;
        float gsz = Math.max(26f, H * 0.17f);
        Glyphs.draw(cv, fill, f.current.code, left, top, gsz);
        float tsz = Math.max(22f, H * 0.15f);
        text.setColor(VALUE);
        drawLeft(cv, f.current.temp + degree(f), left + gsz + 6f * d, top + tsz * 0.9f, tsz);
        float csz = Math.max(13f, H * 0.07f);
        text.setColor(ACCENT);
        drawLeft(cv, Wmo.label(f.current.code), left + gsz + 6f * d, top + tsz * 0.9f + csz + 2f * d, csz);
        float y = top + gsz + 3f * d;
        // stats line
        drawStatsRow(cv, f, left, y + csz, Math.max(12f, H * 0.06f));
        y += csz + 6f * d;
        // status line (freshness / error)
        if (!scr.status.isEmpty()) {
            text.setColor(scr.statusIsError ? BAD : DIM);
            drawLeft(cv, scr.status, left, y + csz * 0.9f, clamp(12f * d, 10f, 14f));
            y += csz + 2f * d;
        }
        // dashed rule
        stroke.setColor(0x66AAAAAA);
        stroke.setStrokeWidth(Math.max(1f, d));
        dashed(cv, left, y + 2f * d, right, y + 2f * d, false);
        y += 5f * d;
        // day rows
        int n = Math.min(rows, f.days.length);
        float rowH = (bottom - y) / Math.max(1, n);
        float rsz = Math.max(13f, rowH * 0.66f);
        float gcol = left + rsz * 3.0f;         // day-of-week column width
        float glyphSz = Math.min(rowH * 0.82f, rsz * 1.5f);
        for (int i = 0; i < n; i++) {
            Forecast.Day day = f.days[i];
            float ry = y + rowH * i;
            text.setTextSize(rsz);
            float base = ry + rowH * 0.5f - (text.getFontMetrics().ascent + text.getFontMetrics().descent) / 2f;
            text.setColor(TITLE);
            drawLeft(cv, dow(day.date), left, base, rsz);
            Glyphs.draw(cv, fill, day.code, gcol, ry + (rowH - glyphSz) / 2f, glyphSz);
            float tx = gcol + glyphSz + 6f * d;
            text.setColor(VALUE);
            tx = drawLeft(cv, day.hi + "°", tx, base, rsz);
            text.setColor(DIM);
            tx = drawLeft(cv, " " + day.lo + "°", tx, base, rsz);
            String pct = day.precipChancePct + "%";
            float precipW = measure(pct, rsz);
            text.setColor(day.precipChancePct >= 25 ? ACCENT : FAINT);
            drawRight(cv, pct, right, base, rsz);
            // The condition word fills the empty middle of a wide row; skipped if the row is too narrow.
            String cond = Wmo.label(day.code);
            float condRight = right - precipW - 12f * d;
            if (condRight - tx > measure(cond, rsz) + 10f * d) {
                text.setColor(ACCENT);
                drawRight(cv, cond, condRight, base, rsz);
            }
        }
    }

    // ---- stats ----

    private void drawStatsRow(Canvas cv, Forecast f, float x, float baseline, float size) {
        Forecast.Current c = f.current;
        x = seg(cv, "UV ", x, baseline, size, TITLE);
        if (c.uvMax < 0) {
            x = seg(cv, "-- ", x, baseline, size, FAINT);
        } else {
            x = seg(cv, c.uvMax + " ", x, baseline, size, VALUE);
            x = seg(cv, uvBand(c.uvMax) + "  ", x, baseline, size, uvColor(c.uvMax));
        }
        x = seg(cv, "HUM ", x, baseline, size, TITLE);
        x = seg(cv, Forecast.Current.display(c.humidity) + (c.humidity < 0 ? "  " : "%  "), x, baseline, size, VALUE);
        x = seg(cv, "WIND ", x, baseline, size, TITLE);
        String wind = c.wind < 0 ? "--" : (c.wind + " " + c.windUnit);
        seg(cv, wind, x, baseline, size, VALUE);
    }

    private void drawStatsStacked(Canvas cv, Forecast f, float x, float top, float size, float maxW) {
        Forecast.Current c = f.current;
        // Scale down so the widest stat line fits the now-block width (maxW).
        String uvLine = "UV " + (c.uvMax < 0 ? "--" : (c.uvMax + " " + uvBand(c.uvMax)));
        String humLine = "HUM " + (c.humidity < 0 ? "--" : (c.humidity + "%"));
        String windLine = "WIND " + (c.wind < 0 ? "--" : (c.wind + " " + c.windUnit));
        float widest = Math.max(measure(uvLine, size), Math.max(measure(humLine, size), measure(windLine, size)));
        if (maxW > 0 && widest > maxW) size *= maxW / widest;
        float y = top + size;
        float xx = seg(cv, "UV ", x, y, size, TITLE);
        if (c.uvMax < 0) seg(cv, "--", xx, y, size, FAINT);
        else { xx = seg(cv, c.uvMax + " ", xx, y, size, VALUE); seg(cv, uvBand(c.uvMax), xx, y, size, uvColor(c.uvMax)); }
        y += size + 1f * d;
        xx = seg(cv, "HUM ", x, y, size, TITLE);
        seg(cv, Forecast.Current.display(c.humidity) + (c.humidity < 0 ? "" : "%"), xx, y, size, VALUE);
        y += size + 1f * d;
        xx = seg(cv, "WIND ", x, y, size, TITLE);
        seg(cv, c.wind < 0 ? "--" : (c.wind + " " + c.windUnit), xx, y, size, VALUE);
    }

    /** Draws one left-aligned colored segment at (x, baseline); returns the x after it. */
    private float seg(Canvas cv, String s, float x, float baseline, float size, int color) {
        text.setColor(color);
        return drawLeft(cv, s, x, baseline, size);
    }

    // ---- text + line helpers ----

    private float measure(String s, float size) { text.setTextSize(size); return text.measureText(s); }

    private float drawLeft(Canvas cv, String s, float x, float baseline, float size) {
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.LEFT);
        cv.drawText(s, x, baseline, text);
        return x + text.measureText(s);
    }
    private void drawRight(Canvas cv, String s, float x, float baseline, float size) {
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(s, x, baseline, text);
    }
    private void drawCenter(Canvas cv, String s, float cx, float baseline, float size) {
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.CENTER);
        cv.drawText(s, cx, baseline, text);
    }

    private void dashed(Canvas cv, float x0, float y0, float x1, float y1, boolean vertical) {
        float dash = 3f * d, gap = 3f * d;
        if (vertical) {
            for (float y = y0; y < y1; y += dash + gap) cv.drawLine(x0, y, x0, Math.min(y + dash, y1), stroke);
        } else {
            for (float x = x0; x < x1; x += dash + gap) cv.drawLine(x, y0, Math.min(x + dash, x1), y0, stroke);
        }
    }

    private static String degree(Forecast f) { return "°" + f.unit; }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Uppercase 3-letter weekday for an ISO date; "?" if it will not parse. */
    private static String dow(String isoDate) {
        try {
            return java.time.LocalDate.parse(isoDate)
                .getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)
                .toUpperCase(java.util.Locale.US);
        } catch (Exception e) { return "?"; }
    }
}
