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
    private static final long PERIOD_MS = 3 * 60 * 60 * 1000L;  // ~3h
    private static final long FLEX_MS = 30 * 60 * 1000L;        // 30m flex
    private static final long BACKOFF_MS = 30 * 60 * 1000L;

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

    private static int widgetCount(Context c) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, ForecastWidget.class)).length;
    }

    @Override public boolean onStartJob(JobParameters params) {
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
