package com.ninereader.ui.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.ninereader.R;
import com.ninereader.ui.components.ThemeSwatchView;
import com.ninereader.ui.theme.ThemeManager;

/** App theme chooser: a row of miniatures of the six themes. Stores the theme value as a string. */
public class ThemePreference extends Preference {

    public ThemePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.pref_theme_picker);
    }

    @Nullable
    @Override
    protected Object onGetDefaultValue(@NonNull android.content.res.TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        if (getPersistedString(null) == null && defaultValue != null) persistString(String.valueOf(defaultValue));
    }

    @NonNull
    public String value() {
        return getPersistedString(ThemeManager.SYSTEM);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setClickable(false);
        ViewGroup row = (ViewGroup) holder.findViewById(R.id.theme_row);
        row.removeAllViews();
        String[] values = getContext().getResources().getStringArray(R.array.app_theme_values);
        String[] labels = getContext().getResources().getStringArray(R.array.app_theme_labels);
        String current = value();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < values.length; i++) {
            String v = values[i];
            View item = inflater.inflate(R.layout.item_theme_swatch, row, false);
            ThemeSwatchView swatch = item.findViewById(R.id.theme_swatch);
            swatch.setTheme(v);
            swatch.setSelected(v.equals(current));
            ((TextView) item.findViewById(R.id.theme_label)).setText(labels[i]);
            item.setContentDescription(labels[i]);
            item.setSelected(v.equals(current));
            item.setOnClickListener(x -> choose(v));
            row.addView(item);
        }
    }

    private void choose(@NonNull String v) {
        if (v.equals(value()) || !callChangeListener(v)) return;
        persistString(v);
        notifyChanged();
    }
}
