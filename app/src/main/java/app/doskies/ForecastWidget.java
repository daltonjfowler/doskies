package app.doskies;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Home-screen widget provider. Renders from {@link Store}'s last snapshot only; it never fetches
 * on its own. {@code onUpdate} schedules the periodic refresh job and kicks one immediate refresh
 * so a freshly placed widget does not sit on stale (or empty) data. Mirrors the mechanics of the
 * Cloudflare Usage Widget's {@code UsageWidget} (SizeF variant map, tap PendingIntents, a
 * broadcast refresh action), adapted for weather.
 *
 * <p>The layouts this class fills ({@code forecast_widget_strip.xml}, {@code forecast_widget_card.xml})
 * are placeholder standard views (T2). T5 replaces them with the hand-drawn DOS/pixel composition;
 * the view ids referenced below are the seam that pass reads and must keep.
 */
public final class ForecastWidget extends AppWidgetProvider {
    private static final String REFRESH = "app.doskies.REFRESH";
    private static final int DAYS = 7;

    private static final int[] DAY_ROW = {
        R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6
    };
    private static final int[] DAY_DOW = {
        R.id.day_0_dow, R.id.day_1_dow, R.id.day_2_dow, R.id.day_3_dow, R.id.day_4_dow, R.id.day_5_dow, R.id.day_6_dow
    };
    private static final int[] DAY_HI = {
        R.id.day_0_hi, R.id.day_1_hi, R.id.day_2_hi, R.id.day_3_hi, R.id.day_4_hi, R.id.day_5_hi, R.id.day_6_hi
    };
    private static final int[] DAY_LO = {
        R.id.day_0_lo, R.id.day_1_lo, R.id.day_2_lo, R.id.day_3_lo, R.id.day_4_lo, R.id.day_5_lo, R.id.day_6_lo
    };
    private static final int[] DAY_PRECIP = {
        R.id.day_0_precip, R.id.day_1_precip, R.id.day_2_precip, R.id.day_3_precip, R.id.day_4_precip,
        R.id.day_5_precip, R.id.day_6_precip
    };

    /** STRIP has no room for the day table or a freshness line; MEDIUM adds those but stays days-free; LARGE adds the 7 day rows. */
    enum Variant { STRIP, MEDIUM, LARGE }

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        updateAll(c);
        RefreshJob.schedule(c);
        RefreshJob.now(c);
    }

    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
        updateAll(c);
    }

    @Override public void onDisabled(Context c) { RefreshJob.cancel(c); }

    @Override public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (REFRESH.equals(intent.getAction())) {
            updateAll(c);   // repaint immediately (e.g. shows "Refresh queued" state) ...
            RefreshJob.now(c); // ... while the real fetch runs off the main thread.
        }
    }

    static void updateAll(Context c) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        for (int id : manager.getAppWidgetIds(new ComponentName(c, ForecastWidget.class))) {
            manager.updateAppWidget(id, views(c));
        }
    }

    /** Maps each supported home-screen size to a variant. apply() with no size chooses the smallest (STRIP). */
    static RemoteViews views(Context c) {
        LinkedHashMap<SizeF, RemoteViews> map = new LinkedHashMap<>();
        map.put(new SizeF(110, 40), variant(c, Variant.STRIP));
        map.put(new SizeF(180, 110), variant(c, Variant.MEDIUM));
        map.put(new SizeF(180, 250), variant(c, Variant.LARGE));
        return new RemoteViews(map);
    }

    static RemoteViews variant(Context c, Variant v) {
        RemoteViews rv = new RemoteViews(c.getPackageName(),
            v == Variant.STRIP ? R.layout.forecast_widget_strip : R.layout.forecast_widget_card);
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent refresh = PendingIntent.getBroadcast(c, 1,
            new Intent(c, ForecastWidget.class).setAction(REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.card, open);
        rv.setOnClickPendingIntent(R.id.refresh, refresh);
        render(c, rv, v);
        return rv;
    }

    /**
     * Fills the widget from Store's last snapshot only. Never fetches here; the refresh job does
     * that. Demo mode (Store.demo(), an explicit user choice, see MainActivity) is the one
     * exception: it always renders Weather.demo() instead of the real snapshot, marked DEMO so it
     * is never mistaken for a live forecast, and skips the error/snapshot states entirely.
     */
    private static void render(Context c, RemoteViews rv, Variant v) {
        Store s = new Store(c);
        if (s.demo()) {
            renderForecast(rv, v, Weather.demo(), s, true);
            return;
        }
        String snapshot = s.snapshot();
        if (snapshot.isEmpty()) {
            renderWaiting(rv, v, s);
            return;
        }
        try {
            char unit = s.units().length() > 0 ? s.units().charAt(0) : 'F';
            Forecast f = Weather.parse(snapshot, unit);
            renderForecast(rv, v, f, s, false);
        } catch (Exception e) {
            // A stored snapshot should always parse (Repository validates before storing), but a
            // corrupt or hand-edited SharedPreferences value must never crash the widget.
            renderWaiting(rv, v, s);
        }
    }

    /** No snapshot yet: nothing to show, so no fake forecast is drawn. */
    private static void renderWaiting(RemoteViews rv, Variant v, Store s) {
        rv.setTextViewText(R.id.current_temp, "--");
        rv.setTextViewText(R.id.current_cond, "Waiting");
        String status = s.error().isEmpty() ? "Tap refresh to load the forecast" : s.error();
        setFreshness(rv, v, status);
        if (v != Variant.STRIP) {
            rv.setTextViewText(R.id.title, "DOSkies");
            hideDays(rv);
        }
    }

    /**
     * A snapshot is present, so it renders regardless of Store.error(): a failed refresh must
     * never blank the last good forecast. When an error is set it takes the freshness line's
     * place instead of the normal "Updated ..." text, so the user still sees something is wrong.
     *
     * <p>{@code demo} marks a Weather.demo() forecast (Store.demo() on): STRIP has no title row to
     * carry a marker, so it prefixes current_cond instead; every other variant prefixes the title.
     * The freshness line always reads "Demo data" rather than a checked time or error, since a
     * demo forecast has neither.
     */
    private static void renderForecast(RemoteViews rv, Variant v, Forecast f, Store s, boolean demo) {
        rv.setTextViewText(R.id.current_temp, f.current.temp + "°" + f.unit);
        String cond = Wmo.label(f.current.code);
        rv.setTextViewText(R.id.current_cond, demo && v == Variant.STRIP ? "DEMO " + cond : cond);
        setFreshness(rv, v, demo ? "Demo data, not live" : (s.error().isEmpty() ? checkedText(s.checked()) : s.error()));
        if (v != Variant.STRIP) {
            rv.setTextViewText(R.id.title, demo ? "DEMO · " + s.placeLabel() : s.placeLabel());
            if (v == Variant.LARGE) showDays(rv, f); else hideDays(rv);
        }
    }

    private static void setFreshness(RemoteViews rv, Variant v, String text) {
        if (v == Variant.STRIP) return; // no room at the strip key; current_cond already carries the state
        rv.setTextViewText(R.id.freshness, text);
    }

    private static void hideDays(RemoteViews rv) {
        for (int id : DAY_ROW) rv.setViewVisibility(id, View.GONE);
    }

    private static void showDays(RemoteViews rv, Forecast f) {
        int n = Math.min(DAYS, f.days.length);
        for (int i = 0; i < DAY_ROW.length; i++) {
            if (i >= n) { rv.setViewVisibility(DAY_ROW[i], View.GONE); continue; }
            rv.setViewVisibility(DAY_ROW[i], View.VISIBLE);
            Forecast.Day d = f.days[i];
            rv.setTextViewText(DAY_DOW[i], dowLabel(d.date, i));
            rv.setTextViewText(DAY_HI[i], d.hi + "°");
            rv.setTextViewText(DAY_LO[i], d.lo + "°");
            rv.setTextViewText(DAY_PRECIP[i], d.precipChancePct + "%");
        }
    }

    /** Index 0 is always today, per the data contract; every other row gets a short weekday name. */
    private static String dowLabel(String isoDate, int index) {
        if (index == 0) return "Today";
        try {
            return LocalDate.parse(isoDate).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US);
        } catch (Exception e) {
            return "?";
        }
    }

    /** Package-visible (not private) so MainActivity's status line can share this exact wording. */
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
