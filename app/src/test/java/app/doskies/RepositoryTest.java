package app.doskies;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Repository.refresh's demo short-circuit, offline: demo mode must return without making a
 * network call and without touching Store's snapshot/checked/error. The network-fetch path
 * (Repository.fetch) is not exercised here or anywhere in this suite; it needs a live connection
 * and stays outside the offline gate, matching the rest of this project's tests.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RepositoryTest {
    private Context c;

    @Before public void before() {
        c = RuntimeEnvironment.getApplication();
        new Store(c).prefs.edit().clear().commit();
    }

    @Test public void demoModeSkipsFetchAndLeavesStoreUntouched() {
        Store s = new Store(c);
        s.setDemo(true);
        s.setSnapshot("{\"marker\":\"unchanged\"}");
        long checkedBefore = s.checked();

        assertTrue("demo mode should report success without fetching", Repository.refresh(c));

        assertEquals("{\"marker\":\"unchanged\"}", s.snapshot());
        assertEquals(checkedBefore, s.checked());
        assertEquals("", s.error());
    }

    @Test public void demoModeNeverWritesAnError() {
        Store s = new Store(c);
        s.setDemo(true);
        Repository.refresh(c);
        assertEquals("", s.error());
    }
}
