package com.ninereader.ui.reader;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ninereader.annotations.AnnotationExporter;
import com.ninereader.annotations.AnnotationStore;
import com.ninereader.annotations.FormField;
import com.ninereader.annotations.PdfAnnotationWriter;
import com.ninereader.annotations.PdfFormReader;
import com.ninereader.data.AppDatabase;
import com.ninereader.data.entity.AnnotationDraftEntity;
import com.ninereader.pdfops.PdfBoxHelper;
import com.ninereader.util.AppExecutors;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Editing session of the open document: pending annotations and form values (auto-saved as a Room
 * draft), AcroForm fields, annotations already in the file, and writing everything into the PDF.
 */
public class DocumentEditor implements AnnotationStore.Listener {

    /** Outcome of a save. */
    public static final class SaveResult {
        public final boolean success;
        public final boolean overwritten;
        @Nullable
        public final Uri destination;
        public final int failures;
        @Nullable
        public final String error;

        SaveResult(boolean success, boolean overwritten, @Nullable Uri destination, int failures, @Nullable String error) {
            this.success = success;
            this.overwritten = overwritten;
            this.destination = destination;
            this.failures = failures;
            this.error = error;
        }
    }

    private final Context context;
    private final AppDatabase db;
    private final AnnotationStore store = new AnnotationStore();
    private final MutableLiveData<Integer> version = new MutableLiveData<>(0);
    private final MutableLiveData<List<FormField>> fields = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<AnnotationExporter.Entry>> existing = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    @Nullable
    private String draftKey;
    private boolean loading;
    private int versionCounter;

    public DocumentEditor(@NonNull Context context, @NonNull AppDatabase db) {
        this.context = context.getApplicationContext();
        this.db = db;
        store.setListener(this);
    }

    @NonNull
    public AnnotationStore store() {
        return store;
    }

    /** Incremented on every change of the pending annotations. */
    public LiveData<Integer> version() {
        return version;
    }

    public LiveData<List<FormField>> fields() {
        return fields;
    }

    public LiveData<List<AnnotationExporter.Entry>> existingAnnotations() {
        return existing;
    }

    public LiveData<Boolean> saving() {
        return saving;
    }

    public boolean hasPendingChanges() {
        return !store.isEmpty();
    }

    /** Loads the draft and the form/annotation info of a document. */
    public void load(@NonNull Uri uri, @Nullable String password) {
        String key = uri.toString();
        boolean sameDoc = key.equals(draftKey);
        draftKey = key;
        if (!sameDoc) {
            loading = true;
            AppExecutors.get().diskIo().execute(() -> {
                AnnotationDraftEntity d = db.drafts().get(key);
                String json = d == null ? null : d.json;
                AppExecutors.get().mainThread().execute(() -> {
                    store.loadJson(json);
                    loading = false;
                });
            });
        }
        AppExecutors.get().background().execute(() -> readPdfInfo(uri, password));
    }

    private void readPdfInfo(Uri uri, @Nullable String password) {
        try (PdfBoxHelper.Loaded loaded = PdfBoxHelper.load(context, uri, password)) {
            fields.postValue(PdfFormReader.read(loaded.doc));
            existing.postValue(PdfAnnotationWriter.existing(loaded.doc));
        } catch (IOException | RuntimeException e) {
            fields.postValue(Collections.emptyList());
            existing.postValue(Collections.emptyList());
        }
    }

    @Override
    public void onAnnotationsChanged(@NonNull AnnotationStore s) {
        version.setValue(++versionCounter);
        if (loading || draftKey == null) return;
        String key = draftKey;
        boolean empty = s.isEmpty();
        String json = s.toJson();
        AppExecutors.get().diskIo().execute(() -> {
            if (empty) {
                db.drafts().delete(key);
            } else {
                AnnotationDraftEntity d = new AnnotationDraftEntity();
                d.uri = key;
                d.json = json;
                d.updatedAt = System.currentTimeMillis();
                db.drafts().upsert(d);
            }
        });
    }

    /**
     * Writes pending changes. With {@code copyDestination == null} the original file is replaced
     * (after the complete new file was written to a temporary file); otherwise a copy is written.
     *
     * @param beforePublish run on the main thread right before the original is replaced (the reader
     *                      must release the file)
     */
    public void save(@NonNull Uri source, @Nullable String password, @Nullable Uri copyDestination,
                     @NonNull Runnable beforePublish, @NonNull SaveCallback callback) {
        if (Boolean.TRUE.equals(saving.getValue())) return;
        saving.setValue(true);
        List<com.ninereader.annotations.Annotation> items = new ArrayList<>(store.all());
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>(store.fields());
        AppExecutors.get().background().execute(() -> {
            File temp;
            int failures;
            try (PdfBoxHelper.Loaded loaded = PdfBoxHelper.load(context, source, password)) {
                java.util.Map<Integer, List<float[]>> redactions = new java.util.TreeMap<>();
                for (com.ninereader.annotations.Annotation an : items) {
                    if (an.type == com.ninereader.annotations.Annotation.Type.REDACT && an.bounds != null) {
                        List<float[]> list = redactions.get(an.page);
                        if (list == null) {
                            list = new ArrayList<>();
                            redactions.put(an.page, list);
                        }
                        list.add(an.bounds);
                    }
                }
                // MOD-03: real removal of the redacted content before other annotations are added.
                com.ninereader.pdfops.Redaction.apply(context, loaded.doc, source, password, redactions);
                PdfAnnotationWriter.Result r = PdfAnnotationWriter.apply(loaded.doc, items, values);
                failures = r.failures.size();
                temp = PdfBoxHelper.saveToTemp(context, loaded.doc);
            } catch (IOException | RuntimeException e) {
                finish(callback, new SaveResult(false, false, null, 0, e.getMessage()));
                return;
            }
            final int f = failures;
            final File out = temp;
            Uri dest = copyDestination != null ? copyDestination : source;
            Runnable publish = () -> AppExecutors.get().background().execute(() -> {
                try {
                    PdfBoxHelper.publish(context, out, dest);
                } catch (IOException | RuntimeException e) {
                    finish(callback, new SaveResult(false, copyDestination == null, dest, f, e.getMessage()));
                    return;
                }
                AppExecutors.get().mainThread().execute(store::clear);
                finish(callback, new SaveResult(true, copyDestination == null, dest, f, null));
            });
            AppExecutors.get().mainThread().execute(() -> {
                if (copyDestination == null) beforePublish.run();
                publish.run();
            });
        });
    }

    private void finish(SaveCallback cb, SaveResult result) {
        AppExecutors.get().mainThread().execute(() -> {
            saving.setValue(false);
            cb.onSaved(result);
        });
    }

    /** Save completion callback (main thread). */
    public interface SaveCallback {
        void onSaved(@NonNull SaveResult result);
    }
}
