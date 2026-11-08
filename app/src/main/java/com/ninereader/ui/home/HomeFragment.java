package com.ninereader.ui.home;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.ninereader.R;
import com.ninereader.data.ReadingStats;
import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.databinding.FragmentHomeBinding;
import com.ninereader.ui.common.DocumentActions;
import com.ninereader.ui.common.DocumentAdapters;
import com.ninereader.ui.common.DocumentOpener;
import com.ninereader.ui.common.ThumbnailLoader;

/** Home tab: resume reading, recent files, favourites and quick statistics (§3.1, BIB-01). */
public class HomeFragment extends Fragment implements DocumentAdapters.Callbacks {

    private FragmentHomeBinding b;
    private HomeViewModel vm;
    private final ActivityResultLauncher<String[]> openDocument = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentHomeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(HomeViewModel.class);
        b.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_open) {
                openDocument.launch(new String[]{"application/pdf"});
                return true;
            }
            if (item.getItemId() == R.id.action_settings) {
                NavHostFragment.findNavController(this).navigate(R.id.action_global_settings);
                return true;
            }
            return false;
        });
        b.openFile.setOnClickListener(v -> openDocument.launch(new String[]{"application/pdf"}));

        DocumentAdapters.Cards cards = new DocumentAdapters.Cards(this);
        b.recents.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        b.recents.setAdapter(cards);
        DocumentAdapters.Rows rows = new DocumentAdapters.Rows(this);
        b.favorites.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.favorites.setAdapter(rows);

        vm.recents().observe(getViewLifecycleOwner(), list -> {
            cards.submitList(list);
            boolean empty = list == null || list.isEmpty();
            b.recents.setVisibility(empty ? View.GONE : View.VISIBLE);
            b.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        });
        vm.favorites().observe(getViewLifecycleOwner(), list -> {
            rows.submitList(list);
            b.favoritesEmpty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
        });
        vm.lastOpened().observe(getViewLifecycleOwner(), this::bindResume);
        vm.todayTime().observe(getViewLifecycleOwner(), t -> bindStats());
        vm.todayPages().observe(getViewLifecycleOwner(), t -> bindStats());
        vm.weekTime().observe(getViewLifecycleOwner(), t -> bindStats());
    }

    private void bindResume(@Nullable DocumentEntity doc) {
        if (doc == null) {
            b.resumeCard.setVisibility(View.GONE);
            return;
        }
        b.resumeCard.setVisibility(View.VISIBLE);
        b.resumeTitle.setText(doc.name);
        b.resumeProgress.setProgress(Math.round(doc.progress() * 1000));
        b.resumeSubtitle.setText(getString(R.string.page_of, doc.lastPage + 1, Math.max(1, doc.pageCount)));
        ThumbnailLoader.load(b.resumeThumb, doc.uri, 128);
        b.resumeCard.setContentDescription(getString(R.string.resume_reading) + " : " + doc.name);
        b.resumeCard.setOnClickListener(v -> onOpen(doc));
    }

    private void bindStats() {
        String h = getString(R.string.unit_hour);
        String m = getString(R.string.unit_minute);
        String lt = getString(R.string.less_than_minute);
        Long today = vm.todayTime().getValue();
        Integer pages = vm.todayPages().getValue();
        Long week = vm.weekTime().getValue();
        b.statsToday.setText(getString(R.string.stats_time_pages,
                ReadingStats.formatDuration(today == null ? 0 : today, h, m, lt), pages == null ? 0 : pages));
        b.statsWeek.setText(ReadingStats.formatDuration(week == null ? 0 : week, h, m, lt));
    }

    private void onPicked(@Nullable Uri uri) {
        if (uri == null) return;
        DocumentOpener.persist(requireContext(), uri);
        DocumentOpener.open(requireContext(), uri);
    }

    @Override
    public void onOpen(@NonNull DocumentEntity doc) {
        DocumentOpener.open(requireContext(), Uri.parse(doc.uri));
    }

    @Override
    public void onToggleFavorite(@NonNull DocumentEntity doc) {
        vm.toggleFavorite(doc);
    }

    @Override
    public void onMore(@NonNull DocumentEntity doc, @NonNull View anchor) {
        DocumentActions.showMenu(this, doc, anchor, null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
