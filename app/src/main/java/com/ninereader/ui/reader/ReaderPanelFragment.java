package com.ninereader.ui.reader;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.ninereader.R;
import com.ninereader.annotations.Annotation;
import com.ninereader.annotations.AnnotationExporter;
import com.ninereader.data.entity.BookmarkEntity;
import com.ninereader.databinding.FragmentReaderPanelBinding;
import com.ninereader.reader.BitmapCache;
import com.ninereader.reader.OutlineItem;
import com.ninereader.reader.PdfEngine;
import com.ninereader.reader.RenderScheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reader side panel (§7.1 screen 4): outline (LEC-06), bookmarks (LEC-10), annotations with export
 * (ANN-06) and page thumbnails (LEC-08). Overlaid on phones, anchored on tablets in landscape.
 */
public class ReaderPanelFragment extends Fragment {

    public static final String TAG = "reader_panel";
    private static final String STATE_TAB = "tab";

    /** Activity callbacks. */
    public interface Host {
        void goToPage(int page);

        void closePanel();

        void onPendingAnnotationClicked(@NonNull Annotation annotation);

        void exportAnnotations(boolean markdown);
    }

    private FragmentReaderPanelBinding binding;
    private ReaderViewModel vm;
    private int tab;
    private final RowAdapter rowAdapter = new RowAdapter();
    @Nullable
    private ThumbnailAdapter thumbAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentReaderPanelBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(requireActivity()).get(ReaderViewModel.class);
        tab = state != null ? state.getInt(STATE_TAB) : 0;
        TabLayout tabs = binding.tabs;
        tabs.addTab(tabs.newTab().setText(R.string.tab_outline));
        tabs.addTab(tabs.newTab().setText(R.string.tab_bookmarks));
        tabs.addTab(tabs.newTab().setText(R.string.tab_annotations));
        tabs.addTab(tabs.newTab().setText(R.string.tab_thumbnails));
        TabLayout.Tab selected = tabs.getTabAt(tab);
        if (selected != null) selected.select();
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab t) {
                tab = t.getPosition();
                refresh();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab t) {
                // no-op
            }

            @Override
            public void onTabReselected(TabLayout.Tab t) {
                // no-op
            }
        });
        binding.panelClose.setOnClickListener(v -> host().closePanel());
        binding.exportText.setOnClickListener(v -> host().exportAnnotations(false));
        binding.exportMarkdown.setOnClickListener(v -> host().exportAnnotations(true));
        vm.outline().observe(getViewLifecycleOwner(), o -> refreshIf(0));
        vm.bookmarks().observe(getViewLifecycleOwner(), b -> refreshIf(1));
        vm.editor().version().observe(getViewLifecycleOwner(), v -> refreshIf(2));
        vm.editor().existingAnnotations().observe(getViewLifecycleOwner(), v -> refreshIf(2));
        vm.currentPage().observe(getViewLifecycleOwner(), p -> {
            if (thumbAdapter != null) thumbAdapter.setCurrent(p);
        });
        refresh();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TAB, tab);
    }

    private Host host() {
        return (Host) requireActivity();
    }

    private void refreshIf(int which) {
        if (tab == which && binding != null) refresh();
    }

    private void refresh() {
        RecyclerView list = binding.list;
        binding.annotationsActions.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        if (tab == 3) {
            RenderScheduler sch = vm.scheduler();
            if (sch == null) return;
            int cols = getResources().getInteger(R.integer.thumbnail_grid_columns);
            if (!(list.getLayoutManager() instanceof GridLayoutManager)) {
                list.setLayoutManager(new GridLayoutManager(requireContext(), cols));
            }
            if (thumbAdapter == null) thumbAdapter = new ThumbnailAdapter(sch);
            if (list.getAdapter() != thumbAdapter) list.setAdapter(thumbAdapter);
            thumbAdapter.setCurrent(vm.page());
            list.scrollToPosition(vm.page());
            binding.empty.setVisibility(View.GONE);
            return;
        }
        if (!(list.getLayoutManager() instanceof LinearLayoutManager) || list.getLayoutManager() instanceof GridLayoutManager) {
            list.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
        if (list.getAdapter() != rowAdapter) list.setAdapter(rowAdapter);
        List<Row> rows = new ArrayList<>();
        int emptyText;
        switch (tab) {
            case 0: {
                List<OutlineItem> outline = vm.outline().getValue();
                if (outline != null) {
                    for (OutlineItem o : outline) rows.add(Row.outline(o));
                }
                emptyText = R.string.outline_empty;
                break;
            }
            case 1: {
                List<BookmarkEntity> bms = vm.bookmarks().getValue();
                if (bms != null) {
                    for (BookmarkEntity b : bms) rows.add(Row.bookmark(b));
                }
                emptyText = R.string.bookmarks_empty;
                break;
            }
            default: {
                for (Annotation a : vm.editor().store().all()) rows.add(Row.pending(a, labelFor(a)));
                List<AnnotationExporter.Entry> existing = vm.editor().existingAnnotations().getValue();
                if (existing != null) {
                    for (AnnotationExporter.Entry e : existing) rows.add(Row.existing(e));
                }
                Collections.sort(rows, (x, y) -> Integer.compare(x.page, y.page));
                emptyText = R.string.annotations_empty;
                break;
            }
        }
        rowAdapter.submit(rows);
        binding.empty.setText(emptyText);
        binding.empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @NonNull
    String labelFor(@NonNull Annotation a) {
        int res;
        switch (a.type) {
            case HIGHLIGHT:
                res = R.string.tool_highlight;
                break;
            case UNDERLINE:
                res = R.string.tool_underline;
                break;
            case STRIKEOUT:
                res = R.string.tool_strikeout;
                break;
            case NOTE:
                res = R.string.tool_note;
                break;
            case INK:
                res = R.string.tool_ink;
                break;
            case RECT:
                res = R.string.tool_rect;
                break;
            case CIRCLE:
                res = R.string.tool_circle;
                break;
            case LINE:
                res = R.string.tool_line;
                break;
            case ARROW:
                res = R.string.tool_arrow;
                break;
            case FREETEXT:
                res = R.string.tool_text;
                break;
            case IMAGE:
                res = R.string.tool_image;
                break;
            case REDACT:
                res = R.string.tool_redact;
                break;
            case WHITEOUT:
            default:
                res = R.string.tool_whiteout;
                break;
        }
        return getString(res);
    }

    // ------------------------------------------------------------------ rows

    /** A row of the outline / bookmark / annotation lists. */
    static final class Row {
        static final int OUTLINE = 0;
        static final int BOOKMARK = 1;
        static final int PENDING = 2;
        static final int EXISTING = 3;

        int kind;
        int page;
        String title = "";
        String subtitle;
        int level;
        long bookmarkId;
        @Nullable
        Annotation annotation;

        static Row outline(OutlineItem o) {
            Row r = new Row();
            r.kind = OUTLINE;
            r.page = o.page;
            r.title = o.title;
            r.level = o.level;
            return r;
        }

        static Row bookmark(BookmarkEntity b) {
            Row r = new Row();
            r.kind = BOOKMARK;
            r.page = b.page;
            r.title = b.label;
            r.bookmarkId = b.id;
            return r;
        }

        static Row pending(Annotation a, String label) {
            Row r = new Row();
            r.kind = PENDING;
            r.page = a.page;
            r.title = label;
            r.subtitle = a.text;
            r.annotation = a;
            return r;
        }

        static Row existing(AnnotationExporter.Entry e) {
            Row r = new Row();
            r.kind = EXISTING;
            r.page = e.page;
            r.title = e.kind;
            r.subtitle = e.text.isEmpty() ? null : e.text;
            return r;
        }
    }

    private final class RowAdapter extends RecyclerView.Adapter<RowHolder> {
        private List<Row> rows = new ArrayList<>();

        void submit(List<Row> newRows) {
            rows = newRows;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RowHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_panel_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder h, int position) {
            Row r = rows.get(position);
            float density = h.itemView.getResources().getDisplayMetrics().density;
            h.itemView.setPaddingRelative(Math.round((16 + r.level * 16) * density), 0, Math.round(4 * density), 0);
            h.title.setText(r.title.isEmpty() ? getString(R.string.page_n, r.page + 1) : r.title);
            h.subtitle.setVisibility(r.subtitle == null || r.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
            h.subtitle.setText(r.subtitle);
            h.page.setText(String.valueOf(r.page + 1));
            h.icon.setVisibility(r.kind == Row.PENDING ? View.VISIBLE : View.GONE);
            if (r.kind == Row.PENDING && r.annotation != null) {
                h.icon.setImageResource(R.drawable.ic_edit);
                h.icon.setColorFilter(r.annotation.color | 0xFF000000);
            }
            boolean deletable = r.kind == Row.BOOKMARK || r.kind == Row.PENDING;
            h.action.setVisibility(deletable ? View.VISIBLE : View.GONE);
            h.action.setOnClickListener(v -> {
                if (r.kind == Row.BOOKMARK) {
                    com.ninereader.NineReaderApp.from(requireContext()).documents().deleteBookmark(r.bookmarkId);
                } else if (r.annotation != null) {
                    vm.editor().store().remove(r.annotation.id);
                }
            });
            h.itemView.setOnClickListener(v -> {
                if (r.kind == Row.PENDING && r.annotation != null) {
                    host().onPendingAnnotationClicked(r.annotation);
                } else {
                    host().goToPage(r.page);
                }
            });
            h.itemView.setOnLongClickListener(v -> {
                if (r.kind != Row.BOOKMARK) return false;
                renameBookmark(r);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private void renameBookmark(Row r) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setText(r.title);
        input.setSingleLine(true);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(requireContext());
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.bookmark_rename)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        com.ninereader.NineReaderApp.from(requireContext()).documents()
                                .renameBookmark(r.bookmarkId, input.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    static final class RowHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView page;
        final ImageView icon;
        final View action;

        RowHolder(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            page = v.findViewById(R.id.page);
            icon = v.findViewById(R.id.icon);
            action = v.findViewById(R.id.action);
        }
    }

    // ------------------------------------------------------------------ thumbnails

    private final class ThumbnailAdapter extends RecyclerView.Adapter<ThumbHolder> {
        private final RenderScheduler scheduler;
        private final PdfEngine engine;
        private int current = -1;

        ThumbnailAdapter(RenderScheduler scheduler) {
            this.scheduler = scheduler;
            this.engine = scheduler.engine();
        }

        void setCurrent(int page) {
            int old = current;
            current = page;
            if (old >= 0) notifyItemChanged(old);
            notifyItemChanged(page);
        }

        @NonNull
        @Override
        public ThumbHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ThumbHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_thumbnail, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ThumbHolder h, int adapterPosition) {
            final int pageIndex = adapterPosition;
            h.label.setText(String.valueOf(pageIndex + 1));
            h.thumb.setSelected(pageIndex == current);
            h.thumb.setContentDescription(getString(R.string.page_n, pageIndex + 1));
            h.itemView.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) host().goToPage(pos);
            });
            if (h.ticket != null) h.ticket.cancel();
            h.thumb.setImageBitmap(null);
            h.page = pageIndex;
            int w = Math.round(getResources().getDimension(R.dimen.thumbnail_width));
            float pw = engine.isMeasured(pageIndex) ? engine.pageWidth(pageIndex) : 612f;
            float ph = engine.isMeasured(pageIndex) ? engine.pageHeight(pageIndex) : 792f;
            int hgt = Math.max(1, Math.round(w * ph / pw));
            h.thumb.setMinimumHeight(hgt);
            h.ticket = scheduler.request(pageIndex, w, hgt, 0, 0, w, hgt, RenderScheduler.PRIORITY_THUMBNAIL,
                    BitmapCache.key(pageIndex, w, hgt, 9, 0), (Bitmap bmp) -> {
                        if (h.page == pageIndex) h.thumb.setImageBitmap(bmp);
                    });
        }

        @Override
        public void onViewRecycled(@NonNull ThumbHolder h) {
            if (h.ticket != null) h.ticket.cancel();
            h.ticket = null;
        }

        @Override
        public int getItemCount() {
            return engine.pageCount();
        }
    }

    static final class ThumbHolder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView label;
        @Nullable
        RenderScheduler.Ticket ticket;
        int page = -1;

        ThumbHolder(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            label = v.findViewById(R.id.label);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** Unused hook kept for intents from the annotation list. */
    static Intent shareIntent(String text) {
        return new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
    }
}
