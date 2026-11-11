package com.ninereader.ui.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.ninereader.R;
import com.ninereader.util.Prefs;

/**
 * The six app themes: System, Light, Dark, AMOLED, Crystal and Sepia, optionally with Material You
 * colours. Themes are overlays applied on top of the activity theme, before {@code super.onCreate()},
 * in this order: base palette, then Sepia or Crystal, then Material You, then AMOLED, then the glass
 * components of Crystal.
 */
public final class ThemeManager {

    public static final String SYSTEM = "system";
    public static final String LIGHT = "light";
    public static final String DARK = "dark";
    public static final String AMOLED = "amoled";
    public static final String CRYSTAL = "crystal";
    public static final String SEPIA = "sepia";

    private ThemeManager() {
    }

    /** Light / dark mode imposed by a theme: Light and Sepia are always light, Dark and AMOLED always dark. */
    public static int nightMode(@NonNull String theme) {
        switch (theme) {
            case LIGHT:
            case SEPIA:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case DARK:
            case AMOLED:
                return AppCompatDelegate.MODE_NIGHT_YES;
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; // System, Crystal
        }
    }

    /** Applies the light / dark mode of a theme to the whole app. */
    public static void applyNightMode(@NonNull String theme) {
        AppCompatDelegate.setDefaultNightMode(nightMode(theme));
    }

    /** Whether Material You colours can be used with a theme (Crystal and Sepia keep their own palette). */
    public static boolean supportsDynamicColors(@NonNull String theme) {
        return !CRYSTAL.equals(theme) && !SEPIA.equals(theme);
    }

    public static boolean dynamicColorsAvailable() {
        return DynamicColors.isDynamicColorAvailable();
    }

    /** Theme and options in effect: an activity created with another signature must be recreated. */
    @NonNull
    public static String signature(@NonNull Context context) {
        Prefs prefs = new Prefs(context);
        return prefs.appTheme() + (useDynamicColors(prefs) ? "+you" : "");
    }

    private static boolean useDynamicColors(@NonNull Prefs prefs) {
        return prefs.dynamicColors() && dynamicColorsAvailable() && supportsDynamicColors(prefs.appTheme());
    }

    /** Applies the chosen theme to an activity. To be called before {@code super.onCreate()}. */
    public static void apply(@NonNull Activity activity) {
        Prefs prefs = new Prefs(activity);
        String theme = prefs.appTheme();
        Resources.Theme t = activity.getTheme();
        if (SEPIA.equals(theme)) t.applyStyle(R.style.ThemeOverlay_NineReader_Sepia, true);
        if (CRYSTAL.equals(theme)) t.applyStyle(R.style.ThemeOverlay_NineReader_Crystal, true);
        boolean dynamic = useDynamicColors(prefs);
        if (dynamic) t.applyStyle(com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_DayNight, true);
        // After Material You: its grey surfaces and pastel accents are replaced by black and vivid tones.
        if (AMOLED.equals(theme)) {
            t.applyStyle(R.style.ThemeOverlay_NineReader_Amoled, true);
            if (dynamic) t.applyStyle(R.style.ThemeOverlay_NineReader_AmoledDynamic, true);
        }
        if (CRYSTAL.equals(theme)) t.applyStyle(R.style.ThemeOverlay_NineReader_Glass, true);
    }

    /** Whether the theme of an activity is dark (status bar icons must then be light). */
    public static boolean isDark(@NonNull Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
}
