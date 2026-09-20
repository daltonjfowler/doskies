package app.doskies;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * hasLocationPermission against a real (Robolectric) PackageManager. The pure decision logic
 * that consumes its result lives in LocatorTest and needs no Robolectric at all.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 35})
public class LocatorPermissionTest {
    @Test public void deniedByDefault() {
        Context c = RuntimeEnvironment.getApplication();
        assertFalse(Locator.hasLocationPermission(c));
    }

    @Test public void grantedAfterTheUserAllowsIt() {
        Application app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        assertTrue(Locator.hasLocationPermission(app));
    }
}
