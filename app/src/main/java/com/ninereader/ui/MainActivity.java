package com.ninereader.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.navigation.NavigationBarView;
import com.ninereader.R;
import com.ninereader.databinding.ActivityMainBinding;
import com.ninereader.ui.components.BottomInsets;
import com.ninereader.ui.components.FloatingNavBar;
import com.ninereader.ui.theme.ThemeManager;
import com.ninereader.ui.theme.ThemedActivity;
import com.ninereader.ui.tools.ToolRunFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Single main activity (§6.1): floating, blurred bottom bar on phones, navigation rail on tablets (§3).
 * The reader is a separate activity so that it can be opened from other apps ("Open with").
 */
public class MainActivity extends ThemedActivity {

    /** Opens a tool directly (e.g. "organize" from the reader). The document is passed as data. */
    public static final String EXTRA_TOOL = "tool";

    private ActivityMainBinding binding;
    private NavController nav;
    @Nullable
    private NavHostFragment host;
    /** Tabs of the floating bar (phone layout). */
    private final List<View> tabs = new ArrayList<>();
    private MainTabsViewModel tabsVm;
    private boolean topLevel = true;
    private boolean imeVisible;
    private int lastInsetsBottom;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (host == null) return;
        nav = host.getNavController();
        tabsVm = new ViewModelProvider(this).get(MainTabsViewModel.class);
        if (binding.floatingNav != null) {
            setupFloatingBar(binding.floatingNav);
        } else if (binding.navRail != null) {
            NavigationBarView rail = binding.navRail;
            Menu menu = rail.getMenu();
            rail.setOnItemSelectedListener(item -> {
                for (int i = 0; i < menu.size(); i++) if (menu.getItem(i) == item) selectTab(i);
                return true;
            });
            rail.setOnItemReselectedListener(item -> selectTab(tabsVm.current()));
            tabsVm.tab().observe(this, t -> {
                if (t != null && t < menu.size() && rail.getSelectedItemId() != menu.getItem(t).getItemId()) {
                    rail.setSelectedItemId(menu.getItem(t).getItemId());
                }
            });
        }
        nav.addOnDestinationChangedListener((controller, dest, args) -> onDestinationChanged(dest));
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            applyInsets(insets);
            return insets;
        });
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightStatusBars(!ThemeManager.isDark(this));
        if (savedInstanceState == null) handleIntent(getIntent());
    }

    // ------------------------------------------------------------------ floating bottom bar

    private void setupFloatingBar(@NonNull FloatingNavBar bar) {
        bar.setBlurTarget(binding.blurTarget);
        PopupMenu holder = new PopupMenu(this, bar);
        holder.inflate(R.menu.main_navigation);
        Menu menu = holder.getMenu();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            View tab = inflater.inflate(R.layout.view_nav_tab, bar, false);
            // Same id as the destination, like the items of a BottomNavigationView.
            tab.setId(item.getItemId());
            ((ImageView) tab.findViewById(R.id.nav_tab_icon)).setImageDrawable(item.getIcon());
            ((TextView) tab.findViewById(R.id.nav_tab_label)).setText(item.getTitle());
            tab.setContentDescription(item.getTitle());
            tab.setClipToOutline(true); // the ripple follows the rounded pill
            int index = i;
            tab.setOnClickListener(v -> selectTab(index));
            bar.addView(tab);
            tabs.add(tab);
        }
        if (binding.barContainer != null) {
            binding.barContainer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (b - t != ob - ot) v.post(this::applyContentInsets);
            });
        }
        // Screens created later get the inset as soon as their view exists.
        if (host != null) {
            host.getChildFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull View v, @Nullable Bundle state) {
                    BottomInsets.apply(v, contentInset());
                }
            }, true); // recursive: the screens of the tabs are children of MainTabsFragment
        }
        tabsVm.tab().observe(this, t -> {
            for (int i = 0; i < tabs.size(); i++) tabs.get(i).setSelected(t != null && t == i);
        });
        // Sliding pill: follows the pages while swiping, slides when a far tab is chosen.
        int inactive = MaterialColors.getColor(bar, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int active = MaterialColors.getColor(bar, R.attr.navBarSelected);
        bar.setIndicatorListener(position -> tintTabs(position, inactive, active));
        bar.setIndicatorPosition(tabsVm.current(), false);
        tabsVm.scroll().observe(this, position -> {
            if (position == null) return;
            float shown = bar.indicatorPosition();
            boolean jump = !Float.isNaN(shown) && Math.abs(position - shown) > 1.01f;
            bar.setIndicatorPosition(position, jump);
        });
    }

    /** Colour of each tab after its distance to the pill: the selected colour fades in as the pill comes. */
    private void tintTabs(float position, int inactive, int active) {
        for (int i = 0; i < tabs.size(); i++) {
            float w = Math.max(0f, 1f - Math.abs(position - i));
            int color = androidx.core.graphics.ColorUtils.blendARGB(inactive, active, w);
            View tab = tabs.get(i);
            ImageView icon = tab.findViewById(R.id.nav_tab_icon);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(color));
            float scale = 1f + 0.1f * w;
            icon.setScaleX(scale);
            icon.setScaleY(scale);
            ((TextView) tab.findViewById(R.id.nav_tab_label)).setTextColor(color);
        }
    }

    /** Shows a main tab: back to the swipeable tabs if another screen is open, then the page. */
    private void selectTab(int index) {
        NavDestination current = nav.getCurrentDestination();
        if (current == null || current.getId() != R.id.mainTabsFragment) nav.popBackStack(R.id.mainTabsFragment, false);
        tabsVm.select(index);
    }

    /** Height the scrolling content must leave free under the floating bar. */
    private int contentInset() {
        if (binding.barContainer == null || binding.barContainer.getVisibility() != View.VISIBLE) return 0;
        int h = binding.barContainer.getHeight();
        return h > 0 ? h : 0;
    }

    private void applyContentInsets() {
        if (host == null) return;
        int inset = contentInset();
        for (Fragment f : host.getChildFragmentManager().getFragments()) {
            if (f.getView() != null) BottomInsets.apply(f.getView(), inset);
        }
    }

    // ------------------------------------------------------------------ insets and destinations

    private void applyInsets(@NonNull WindowInsetsCompat insets) {
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
        imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
        lastInsetsBottom = bars.bottom;
        if (binding.barContainer != null) {
            int side = Math.round(20 * getResources().getDisplayMetrics().density);
            int vertical = Math.round(12 * getResources().getDisplayMetrics().density);
            binding.barContainer.setPadding(side + bars.left, vertical, side + bars.right, vertical + bars.bottom);
            boolean show = topLevel && !imeVisible;
            binding.barContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            // The content goes under the bar (and is blurred there); without the bar it stops above the system bars.
            binding.navHost.setPadding(bars.left, bars.top, bars.right, show ? 0 : Math.max(bars.bottom, ime.bottom));
            binding.navHost.post(this::applyContentInsets);
        } else {
            binding.navHost.setPadding(0, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            if (binding.navRail != null) binding.navRail.setPadding(bars.left, bars.top, 0, bars.bottom);
        }
    }

    private void onDestinationChanged(@NonNull NavDestination dest) {
        int id = dest.getId();
        topLevel = id == R.id.mainTabsFragment;
        // The rail stays visible on tablets: plenty of horizontal space.
        if (binding.barContainer != null) ViewCompat.requestApplyInsets(binding.getRoot());
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

    @Override
    public boolean onSupportNavigateUp() {
        return nav != null && nav.navigateUp() || super.onSupportNavigateUp();
    }
}
