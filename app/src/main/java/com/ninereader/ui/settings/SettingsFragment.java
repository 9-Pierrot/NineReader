package com.ninereader.ui.settings;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ninereader.BuildConfig;
import com.ninereader.NineReaderApp;
import com.ninereader.R;
import com.ninereader.data.backup.BackupData;
import com.ninereader.data.backup.BackupManager;
import com.ninereader.util.AppExecutors;
import com.ninereader.util.Prefs;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Settings (§7.1 screen 9), including JSON backup / restore of user data (BIB-05). */
public class SettingsFragment extends PreferenceFragmentCompat {

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), this::exportTo);
    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importFrom);

    /** Material You does not apply to Crystal and Sepia, which keep their own palette. */
    private void updateDynamicColors(@androidx.annotation.NonNull androidx.preference.SwitchPreferenceCompat pref,
                                     @androidx.annotation.NonNull String theme) {
        boolean supported = com.ninereader.ui.theme.ThemeManager.supportsDynamicColors(theme);
        pref.setEnabled(supported);
        pref.setSummary(supported ? R.string.pref_dynamic_colors_summary : R.string.pref_dynamic_colors_unavailable);
    }

    /** Views do not change theme live: the screen is recreated once the new value is stored. */
    private void recreateAfterChange() {
        View v = getView();
        if (v != null) v.post(() -> {
            if (isAdded()) requireActivity().recreate();
        });
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        Preference theme = findPreference(Prefs.KEY_APP_THEME);
        androidx.preference.SwitchPreferenceCompat dynamic = findPreference(Prefs.KEY_DYNAMIC_COLORS);
        if (dynamic != null) {
            dynamic.setVisible(com.ninereader.ui.theme.ThemeManager.dynamicColorsAvailable());
            updateDynamicColors(dynamic, new Prefs(requireContext()).appTheme());
            dynamic.setOnPreferenceChangeListener((p, value) -> {
                recreateAfterChange();
                return true;
            });
        }
        if (theme != null) {
            theme.setOnPreferenceChangeListener((p, value) -> {
                String old = new Prefs(requireContext()).appTheme();
                String chosen = String.valueOf(value);
                if (dynamic != null) updateDynamicColors(dynamic, chosen);
                if (com.ninereader.ui.theme.ThemeManager.nightMode(old) != com.ninereader.ui.theme.ThemeManager.nightMode(chosen)) {
                    // A new light / dark mode recreates every activity by itself.
                    Prefs.applyAppTheme(chosen);
                } else {
                    recreateAfterChange();
                }
                return true;
            });
        }
        click("signatures", () -> NavHostFragment.findNavController(this).navigate(R.id.action_global_signatures));
        click("backup_export", () -> exportLauncher.launch("ninereader_backup_"
                + new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date()) + ".json"));
        click("backup_import", () -> importLauncher.launch(new String[]{"application/json", "text/plain", "*/*"}));
        click("clear_recents", () -> {
            NineReaderApp.from(requireContext()).documents().clearRecents();
            Toast.makeText(requireContext(), R.string.recents_cleared, Toast.LENGTH_SHORT).show();
        });
        click("privacy", () -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.privacy_title)
                .setMessage(R.string.privacy_text)
                .setPositiveButton(android.R.string.ok, null)
                .show());
        Preference version = findPreference("version");
        if (version != null) version.setSummary(BuildConfig.VERSION_NAME);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View list = super.onCreateView(inflater, container, state);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        MaterialToolbar toolbar = (MaterialToolbar) inflater.inflate(R.layout.view_toolbar, root, false);
        toolbar.setTitle(R.string.settings);
        toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        root.addView(toolbar);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void click(String key, Runnable action) {
        Preference p = findPreference(key);
        if (p != null) {
            p.setOnPreferenceClickListener(pref -> {
                action.run();
                return true;
            });
        }
    }

    private void exportTo(@Nullable Uri uri) {
        if (uri == null) return;
        android.content.Context ctx = requireContext().getApplicationContext();
        AppExecutors.get().diskIo().execute(() -> {
            boolean ok;
            try (OutputStream out = ctx.getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("null stream");
                Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                new BackupManager(NineReaderApp.from(ctx).db()).export(w, System.currentTimeMillis());
                ok = true;
            } catch (IOException | SecurityException e) {
                ok = false;
            }
            boolean success = ok;
            AppExecutors.get().mainThread().execute(() ->
                    Toast.makeText(ctx, success ? R.string.backup_exported : R.string.error_generic, Toast.LENGTH_SHORT).show());
        });
    }

    private void importFrom(@Nullable Uri uri) {
        if (uri == null) return;
        android.content.Context ctx = requireContext().getApplicationContext();
        AppExecutors.get().diskIo().execute(() -> {
            String message;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("null stream");
                BackupManager manager = new BackupManager(NineReaderApp.from(ctx).db());
                BackupData data = manager.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
                int count = manager.importData(data);
                message = ctx.getString(R.string.backup_imported, count);
            } catch (IOException | SecurityException e) {
                message = ctx.getString(R.string.backup_invalid);
            }
            String msg = message;
            AppExecutors.get().mainThread().execute(() -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
        });
    }
}
