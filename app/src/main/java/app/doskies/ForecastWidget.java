package app.doskies;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.RemoteViews;
import java.util.Locale;

/**
 * Home-screen widget provider. Renders the CGA panel look ({@link CgaRenderer}) to a bitmap sized to
 * each widget's current dimensions, so the pixel font, hand-drawn glyphs, double border and
 * scanlines stay crisp. Reads {@link Store}'s last snapshot only; it never fetches on its own.
 * {@code onUpdate} schedules the periodic refresh job and kicks one immediate refresh so a freshly
 * placed widget does not sit on stale or empty data.
 *
 * <p>Layout adapts to the widget's shape (see {@link CgaRenderer#chooseVariant}): a wide, short
 * widget draws the one-row 7-day layout; a tall one draws the stacked list; small sizes fall back to
 * MEDIUM or the single-line STRIP.
 */
public final class ForecastWidget extends AppWidgetProvider {
    private static final String REFRESH = "app.doskies.REFRESH";
    /** Cap the rendered bitmap so a very large widget cannot exceed the RemoteViews bitmap limit. */
    private static final int MAX_BITMAP_PX = 1600;

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c, m, id));
        RefreshJob.schedule(c);
        RefreshJob.now(c);
    }

    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
        m.updateAppWidget(id, build(c, m, id));
    }

    @Override public void onDisabled(Context c) { RefreshJob.cancel(c); }

    @Override public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (REFRESH.equals(intent.getAction())) {
            updateAll(c);       // repaint now (e.g. an error/waiting state) ...
            RefreshJob.now(c);  // ... while the real fetch runs off the main thread.
        }
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, ForecastWidget.class))) {
            m.updateAppWidget(id, build(c, m, id));
        }
    }

    /** Renders one widget instance at its current size. */
    private static RemoteViews build(Context c, AppWidgetManager m, int id) {
        Bundle opts = m.getAppWidgetOptions(id);
        int wDp = optionDp(opts, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
        int hDp = optionDp(opts, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110);
        return buildForSize(c, wDp, hDp);
    }

    /**
     * The actual assembled-widget build, extracted from {@link #build} as a plumbing-only seam so
     * it can be driven directly by a test at representative sizes (docs/ADVERSARIAL-REVIEW.md's
     * P2: widget integration coverage). Renders the CGA-panel bitmap at wDp x hDp, builds the real
     * {@code forecast_widget_frame} RemoteViews, binds the bitmap ImageView, and wires both tap
     * targets (open-app on the canvas, refresh on the overlay). No drawing lives here; that stays
     * entirely in {@link CgaRenderer}.
     */
    static RemoteViews buildForSize(Context c, int wDp, int hDp) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        CgaRenderer.Variant v = CgaRenderer.chooseVariant(wDp, hDp);
        int wPx = clampPx(Math.round(wDp * dm.density));
        int hPx = clampPx(Math.round(hDp * dm.density));

        Bitmap bmp = new CgaRenderer(c).render(wPx, hPx, v, screen(c));

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.forecast_widget_frame);
        rv.setImageViewBitmap(R.id.canvas, bmp);
        rv.setOnClickPendingIntent(R.id.canvas, PendingIntent.getActivity(c, 0,
            new Intent(c, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        rv.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(c, 1,
            new Intent(c, ForecastWidget.class).setAction(REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return rv;
    }

    private static int optionDp(Bundle opts, String key, int fallback) {
        int v = opts == null ? 0 : opts.getInt(key, 0);
        return v > 0 ? v : fallback;
    }

    private static int clampPx(int px) { return Math.max(1, Math.min(px, MAX_BITMAP_PX)); }

    /**
     * Builds the render state from Store's last snapshot only; never fetches here. Demo mode
     * (Store.demo(), an explicit user choice) always renders Weather.demo(), marked DEMO, and is
     * never substituted for a failed fetch. A present snapshot renders even when an error is set (a
     * failed refresh must not blank the last good forecast); the error then shows on the status line.
     *
     * <p>Snapshot provenance (docs/ADVERSARIAL-REVIEW.md's P1): a present snapshot is parsed with
     * {@link Store#snapshotUnit()} and labeled with {@link Store#snapshotPlace()} -- the unit and
     * place it was actually FETCHED under -- never the live, possibly-since-changed requested
     * units()/placeLabel(). That keeps a unit change or a city change from relabeling or
     * reconverting stale data after a failed refresh. The waiting and demo states have no fetched
     * data to be faithful to, so they use the requested placeLabel() instead.
     */
    static CgaRenderer.Screen screen(Context c) {
        Store s = new Store(c);
        CgaRenderer.Screen scr = new CgaRenderer.Screen();
        scr.opacity = s.widgetOpacity();

        if (s.demo()) {
            scr.place = s.placeLabel().toUpperCase(Locale.US);
            scr.forecast = Weather.demo();
            scr.demo = true;
            scr.status = "Demo data, not live";
            return scr;
        }

        String snapshot = s.snapshot();
        boolean hasError = !s.error().isEmpty();
        if (snapshot.isEmpty()) {
            scr.place = s.placeLabel().toUpperCase(Locale.US);
            scr.forecast = null;
            scr.status = hasError ? s.error() : "Tap refresh to load";
            scr.statusIsError = hasError;
            return scr;
        }
        scr.place = s.snapshotPlace().toUpperCase(Locale.US);
        try {
            scr.forecast = Weather.parse(snapshot, s.snapshotUnit());
        } catch (Exception e) {
            // A stored snapshot should always parse, but a corrupt value must never crash the widget.
            scr.forecast = null;
        }
        scr.status = hasError ? s.error() : checkedText(s.checked());
        scr.statusIsError = hasError;
        return scr;
    }

    /** Package-visible so MainActivity's status line can share this exact wording. */
    static String checkedText(long checkedAt) {
        if (checkedAt <= 0) return "Not checked yet";
        long ageMs = System.currentTimeMillis() - checkedAt;
        if (ageMs < 60_000L) return "Updated just now";
        long minutes = ageMs / 60_000L;
        if (minutes < 60) return "Updated " + minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return "Updated " + hours + "h ago";
        return "Updated " + (hours / 24) + "d ago";
    }
}
