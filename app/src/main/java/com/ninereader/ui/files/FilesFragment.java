package com.ninereader.ui.files;

import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ninereader.NineReaderApp;
import com.ninereader.R;
import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.data.entity.TrashItemEntity;
import com.ninereader.databinding.FragmentFilesBinding;
import com.ninereader.databinding.FilesListPaneBinding;
import com.ninereader.ui.common.DocumentActions;
import com.ninereader.ui.common.DocumentOpener;
import com.ninereader.ui.common.ThumbnailLoader;
import com.ninereader.util.AppExecutors;
import com.ninereader.util.FileUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Files tab (§3.1, BIB-02..04): library, SAF folders, collections and trash; search by name, sort;
 * list + preview on tablets (§3.2).
 */
public class FilesFragment extends Fragment implements DocumentActions.MoveRequester {

    private FragmentFilesBinding b;
    private FilesListPaneBinding list;
    private FilesViewModel vm;
    private final Adapter adapter = new Adapter();
    @Nullable
    private DocumentEntity pendingMove;
    @Nullable
    private TrashItemEntity pendingRestore;
    @Nullable
    private FileRow previewed;
    private OnBackPressedCallback backCallback;

    private final ActivityResultLauncher<Uri> addFolder = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), this::onFolderPicked);
    private final ActivityResultLauncher<Uri> moveTarget = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null && pendingMove != null) DocumentActions.move(this, pendingMove, uri);
                pendingMove = null;
            });
    private final ActivityResultLauncher<String[]> openDocument = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                DocumentOpener.persist(requireContext(), uri);
                DocumentOpener.open(requireContext(), uri);
            });
    private final ActivityResultLauncher<String> restoreTarget = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"), this::onRestoreTarget);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentFilesBinding.inflate(inflater, container, false);
        list = b.listPane;
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(FilesViewModel.class);
        list.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.list.setAdapter(adapter);
        setupToolbar();
        list.sections.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            vm.setSection(id == R.id.section_folders ? FilesViewModel.Section.FOLDERS
                    : id == R.id.section_collections ? FilesViewModel.Section.COLLECTIONS
                    : id == R.id.section_trash ? FilesViewModel.Section.TRASH : FilesViewModel.Section.LIBRARY);
        });
        list.pathUp.setOnClickListener(v -> vm.goUp());
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                vm.goUp();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);
        vm.rows().observe(getViewLifecycleOwner(), rows -> {
            adapter.submit(rows);
            updateEmpty(rows);
        });
        vm.loading().observe(getViewLifecycleOwner(), l -> list.loading.setVisibility(Boolean.TRUE.equals(l) ? View.VISIBLE : View.GONE));
        vm.pathVersion().observe(getViewLifecycleOwner(), v -> updatePath());
        vm.section().observe(getViewLifecycleOwner(), s -> updatePath());
        if (b.previewOpen != null) {
            b.previewOpen.setOnClickListener(v -> {
                if (previewed != null) open(previewed);
            });
            b.previewMore.setOnClickListener(v -> {
                if (previewed != null) more(previewed, v);
            });
        }
    }

    private boolean isTwoPane() {
        return b.previewPane != null;
    }

    private void setupToolbar() {
        MenuItem search = list.toolbar.getMenu().findItem(R.id.action_search);
        SearchView sv = (SearchView) search.getActionView();
        if (sv != null) {
            sv.setQueryHint(getString(R.string.search_by_name));
            sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String q) {
                    vm.setQuery(q);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String q) {
                    vm.setQuery(q);
                    return true;
                }
            });
        }
        list.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_sort) {
                showSort();
            } else if (id == R.id.action_open) {
                openDocument.launch(new String[]{"application/pdf"});
            } else if (id == R.id.action_add_folder) {
                addFolder.launch(null);
            } else {
                return false;
            }
            return true;
        });
    }

    private void showSort() {
        String[] keys = {FileSorter.SORT_NAME, FileSorter.SORT_DATE, FileSorter.SORT_SIZE};
        String[] labels = {getString(R.string.sort_name), getString(R.string.sort_date), getString(R.string.sort_size)};
        int checked = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(vm.sortMode())) checked = i;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sort)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    vm.setSort(keys[which]);
                    d.dismiss();
                })
                .show();
    }

    private void updatePath() {
        String path = vm.path();
        boolean inside = vm.canGoUp();
        list.pathBar.setVisibility(inside ? View.VISIBLE : View.GONE);
        list.pathLabel.setText(path);
        backCallback.setEnabled(inside);
        list.pathAction.setVisibility(View.GONE);
        FilesViewModel.Section s = vm.section().getValue();
        if (s == FilesViewModel.Section.COLLECTIONS && !inside) {
            list.pathBar.setVisibility(View.VISIBLE);
            list.pathUp.setVisibility(View.GONE);
            list.pathLabel.setText(R.string.section_collections);
            list.pathAction.setVisibility(View.VISIBLE);
            list.pathAction.setText(R.string.collection_new);
            list.pathAction.setOnClickListener(v -> DocumentActions.createCollection(this,
                    name -> NineReaderApp.from(requireContext()).collections().createAsync(name, 0)));
        } else if (s == FilesViewModel.Section.TRASH) {
            list.pathBar.setVisibility(View.VISIBLE);
            list.pathUp.setVisibility(View.GONE);
            list.pathLabel.setText(R.string.trash_retention);
        } else {
            list.pathUp.setVisibility(View.VISIBLE);
        }
    }

    private void updateEmpty(List<FileRow> rows) {
        boolean empty = rows == null || rows.isEmpty();
        list.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) return;
        FilesViewModel.Section s = vm.section().getValue();
        list.emptyAction.setVisibility(View.GONE);
        if (s == FilesViewModel.Section.FOLDERS && !vm.canGoUp()) {
            list.emptyText.setText(R.string.folders_empty);
            list.emptyAction.setVisibility(View.VISIBLE);
            list.emptyAction.setText(R.string.add_folder);
            list.emptyAction.setOnClickListener(v -> addFolder.launch(null));
        } else if (s == FilesViewModel.Section.COLLECTIONS) {
            list.emptyText.setText(vm.canGoUp() ? R.string.collection_empty : R.string.collections_empty);
        } else if (s == FilesViewModel.Section.TRASH) {
            list.emptyText.setText(R.string.trash_empty);
        } else if (s == FilesViewModel.Section.FOLDERS) {
            list.emptyText.setText(R.string.folder_no_pdf);
        } else {
            list.emptyText.setText(R.string.library_empty);
            list.emptyAction.setVisibility(View.VISIBLE);
            list.emptyAction.setText(R.string.open_file);
            list.emptyAction.setOnClickListener(v -> openDocument.launch(new String[]{"application/pdf"}));
        }
    }

    private void onFolderPicked(@Nullable Uri uri) {
        if (uri == null) return;
        DocumentOpener.persist(requireContext(), uri);
        DocumentFile df = DocumentFile.fromTreeUri(requireContext(), uri);
        String name = df != null && df.getName() != null ? df.getName() : uri.getLastPathSegment();
        NineReaderApp.from(requireContext()).folders().add(uri.toString(), name == null ? "?" : name);
        list.sectionFolders.setChecked(true);
    }

    // ------------------------------------------------------------------ row actions

    private void onClick(@NonNull FileRow row) {
        switch (row.kind) {
            case FOLDER_ROOT:
            case FOLDER:
                vm.enterFolder(row);
                break;
            case COLLECTION:
                vm.setCollection(row.collection);
                break;
            case TRASH:
                trashDialog(row);
                break;
            default:
                if (isTwoPane()) {
                    preview(row);
                } else {
                    open(row);
                }
                break;
        }
    }

    private void open(@NonNull FileRow row) {
        if (row.kind == FileRow.Kind.PDF) {
            withEntity(row, doc -> DocumentOpener.open(requireContext(), Uri.parse(doc.uri)));
        } else {
            DocumentOpener.open(requireContext(), Uri.parse(row.uri));
        }
    }

    private void preview(@NonNull FileRow row) {
        previewed = row;
        if (b.previewContent == null || b.previewEmpty == null || b.previewThumb == null) return;
        b.previewEmpty.setVisibility(View.GONE);
        b.previewContent.setVisibility(View.VISIBLE);
        b.previewTitle.setText(row.title);
        b.previewInfo.setText(subtitle(row));
        ThumbnailLoader.load(b.previewThumb, row.uri, 640);
    }

    /** Ensures a library entry exists for a PDF found in a folder. */
    private void withEntity(@NonNull FileRow row, @NonNull java.util.function.Consumer<DocumentEntity> action) {
        if (row.doc != null) {
            action.accept(row.doc);
            return;
        }
        NineReaderApp app = NineReaderApp.from(requireContext());
        AppExecutors.get().diskIo().execute(() -> {
            DocumentEntity doc = app.documents().ensure(row.uri, row.title, row.size, row.parentUri);
            AppExecutors.get().mainThread().execute(() -> {
                if (isAdded()) action.accept(doc);
            });
        });
    }

    private void more(@NonNull FileRow row, @NonNull View anchor) {
        switch (row.kind) {
            case FOLDER_ROOT: {
                PopupMenu menu = new PopupMenu(requireContext(), anchor);
                menu.getMenu().add(R.string.folder_remove);
                menu.setOnMenuItemClickListener(i -> {
                    NineReaderApp.from(requireContext()).folders().remove(row.uri);
                    try {
                        requireContext().getContentResolver().releasePersistableUriPermission(Uri.parse(row.uri),
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } catch (SecurityException ignored) {
                        // Already released.
                    }
                    return true;
                });
                menu.show();
                break;
            }
            case COLLECTION:
                collectionMenu(row, anchor);
                break;
            case TRASH:
                trashDialog(row);
                break;
            case FOLDER:
                break;
            default:
                withEntity(row, doc -> DocumentActions.showMenu(this, doc, anchor, this));
                break;
        }
    }

    private void collectionMenu(@NonNull FileRow row, @NonNull View anchor) {
        if (row.collection == null) return;
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.rename);
        menu.getMenu().add(0, 2, 1, R.string.delete);
        menu.setOnMenuItemClickListener(i -> {
            if (i.getItemId() == 1) {
                DocumentActions.createCollection(this, name ->
                        NineReaderApp.from(requireContext()).collections().rename(row.collection.id, name));
            } else {
                new MaterialAlertDialogBuilder(requireContext())
                        .setMessage(getString(R.string.collection_delete_confirm, row.title))
                        .setPositiveButton(R.string.delete, (d, w) ->
                                NineReaderApp.from(requireContext()).collections().delete(row.collection.id))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
            return true;
        });
        menu.show();
    }

    private void trashDialog(@NonNull FileRow row) {
        TrashItemEntity item = row.trash;
        if (item == null) return;
        NineReaderApp app = NineReaderApp.from(requireContext());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.name)
                .setMessage(getString(R.string.trash_days_left, item.daysLeft(System.currentTimeMillis())))
                .setPositiveButton(R.string.restore, (d, w) -> AppExecutors.get().diskIo().execute(() -> {
                    Uri restored = null;
                    boolean failed = false;
                    try {
                        restored = app.trash().restore(item);
                    } catch (IOException e) {
                        failed = true;
                    }
                    boolean needTarget = restored == null && !failed;
                    boolean error = failed;
                    AppExecutors.get().mainThread().execute(() -> {
                        if (!isAdded()) return;
                        if (needTarget) {
                            pendingRestore = item;
                            restoreTarget.launch(item.name);
                        } else {
                            Toast.makeText(requireContext(), error ? R.string.error_generic : R.string.restored, Toast.LENGTH_SHORT).show();
                        }
                    });
                }))
                .setNeutralButton(R.string.delete_forever, (d, w) ->
                        AppExecutors.get().diskIo().execute(() -> app.trash().deleteForever(item)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onRestoreTarget(@Nullable Uri uri) {
        TrashItemEntity item = pendingRestore;
        pendingRestore = null;
        if (uri == null || item == null) return;
        NineReaderApp app = NineReaderApp.from(requireContext());
        AppExecutors.get().diskIo().execute(() -> {
            boolean ok;
            try {
                app.trash().restoreTo(item, uri);
                ok = true;
            } catch (IOException e) {
                ok = false;
            }
            boolean success = ok;
            AppExecutors.get().mainThread().execute(() -> {
                if (isAdded()) Toast.makeText(requireContext(), success ? R.string.restored : R.string.error_generic, Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    public void requestMoveDestination(@NonNull DocumentEntity doc) {
        pendingMove = doc;
        moveTarget.launch(null);
    }

    @NonNull
    String subtitle(@NonNull FileRow r) {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        List<String> parts = new ArrayList<>();
        if (r.kind == FileRow.Kind.TRASH && r.trash != null) {
            parts.add(getString(R.string.trash_days_left, r.trash.daysLeft(System.currentTimeMillis())));
        }
        if (r.size > 0) parts.add(FileUtils.formatSize(r.size, locale));
        if (r.doc != null && r.doc.pageCount > 0) parts.add(getString(R.string.pages_count, r.doc.pageCount));
        if (r.date > 0 && r.kind != FileRow.Kind.TRASH) parts.add(DateUtils.getRelativeTimeSpanString(r.date).toString());
        return String.join(" · ", parts);
    }

    // ------------------------------------------------------------------ adapter

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        private List<FileRow> rows = new ArrayList<>();

        void submit(List<FileRow> newRows) {
            rows = newRows == null ? new ArrayList<>() : newRows;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_document_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            FileRow r = rows.get(position);
            h.title.setText(r.title);
            h.subtitle.setText(subtitle(r));
            h.subtitle.setVisibility(h.subtitle.getText().length() == 0 ? View.GONE : View.VISIBLE);
            switch (r.kind) {
                case FOLDER_ROOT:
                case FOLDER:
                    h.thumb.setTag(R.id.tag_thumbnail_key, null);
                    h.thumb.setImageResource(R.drawable.ic_folder);
                    break;
                case COLLECTION:
                    h.thumb.setTag(R.id.tag_thumbnail_key, null);
                    h.thumb.setImageResource(R.drawable.ic_label);
                    break;
                case TRASH:
                    h.thumb.setTag(R.id.tag_thumbnail_key, null);
                    h.thumb.setImageResource(R.drawable.ic_delete);
                    break;
                default:
                    ThumbnailLoader.load(h.thumb, r.uri, 120);
                    break;
            }
            boolean isDoc = r.kind == FileRow.Kind.DOCUMENT;
            h.favorite.setVisibility(isDoc ? View.VISIBLE : View.GONE);
            if (isDoc && r.doc != null) {
                DocumentEntity d = r.doc;
                h.favorite.setImageResource(d.favorite ? R.drawable.ic_star : R.drawable.ic_star_border);
                h.favorite.setContentDescription(getString(d.favorite ? R.string.favorite_remove : R.string.favorite_add));
                h.favorite.setOnClickListener(v -> NineReaderApp.from(requireContext()).documents().setFavorite(d.id, !d.favorite));
            }
            h.more.setVisibility(r.kind == FileRow.Kind.FOLDER ? View.GONE : View.VISIBLE);
            h.more.setOnClickListener(v -> more(r, v));
            h.itemView.setOnClickListener(v -> onClick(r));
            h.itemView.setOnLongClickListener(v -> {
                more(r, v);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title;
        final TextView subtitle;
        final ImageButton favorite;
        final ImageButton more;

        Holder(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            favorite = v.findViewById(R.id.favorite);
            more = v.findViewById(R.id.more);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
        list = null;
    }
}
