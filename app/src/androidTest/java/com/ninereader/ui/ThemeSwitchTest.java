package com.ninereader.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;

import android.content.Context;
import android.os.SystemClock;
import android.util.TypedValue;

import androidx.navigation.Navigation;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ninereader.R;
import com.ninereader.ui.theme.ThemeManager;
import com.ninereader.util.Prefs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

/** A theme chosen in the settings is applied at once, whether the light / dark mode changes or not. */
@RunWith(AndroidJUnit4.class)
public class ThemeSwitchTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private String previous;

    @Before
    public void setUp() {
        Prefs prefs = new Prefs(context);
        previous = prefs.appTheme();
        prefs.raw().edit().putString(Prefs.KEY_APP_THEME, ThemeManager.SYSTEM).commit();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> Prefs.applyAppTheme(ThemeManager.SYSTEM));
    }

    @After
    public void tearDown() {
        new Prefs(context).raw().edit().putString(Prefs.KEY_APP_THEME, previous).commit();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> Prefs.applyAppTheme(previous));
    }

    private static int background(ActivityScenario<MainActivity> scenario) {
        AtomicInteger color = new AtomicInteger();
        scenario.onActivity(a -> {
            TypedValue v = new TypedValue();
            a.getTheme().resolveAttribute(android.R.attr.colorBackground, v, true);
            color.set(v.data);
        });
        return color.get();
    }

    private static void waitForBackground(ActivityScenario<MainActivity> scenario, int expected, String theme) {
        long end = SystemClock.elapsedRealtime() + 5_000;
        int last = 0;
        while (SystemClock.elapsedRealtime() < end) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            last = background(scenario);
            if (last == expected) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError(theme + " not applied: background " + Integer.toHexString(last));
    }

    private static void choose(String label) {
        onView(withContentDescription(label)).perform(scrollTo(), click());
    }

    @Test
    public void chosenThemeIsAppliedImmediately() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> Navigation.findNavController(a, R.id.nav_host).navigate(R.id.settingsFragment));
            String[] labels = context.getResources().getStringArray(R.array.app_theme_labels);
            // AMOLED: dark like a phone in dark mode, so the system does not recreate the screen by itself.
            choose(labels[3]);
            waitForBackground(scenario, 0xFF000000, "AMOLED");
            // Sepia: light, the light / dark mode changes.
            scenario.onActivity(a -> Navigation.findNavController(a, R.id.nav_host).navigate(R.id.settingsFragment));
            choose(labels[5]);
            waitForBackground(scenario, 0xFFF6EEDC, "Sepia");
        }
    }
}
