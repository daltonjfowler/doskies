package app.doskies;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Minimal placeholder host activity. Settings UI (units, location mode, city search) lands in T4. */
public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView label = new TextView(this);
        label.setText(getString(R.string.app_name));
        label.setTextColor(Color.rgb(239, 245, 238));
        label.setTextSize(24);
        label.setGravity(Gravity.CENTER);
        label.setBackgroundColor(Color.rgb(17, 25, 22));
        setContentView(label);
    }
}
