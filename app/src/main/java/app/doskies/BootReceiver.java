package app.doskies;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reschedules the refresh job and repaints any placed widgets after a reboot or an app update. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            RefreshJob.schedule(c);
            RefreshJob.scheduleUpdateCheck(c);
            Notifications.createChannels(c);
            ForecastWidget.updateAll(c);
        }
    }
}
