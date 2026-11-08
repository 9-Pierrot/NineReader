package com.ninereader.ui.tools;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.ninereader.pdfops.PagePlan;
import com.ninereader.reader.BitmapCache;
import com.ninereader.reader.PdfEngine;
import com.ninereader.reader.RenderScheduler;
import com.ninereader.util.AppExecutors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Page organizer state (ORG-01): opened document, page plan and selection. */
public class OrganizeViewModel extends AndroidViewModel {

    /** Loading state. */
    public enum Status { EMPTY, LOADING, READY, PASSWORD, ERROR }

    final MutableLiveData<Status> status = new MutableLiveData<>(Status.EMPTY);
    final MutableLiveData<Integer> version = new MutableLiveData<>(0);
    @Nullable
    Uri uri;
    @Nullable
    String password;
    @Nullable
    RenderScheduler scheduler;
    @NonNull
    PagePlan plan = new PagePlan();
    final Set<Integer> selection = new LinkedHashSet<>();
    @Nullable
    UUID workId;
    @Nullable
    Uri output;
    /** Stable ids for the entries of the plan (for drag & drop animations). */
    final List<Long> ids = new ArrayList<>();
    private long nextId;

    public OrganizeViewModel(@NonNull Application app) {
        super(app);
    }

    void open(@NonNull Uri u, @Nullable String pwd) {
        close();
        uri = u;
        password = pwd;
        status.setValue(Status.LOADING);
        AppExecutors.get().background().execute(() -> {
            try {
                PdfEngine engine = PdfEngine.open(getApplication(), u, pwd);
                for (int i = 0; i < engine.pageCount(); i++) engine.ensureSize(i);
                scheduler = new RenderScheduler(engine, new BitmapCache(24 * 1024 * 1024));
                plan = PagePlan.identity(engine.pageCount());
                resetIds();
                selection.clear();
                status.postValue(Status.READY);
            } catch (io.legere.pdfiumandroid.PdfPasswordException e) {
                status.postValue(Status.PASSWORD);
            } catch (IOException | RuntimeException e) {
                status.postValue(Status.ERROR);
            }
        });
    }

    private void resetIds() {
        ids.clear();
        for (int i = 0; i < plan.size(); i++) ids.add(nextId++);
    }

    void reset() {
        if (scheduler == null) return;
        plan = PagePlan.identity(scheduler.engine().pageCount());
        resetIds();
        selection.clear();
        changed();
    }

    void move(int from, int to) {
        plan.move(from, to);
        Long id = ids.remove(from);
        ids.add(to, id);
        // Keep the selection on the same pages.
        Set<Integer> moved = new LinkedHashSet<>();
        for (int s : selection) {
            int n = s;
            if (s == from) {
                n = to;
            } else if (from < to && s > from && s <= to) {
                n = s - 1;
            } else if (from > to && s >= to && s < from) {
                n = s + 1;
            }
            moved.add(n);
        }
        selection.clear();
        selection.addAll(moved);
    }

    void toggle(int index) {
        if (!selection.remove(index)) selection.add(index);
        changed();
    }

    void selectAll() {
        if (selection.size() == plan.size()) {
            selection.clear();
        } else {
            for (int i = 0; i < plan.size(); i++) selection.add(i);
        }
        changed();
    }

    void rotateSelected(int delta) {
        for (int i : selection) plan.rotate(i, delta);
        changed();
    }

    void deleteSelected() {
        List<Integer> sorted = new ArrayList<>(selection);
        Collections.sort(sorted, Collections.reverseOrder());
        for (int i : sorted) {
            plan.remove(i);
            ids.remove(i);
        }
        selection.clear();
        changed();
    }

    void duplicateSelected() {
        List<Integer> sorted = new ArrayList<>(selection);
        Collections.sort(sorted, Collections.reverseOrder());
        for (int i : sorted) {
            plan.duplicate(i);
            ids.add(i + 1, nextId++);
        }
        selection.clear();
        changed();
    }

    void insertBlankAfterSelection() {
        int at = plan.size();
        if (!selection.isEmpty()) at = Collections.max(selection) + 1;
        plan.insertBlank(at);
        ids.add(at, nextId++);
        selection.clear();
        changed();
    }

    void changed() {
        Integer v = version.getValue();
        version.setValue(v == null ? 1 : v + 1);
    }

    private void close() {
        RenderScheduler s = scheduler;
        scheduler = null;
        if (s != null) {
            s.shutdown();
            PdfEngine e = s.engine();
            AppExecutors.get().background().execute(e::close);
        }
    }

    @Override
    protected void onCleared() {
        close();
        super.onCleared();
    }
}
