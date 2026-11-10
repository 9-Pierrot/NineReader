package com.ninereader.ui.reader;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ninereader.edit.TextBlock;
import com.ninereader.edit.TextBlockExtractor;
import com.ninereader.pdfops.PdfBoxHelper;
import com.ninereader.reader.PdfEngine;
import com.ninereader.reader.ReflowFormatter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Text detection for the edit mode (MOD-02): finds the editable blocks of each page on demand, from the
 * PDF glyphs, or by OCR for scanned pages. Results are cached per page.
 */
public class TextEditSession {

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nr-text-edit");
        t.setDaemon(true);
        return t;
    });
    private final Map<Integer, List<TextBlock>> blocks = new ConcurrentHashMap<>();
    private final Set<Integer> pending = ConcurrentHashMap.newKeySet();
    private final MutableLiveData<Integer> version = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> recognizedPage = new MutableLiveData<>(-1);
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private int versionCounter;
    @Nullable
    private volatile PdfBoxHelper.Loaded loaded;
    @Nullable
    private Uri uri;
    @Nullable
    private String password;
    @Nullable
    private Supplier<PdfEngine> engine;
    private int generation;

    public TextEditSession(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** Incremented whenever blocks of a page become available. */
    public LiveData<Integer> version() {
        return version;
    }

    /** Emits the page index after an OCR recognition (scanned page). */
    public LiveData<Integer> recognizedPage() {
        return recognizedPage;
    }

    public LiveData<Boolean> busy() {
        return busy;
    }

    /** Binds the session to the open document; forgets previous results when the document changed. */
    public void bind(@NonNull Uri uri, @Nullable String password, @NonNull Supplier<PdfEngine> engine) {
        this.uri = uri;
        this.password = password;
        this.engine = engine;
        invalidate();
    }

    /** Drops cached blocks (the file was modified). */
    public void invalidate() {
        generation++;
        blocks.clear();
        pending.clear();
        PdfBoxHelper.Loaded l = loaded;
        loaded = null;
        if (l != null) executor.execute(l::close);
    }

    /** Cached blocks of a page, or null when not detected yet. */
    @Nullable
    public List<TextBlock> blocks(int page) {
        return blocks.get(page);
    }

    /** Detects the blocks of a page in the background (no-op when cached or pending). */
    public void request(int page) {
        if (page < 0 || blocks.containsKey(page) || !pending.add(page)) return;
        Uri u = uri;
        Supplier<PdfEngine> e = engine;
        if (u == null || e == null) return;
        int gen = generation;
        busy.postValue(true);
        executor.execute(() -> {
            List<TextBlock> result = Collections.emptyList();
            boolean ocr = false;
            try {
                PdfBoxHelper.Loaded l = loaded;
                if (l == null) {
                    l = PdfBoxHelper.load(context, u, password);
                    loaded = l;
                }
                if (page < l.doc.getNumberOfPages()) result = TextBlockExtractor.fromPdf(l.doc, page);
                PdfEngine pdf = e.get();
                if (result.isEmpty() && pdf != null && !pdf.isClosed() && !ReflowFormatter.hasUsableText(pdf.pageText(page))) {
                    // Scanned page: recognise its text (offline OCR).
                    result = TextBlockExtractor.fromOcr(context, pdf, page);
                    ocr = !result.isEmpty();
                }
            } catch (IOException | RuntimeException ex) {
                result = Collections.emptyList();
            }
            if (gen != generation) return;
            blocks.put(page, result);
            pending.remove(page);
            busy.postValue(!pending.isEmpty());
            synchronized (this) {
                versionCounter++;
                version.postValue(versionCounter);
            }
            if (ocr) recognizedPage.postValue(page);
        });
    }

    /** Releases the PdfBox document. */
    public void close() {
        invalidate();
        executor.shutdown();
    }
}
