package com.ninereader.ui.files;

import android.app.Application;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.ninereader.NineReaderApp;
import com.ninereader.data.entity.CollectionEntity;
import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.data.entity.FolderEntity;
import com.ninereader.data.entity.TrashItemEntity;
import com.ninereader.util.AppExecutors;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** State of the file explorer: section, folder navigation, collection, search and sort (BIB-02..04). */
public class FilesViewModel extends AndroidViewModel {

    /** Explorer sections. */
    public enum Section { LIBRARY, FOLDERS, COLLECTIONS, TRASH }

    /** A folder level in SAF navigation. */
    static final class Level {
        final String treeUri;
        final String documentId;
        final String name;

        Level(String treeUri, String documentId, String name) {
            this.treeUri = treeUri;
            this.documentId = documentId;
            this.name = name;
        }
    }

    private final NineReaderApp app;
    private final MutableLiveData<Section> section = new MutableLiveData<>(Section.LIBRARY);
    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MutableLiveData<String> sort;
    private final MutableLiveData<List<FileRow>> browsed = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<CollectionEntity> openCollection = new MutableLiveData<>(null);
    private final MediatorLiveData<List<FileRow>> rows = new MediatorLiveData<>();
    private final Deque<Level> stack = new ArrayDeque<>();
    private final LiveData<List<DocumentEntity>> library;
    private final LiveData<List<FolderEntity>> folders;
    private final LiveData<List<CollectionEntity>> collections;
    private final LiveData<List<TrashItemEntity>> trash;
    @Nullable
    private LiveData<List<DocumentEntity>> collectionDocs;
    private final MutableLiveData<Integer> pathVersion = new MutableLiveData<>(0);

    public FilesViewModel(@NonNull Application application) {
        super(application);
        app = NineReaderApp.from(application);
        sort = new MutableLiveData<>(app.prefs().filesSort());
        library = app.documents().all();
        folders = app.folders().all();
        collections = app.collections().all();
        trash = app.trash().all();
        rows.addSource(section, s -> rebuild());
        rows.addSource(query, s -> rebuild());
        rows.addSource(sort, s -> rebuild());
        rows.addSource(library, s -> rebuild());
        rows.addSource(folders, s -> rebuild());
        rows.addSource(collections, s -> rebuild());
        rows.addSource(trash, s -> rebuild());
        rows.addSource(browsed, s -> rebuild());
    }

    public LiveData<List<FileRow>> rows() {
        return rows;
    }

    public LiveData<Section> section() {
        return section;
    }

    public LiveData<Boolean> loading() {
        return loading;
    }

    public LiveData<Integer> pathVersion() {
        return pathVersion;
    }

    public void setSection(@NonNull Section s) {
        if (s == section.getValue()) return;
        stack.clear();
        setCollection(null);
        section.setValue(s);
        pathVersion.setValue(pathVersion.getValue() == null ? 0 : pathVersion.getValue() + 1);
    }

    public void setQuery(@NonNull String q) {
        query.setValue(q);
    }

    @NonNull
    public String sortMode() {
        String s = sort.getValue();
        return s == null ? FileSorter.SORT_NAME : s;
    }

    public void setSort(@NonNull String s) {
        app.prefs().setFilesSort(s);
        sort.setValue(s);
    }

    /** Path shown above the list ("" at the top level). */
    @NonNull
    public String path() {
        if (section.getValue() == Section.COLLECTIONS && openCollection.getValue() != null) {
            return openCollection.getValue().name;
        }
        StringBuilder sb = new StringBuilder();
        List<Level> levels = new ArrayList<>(stack);
        Collections.reverse(levels);
        for (Level l : levels) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(l.name);
        }
        return sb.toString();
    }

    public boolean canGoUp() {
        return !stack.isEmpty() || openCollection.getValue() != null;
    }

    public void goUp() {
        if (openCollection.getValue() != null) {
            setCollection(null);
        } else if (!stack.isEmpty()) {
            stack.pop();
            if (stack.isEmpty()) {
                browsed.setValue(Collections.emptyList());
            } else {
                load(stack.peek());
            }
        }
        pathVersion.setValue(pathVersion.getValue() == null ? 0 : pathVersion.getValue() + 1);
    }

    public void enterFolder(@NonNull FileRow row) {
        String tree = row.kind == FileRow.Kind.FOLDER_ROOT ? row.uri : row.treeUri;
        if (tree == null) return;
        String docId = row.kind == FileRow.Kind.FOLDER_ROOT
                ? DocumentsContract.getTreeDocumentId(Uri.parse(tree)) : row.documentId;
        if (docId == null) return;
        Level level = new Level(tree, docId, row.title);
        stack.push(level);
        load(level);
        pathVersion.setValue(pathVersion.getValue() == null ? 0 : pathVersion.getValue() + 1);
    }

    /** Re-lists the current folder (after an operation). */
    public void refresh() {
        Level l = stack.peek();
        if (l != null) load(l);
    }

    private void load(Level level) {
        loading.setValue(true);
        AppExecutors.get().background().execute(() -> {
            List<FileRow> list = SafBrowser.list(getApplication(), Uri.parse(level.treeUri), level.documentId);
            if (stack.peek() == level) browsed.postValue(list);
            loading.postValue(false);
        });
    }

    public void setCollection(@Nullable CollectionEntity c) {
        if (collectionDocs != null) rows.removeSource(collectionDocs);
        collectionDocs = null;
        openCollection.setValue(c);
        if (c != null) {
            collectionDocs = app.documents().byCollection(c.id);
            rows.addSource(collectionDocs, s -> rebuild());
        }
        pathVersion.setValue(pathVersion.getValue() == null ? 0 : pathVersion.getValue() + 1);
        rebuild();
    }

    private void rebuild() {
        Section s = section.getValue();
        List<FileRow> out = new ArrayList<>();
        if (s == null) s = Section.LIBRARY;
        switch (s) {
            case LIBRARY:
                addDocuments(out, library.getValue());
                break;
            case FOLDERS:
                if (stack.isEmpty()) {
                    List<FolderEntity> list = folders.getValue();
                    if (list != null) {
                        for (FolderEntity f : list) {
                            FileRow r = new FileRow(FileRow.Kind.FOLDER_ROOT, f.name, f.uri);
                            r.date = f.addedAt;
                            out.add(r);
                        }
                    }
                } else {
                    List<FileRow> list = browsed.getValue();
                    if (list != null) out.addAll(list);
                }
                break;
            case COLLECTIONS:
                if (openCollection.getValue() == null) {
                    List<CollectionEntity> list = collections.getValue();
                    if (list != null) {
                        for (CollectionEntity c : list) {
                            FileRow r = new FileRow(FileRow.Kind.COLLECTION, c.name, "collection:" + c.id);
                            r.collection = c;
                            r.date = c.createdAt;
                            out.add(r);
                        }
                    }
                } else if (collectionDocs != null) {
                    addDocuments(out, collectionDocs.getValue());
                }
                break;
            case TRASH:
                List<TrashItemEntity> items = trash.getValue();
                if (items != null) {
                    for (TrashItemEntity t : items) {
                        FileRow r = new FileRow(FileRow.Kind.TRASH, t.name, t.tempPath);
                        r.trash = t;
                        r.size = t.size;
                        r.date = t.deletedAt;
                        out.add(r);
                    }
                }
                break;
            default:
                break;
        }
        String q = query.getValue();
        rows.setValue(FileSorter.apply(out, sortMode(), q == null ? "" : q));
    }

    private static void addDocuments(List<FileRow> out, @Nullable List<DocumentEntity> docs) {
        if (docs == null) return;
        for (DocumentEntity d : docs) {
            FileRow r = new FileRow(FileRow.Kind.DOCUMENT, d.name, d.uri);
            r.doc = d;
            r.size = d.size;
            r.date = Math.max(d.lastOpened, d.addedAt);
            r.parentUri = d.parentUri;
            out.add(r);
        }
    }
}
