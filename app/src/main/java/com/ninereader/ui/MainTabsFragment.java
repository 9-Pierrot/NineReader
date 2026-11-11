package com.ninereader.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.ninereader.ui.create.CreateFragment;
import com.ninereader.ui.files.FilesFragment;
import com.ninereader.ui.home.HomeFragment;
import com.ninereader.ui.tools.ToolsFragment;

/**
 * The four main screens (Home, Files, Create, Tools) side by side: a horizontal swipe goes from one to
 * the next, following the finger. The bottom bar (or the rail) selects the same pages. Screens opened
 * from them (settings, a tool...) stay destinations of the navigation graph.
 */
public class MainTabsFragment extends Fragment {

    private MainTabsViewModel tabs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        ViewPager2 p = new ViewPager2(requireContext());
        p.setId(com.ninereader.R.id.main_pager);
        p.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return p;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        tabs = new ViewModelProvider(requireActivity()).get(MainTabsViewModel.class);
        ViewPager2 p = (ViewPager2) view;
        p.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case MainTabsViewModel.FILES:
                        return new FilesFragment();
                    case MainTabsViewModel.CREATE:
                        return new CreateFragment();
                    case MainTabsViewModel.TOOLS:
                        return new ToolsFragment();
                    default:
                        return new HomeFragment();
                }
            }

            @Override
            public int getItemCount() {
                return MainTabsViewModel.COUNT;
            }
        });
        // Neighbouring screens ready: no blank page while swiping.
        p.setOffscreenPageLimit(1);
        p.setCurrentItem(tabs.current(), false);
        p.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tabs.select(position);
            }

            @Override
            public void onPageScrolled(int position, float offset, int offsetPixels) {
                tabs.setScroll(position + offset);
            }
        });
        tabs.tab().observe(getViewLifecycleOwner(), t -> {
            // Neighbouring tab: slide; farther: switch at once instead of scrolling through every page.
            if (t != null && p.getCurrentItem() != t) p.setCurrentItem(t, Math.abs(p.getCurrentItem() - t) == 1);
        });
        // Back from another tab returns to Home (a screen's own back handling, e.g. the parent folder
        // in Files, comes first: registered later by the page).
        OnBackPressedCallback back = new OnBackPressedCallback(tabs.current() != MainTabsViewModel.HOME) {
            @Override
            public void handleOnBackPressed() {
                tabs.select(MainTabsViewModel.HOME);
            }
        };
        tabs.tab().observe(getViewLifecycleOwner(), t -> back.setEnabled(t != null && t != MainTabsViewModel.HOME));
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), back);
    }

}
