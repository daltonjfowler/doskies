package app.doskies;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;

/**
 * Periodic and on-demand widget refresh, mirroring the Cloudflare Usage Widget's RefreshJob.
 * Runs {@link Repository#refresh(Context)} off the main thread on Repository's own single-thread
 * executor, repaints every placed widget, then calls jobFinished.
 */
public final class RefreshJob extends JobService {
    private static final int PERIODIC_JOB_ID = 401;
    private static final int NOW_JOB_ID = 402;
    static final int UPDATE_CHECK = 403;
    private static final long PERIOD_MS = 3 * 60 * 60 * 1000L;  // ~3h
    private static final long FLEX_MS = 30 * 60 * 1000L;        // 30m flex
    private static final long BACKOFF_MS = 30 * 60 * 1000L;
    private static final long UPDATE_CHECK_PERIOD_MS = 24 * 60 * 60 * 1000L;  // daily
    private static final long UPDATE_CHECK_FLEX_MS = 4 * 60 * 60 * 1000L;    // 4h flex

    private final ConcurrentHashMap<JobParameters, FutureTask<Void>> tasks = new ConcurrentHashMap<>();

    /** Schedules the periodic refresh, but only when at least one widget is actually placed. */
    static void schedule(Context c) {
        if (widgetCount(c) == 0) return;
        JobScheduler scheduler = c.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        scheduler.schedule(new JobInfo.Builder(PERIODIC_JOB_ID, new ComponentName(c, RefreshJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(PERIOD_MS, FLEX_MS)
            .setBackoffCriteria(BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build());
    }

    /** Kicks one immediate refresh. Runs regardless of widget count: a freshly placed widget needs data now. */
    static void now(Context c) {
        JobScheduler scheduler = c.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        scheduler.schedule(new JobInfo.Builder(NOW_JOB_ID, new ComponentName(c, RefreshJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(0)
            .setBackoffCriteria(BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build());
    }

    static void cancel(Context c) {
        JobScheduler scheduler = c.getSystemService(JobScheduler.class);
        if (scheduler != null) scheduler.cancelAll();
    }

    /** The daily update check (Phase 2). A persisted periodic job on any network, every 24 hours
     *  with a 4-hour flex. It only checks latest.json and, for a genuinely newer version, posts
     *  one notification; nothing downloads or installs automatically. Idempotent. */
    static void scheduleUpdateCheck(Context c) {
        JobScheduler scheduler = c.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        scheduler.schedule(new JobInfo.Builder(UPDATE_CHECK, new ComponentName(c, RefreshJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(UPDATE_CHECK_PERIOD_MS, UPDATE_CHECK_FLEX_MS)
            .build());
    }

    private static int widgetCount(Context c) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, ForecastWidget.class)).length;
    }

    @Override public boolean onStartJob(JobParameters params) {
        if (params.getJobId() == UPDATE_CHECK) {
            FutureTask<Void> task = new FutureTask<>(() -> {
                Updater.checkAndNotify(getApplicationContext(), BuildConfig.VERSION_CODE);
                if (tasks.remove(params) != null) jobFinished(params, false);
                return null;
            });
            tasks.put(params, task);
            Repository.IO.execute(task);
            return true;
        }
        FutureTask<Void> task = new FutureTask<>(() -> {
            boolean ok = Repository.refresh(getApplicationContext());
            ForecastWidget.updateAll(getApplicationContext());
            if (tasks.remove(params) != null) jobFinished(params, !ok);
            return null;
        });
        tasks.put(params, task);
        Repository.IO.execute(task);
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        FutureTask<Void> task = tasks.remove(params);
        if (task != null) task.cancel(true);
        return true;
    }
}
