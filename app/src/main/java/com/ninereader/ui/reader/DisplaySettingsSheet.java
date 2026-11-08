package com.ninereader.ui.reader;

import android.view.LayoutInflater;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.ninereader.R;
import com.ninereader.databinding.SheetDisplayBinding;
import com.ninereader.reader.ReaderTheme;
import com.ninereader.reader.ReadingMode;

/**
 * Reading display settings: theme (LEC-09), page layout (LEC-02/03/14), smart crop (RAP-01),
 * one-hand mode (RAP-02), brightness and blue light filter (RAP-03).
 */
class DisplaySettingsSheet {

    private final ReaderActivity a;

    DisplaySettingsSheet(@NonNull ReaderActivity activity) {
        this.a = activity;
    }

    void show() {
        BottomSheetDialog dialog = new BottomSheetDialog(a);
        SheetDisplayBinding s = SheetDisplayBinding.inflate(LayoutInflater.from(a));
        dialog.setContentView(s.getRoot());

        switch (a.currentTheme()) {
            case DARK:
                s.themeDark.setChecked(true);
                break;
            case SEPIA:
                s.themeSepia.setChecked(true);
                break;
            case INVERTED:
                s.themeInverted.setChecked(true);
                break;
            default:
                s.themeLight.setChecked(true);
                break;
        }
        s.themeGroup.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            ReaderTheme t = id == R.id.theme_dark ? ReaderTheme.DARK
                    : id == R.id.theme_sepia ? ReaderTheme.SEPIA
                    : id == R.id.theme_inverted ? ReaderTheme.INVERTED : ReaderTheme.LIGHT;
            a.setReaderTheme(t);
        });

        switch (a.currentMode()) {
            case PAGED:
                s.modePaged.setChecked(true);
                break;
            case DOUBLE:
                s.modeDouble.setChecked(true);
                break;
            default:
                s.modeContinuous.setChecked(true);
                break;
        }
        s.modeGroup.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            a.setReadingMode(id == R.id.mode_paged ? ReadingMode.PAGED
                    : id == R.id.mode_double ? ReadingMode.DOUBLE : ReadingMode.CONTINUOUS);
        });

        s.switchCrop.setChecked(a.vm.isCropEnabled());
        s.switchCrop.setOnCheckedChangeListener((v, checked) -> a.vm.setCropEnabled(checked));
        s.switchTapZones.setChecked(a.prefs.tapZones());
        s.switchTapZones.setOnCheckedChangeListener((v, checked) ->
                a.prefs.raw().edit().putBoolean(com.ninereader.util.Prefs.KEY_TAP_ZONES, checked).apply());
        s.switchVolumeKeys.setChecked(a.prefs.volumeKeys());
        s.switchVolumeKeys.setOnCheckedChangeListener((v, checked) ->
                a.prefs.raw().edit().putBoolean(com.ninereader.util.Prefs.KEY_VOLUME_KEYS, checked).apply());

        int brightness = a.prefs.brightness();
        s.brightnessSystem.setChecked(brightness < 0);
        s.brightnessSlider.setEnabled(brightness >= 0);
        s.brightnessSlider.setValue(brightness < 0 ? 50 : Math.max(1, Math.min(100, brightness)));
        s.brightnessSystem.setOnCheckedChangeListener((v, checked) -> {
            s.brightnessSlider.setEnabled(!checked);
            a.prefs.setBrightness(checked ? -1 : Math.round(s.brightnessSlider.getValue()));
            applyWindow();
        });
        s.brightnessSlider.addOnChangeListener((sl, value, fromUser) -> {
            if (!fromUser) return;
            a.prefs.setBrightness(Math.round(value));
            applyWindow();
        });
        s.blueSlider.setValue(Math.max(0, Math.min(80, a.prefs.blueFilter())));
        s.blueSlider.addOnChangeListener((sl, value, fromUser) -> {
            if (!fromUser) return;
            a.prefs.setBlueFilter(Math.round(value));
            applyWindow();
        });
        dialog.show();
    }

    private void applyWindow() {
        WindowManager.LayoutParams lp = a.getWindow().getAttributes();
        int brightness = a.prefs.brightness();
        lp.screenBrightness = brightness < 0 ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : Math.max(0.01f, brightness / 100f);
        a.getWindow().setAttributes(lp);
        int blue = a.prefs.blueFilter();
        if (blue > 0) {
            int alpha = Math.round(blue / 100f * 160);
            a.b.filterOverlay.setBackgroundColor((alpha << 24) | 0x00FF8F00);
            a.b.filterOverlay.setVisibility(android.view.View.VISIBLE);
        } else {
            a.b.filterOverlay.setVisibility(android.view.View.GONE);
        }
    }
}
