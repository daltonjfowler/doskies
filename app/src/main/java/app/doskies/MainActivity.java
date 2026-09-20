package app.doskies;

import android.Manifest;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Minimal placeholder host activity. Settings UI (units, location mode, city search) lands in T4.
 *
 * <p>On launch, in auto mode without location permission yet, requests ACCESS_COARSE_LOCATION
 * (the framework API only; no androidx dependency added for this). Either outcome kicks a
 * refresh: granted lets it use a fresh fix, denied falls back to the stored place. Never blocks
 * on the user's choice.
 */
public final class MainActivity extends Activity {
    private static final int LOCATION_PERMISSION_REQUEST = 1;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView label = new TextView(this);
        label.setText(getString(R.string.app_name));
        label.setTextColor(Color.rgb(239, 245, 238));
        label.setTextSize(24);
        label.setGravity(Gravity.CENTER);
        label.setBackgroundColor(Color.rgb(17, 25, 22));
        setContentView(label);

        Store store = new Store(this);
        if ("auto".equals(store.mode()) && !Locator.hasLocationPermission(this)) {
            requestPermissions(new String[] { Manifest.permission.ACCESS_COARSE_LOCATION }, LOCATION_PERMISSION_REQUEST);
        } else {
            RefreshJob.now(this);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            // Granted or denied, kick a refresh either way; denial just means Repository falls
            // back to the stored coordinates. Never block on the user's choice.
            RefreshJob.now(this);
        }
    }
}
