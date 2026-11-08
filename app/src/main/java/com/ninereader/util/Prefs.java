package com.ninereader.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

/** Typed access to user preferences (backed by the default SharedPreferences used by the settings screen). */
public final class Prefs {

    public static final String KEY_APP_THEME = "app_theme";
    public static final String KEY_READING_MODE = "reading_mode";
    public static final String KEY_READER_THEME = "reader_theme";
    public static final String KEY_TAP_ZONES = "tap_zones";
    public static final String KEY_VOLUME_KEYS = "volume_keys";
    public static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    public static final String KEY_SMART_CROP = "smart_crop";
    public static final String KEY_DOUBLE_PAGE_LANDSCAPE_TABLET = "double_page_tablet_landscape";
    public static final String KEY_HIGHLIGHT_COLOR = "highlight_color";
    public static final String KEY_INK_WIDTH = "ink_width";
    public static final String KEY_INITIALS = "initials";
    public static final String KEY_TTS_RATE = "tts_rate";
    public static final String KEY_RSVP_WPM = "rsvp_wpm";
    public static final String KEY_REFLOW_FONT = "reflow_font_size";
    public static final String KEY_REFLOW_SPACING = "reflow_line_spacing";
    public static final String KEY_AUTOSCROLL_SPEED = "autoscroll_speed";
    public static final String KEY_BLUE_FILTER = "blue_filter";
    public static final String KEY_BRIGHTNESS = "reader_brightness";
    public static final String KEY_FILES_SORT = "files_sort";
    public static final String KEY_STYLUS_ONLY = "stylus_only_drawing";

    private final SharedPreferences sp;

    public Prefs(@NonNull Context context) {
        sp = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    @NonNull
    public SharedPreferences raw() {
        return sp;
    }

    public String appTheme() {
        return sp.getString(KEY_APP_THEME, "system");
    }

    public String readingMode() {
        return sp.getString(KEY_READING_MODE, "continuous");
    }

    public String readerTheme() {
        return sp.getString(KEY_READER_THEME, "auto");
    }

    public void setReaderTheme(String value) {
        sp.edit().putString(KEY_READER_THEME, value).apply();
    }

    public boolean tapZones() {
        return sp.getBoolean(KEY_TAP_ZONES, true);
    }

    public boolean volumeKeys() {
        return sp.getBoolean(KEY_VOLUME_KEYS, false);
    }

    public boolean keepScreenOn() {
        return sp.getBoolean(KEY_KEEP_SCREEN_ON, true);
    }

    public boolean smartCrop() {
        return sp.getBoolean(KEY_SMART_CROP, false);
    }

    public void setSmartCrop(boolean v) {
        sp.edit().putBoolean(KEY_SMART_CROP, v).apply();
    }

    public boolean doublePageOnTabletLandscape() {
        return sp.getBoolean(KEY_DOUBLE_PAGE_LANDSCAPE_TABLET, true);
    }

    public int highlightColor() {
        return sp.getInt(KEY_HIGHLIGHT_COLOR, 0xFFFFEB3B);
    }

    public void setHighlightColor(int color) {
        sp.edit().putInt(KEY_HIGHLIGHT_COLOR, color).apply();
    }

    public float inkWidth() {
        return sp.getFloat(KEY_INK_WIDTH, 2.5f);
    }

    public void setInkWidth(float w) {
        sp.edit().putFloat(KEY_INK_WIDTH, w).apply();
    }

    public String initials() {
        return sp.getString(KEY_INITIALS, "");
    }

    public float ttsRate() {
        return sp.getInt(KEY_TTS_RATE, 100) / 100f;
    }

    public void setTtsRate(float rate) {
        sp.edit().putInt(KEY_TTS_RATE, Math.round(rate * 100)).apply();
    }

    public int rsvpWpm() {
        return sp.getInt(KEY_RSVP_WPM, 300);
    }

    public void setRsvpWpm(int wpm) {
        sp.edit().putInt(KEY_RSVP_WPM, wpm).apply();
    }

    public int reflowFontSize() {
        return sp.getInt(KEY_REFLOW_FONT, 18);
    }

    public void setReflowFontSize(int sp) {
        this.sp.edit().putInt(KEY_REFLOW_FONT, sp).apply();
    }

    public float reflowLineSpacing() {
        return sp.getFloat(KEY_REFLOW_SPACING, 1.4f);
    }

    public void setReflowLineSpacing(float v) {
        sp.edit().putFloat(KEY_REFLOW_SPACING, v).apply();
    }

    public int autoScrollSpeed() {
        return sp.getInt(KEY_AUTOSCROLL_SPEED, 40);
    }

    public void setAutoScrollSpeed(int v) {
        sp.edit().putInt(KEY_AUTOSCROLL_SPEED, v).apply();
    }

    public int blueFilter() {
        return sp.getInt(KEY_BLUE_FILTER, 0);
    }

    public void setBlueFilter(int v) {
        sp.edit().putInt(KEY_BLUE_FILTER, v).apply();
    }

    /** Reader brightness in [0..100], or -1 to follow the system. */
    public int brightness() {
        return sp.getInt(KEY_BRIGHTNESS, -1);
    }

    public void setBrightness(int v) {
        sp.edit().putInt(KEY_BRIGHTNESS, v).apply();
    }

    public String filesSort() {
        return sp.getString(KEY_FILES_SORT, "name");
    }

    public void setFilesSort(String v) {
        sp.edit().putString(KEY_FILES_SORT, v).apply();
    }

    public boolean stylusOnlyDrawing() {
        return sp.getBoolean(KEY_STYLUS_ONLY, false);
    }

    /** Applies the app-wide light/dark preference. */
    public static void applyAppTheme(@NonNull String value) {
        int mode;
        switch (value) {
            case "light":
                mode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case "dark":
                mode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
