package com.ninereader.ui.theme;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Activity drawn with the chosen app theme. Views do not change theme live: an activity created with
 * another theme than the current one (changed in the settings meanwhile) is recreated when it comes back.
 */
public abstract class ThemedActivity extends AppCompatActivity {

    @Nullable
    private String themeSignature;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.apply(this);
        themeSignature = ThemeManager.signature(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeSignature != null && !themeSignature.equals(ThemeManager.signature(this))) recreate();
    }
}
