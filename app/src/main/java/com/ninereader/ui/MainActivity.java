package com.ninereader.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationBarView;
import com.ninereader.R;
import com.ninereader.databinding.ActivityMainBinding;
import com.ninereader.ui.tools.ToolRunFragment;

/**
 * Single main activity (§6.1): bottom navigation on phones, navigation rail on tablets (§3).
 * The reader is a separate activity so that it can be opened from other apps ("Open with").
 */
public class MainActivity extends AppCompatActivity {

    /** Opens a tool directly (e.g. "organize" from the reader). The document is passed as data. */
    public static final String EXTRA_TOOL = "tool";

    private ActivityMainBinding binding;
    private NavController nav;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (host == null) return;
        nav = host.getNavController();
        NavigationBarView bar = binding.bottomNav != null ? binding.bottomNav : binding.navRail;
        if (bar != null) {
            NavigationUI.setupWithNavController(bar, nav);
            bar.setOnItemReselectedListener(item -> nav.popBackStack(item.getItemId(), false));
        }
        nav.addOnDestinationChangedListener((controller, dest, args) -> updateNavVisibility(dest));
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            if (binding.bottomNav != null) {
                boolean navVisible = binding.bottomNav.getVisibility() == View.VISIBLE;
                binding.navHost.setPadding(bars.left, bars.top, bars.right,
                        Math.max(navVisible ? 0 : bars.bottom, ime.bottom));
            } else {
                binding.navHost.setPadding(0, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                if (binding.navRail != null) binding.navRail.setPadding(bars.left, bars.top, 0, bars.bottom);
            }
            return insets;
        });
        if (savedInstanceState == null) handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(@Nullable Intent intent) {
        if (intent == null || nav == null) return;
        String tool = intent.getStringExtra(EXTRA_TOOL);
        Uri data = intent.getData();
        if (tool == null) return;
        Bundle args = new Bundle();
        args.putString(ToolRunFragment.ARG_TOOL, tool);
        if (data != null) args.putString(ToolRunFragment.ARG_URI, data.toString());
        if ("organize".equals(tool)) {
            nav.navigate(R.id.organizeFragment, args);
        } else {
            nav.navigate(R.id.toolRunFragment, args);
        }
        intent.removeExtra(EXTRA_TOOL);
    }

    private void updateNavVisibility(NavDestination dest) {
        int id = dest.getId();
        boolean top = id == R.id.homeFragment || id == R.id.filesFragment || id == R.id.createFragment || id == R.id.toolsFragment;
        if (binding.bottomNav != null) {
            binding.bottomNav.setVisibility(top ? View.VISIBLE : View.GONE);
            ViewCompat.requestApplyInsets(binding.getRoot());
        }
        // The rail stays visible on tablets: plenty of horizontal space.
    }

    @Override
    public boolean onSupportNavigateUp() {
        return nav != null && nav.navigateUp() || super.onSupportNavigateUp();
    }
}
