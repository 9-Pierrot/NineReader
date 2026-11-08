package com.ninereader.ui.reader;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.ninereader.NineReaderApp;
import com.ninereader.data.DocumentRepository;
import com.ninereader.data.ReadingStats;
import com.ninereader.data.entity.BookmarkEntity;
import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.reader.BitmapCache;
import com.ninereader.reader.MarginDetector;
import com.ninereader.reader.OutlineItem;
import com.ninereader.reader.PdfEngine;
import com.ninereader.reader.ReaderTheme;
import com.ninereader.reader.ReadingMode;
import com.ninereader.reader.RenderScheduler;
import com.ninereader.reader.TextSearch;
import com.ninereader.util.AppExecutors;
import com.ninereader.util.FileUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.legere.pdfiumandroid.PdfDocument;
import io.legere.pdfiumandroid.PdfPasswordException;

/**
 * State of the reader screen: the opened document, rendering pipeline, reading position, search,
 * selection, bookmarks and outline. Survives configuration changes (§5.2).
 */
public class ReaderViewModel extends AndroidViewModel {

    /** Opening status. */
    public enum Status { IDLE, LOADING, PASSWORD, READY, ERROR }

    /** Above this page count only the pages around the start page are measured before display. */
    private static final int MEASURE_ALL_LIMIT = 400;
    private static final int MAX_SEARCH_RESULTS = 2000;

    private final MutableLiveData<Status> status = new MutableLiveData<>(Status.IDLE);
    private final MutableLiveData<Boolean> wrongPassword = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> currentPage = new MutableLiveData<>(0);
    private final MutableLiveData<SearchState> search = new MutableLiveData<>(SearchState.empty());
    private final MutableLiveData<TextSelection> selection = new MutableLiveData<>(null);
    private final MutableLiveData<List<OutlineItem>> outline = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Long> documentId = new MutableLiveData<>();
    private final MutableLiveData<float[]> crop = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> sizesUpdated = new MutableLiveData<>(false);
    private final LiveData<List<BookmarkEntity>> bookmarks;

    private final DocumentRepository documents;
    private final DocumentEditor editor;
    /** Text extraction / search / hit tests, kept apart from rendering. */
    private final ExecutorService textExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nr-text");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final AtomicInteger searchGeneration = new AtomicInteger();

    @Nullable
    private Uri uri;
    @Nullable
    private PdfEngine engine;
    @Nullable
    private RenderScheduler scheduler;
    @Nullable
    private DocumentEntity document;
    private String displayName = "";
    private int initialPage;
    private float initialZoom = 1f;
    @Nullable
    private ReadingMode explicitMode;
    @Nullable
    private ReaderTheme theme;
    private boolean cropEnabled;
    @Nullable
    private float[] computedCrop;
    private String errorDetail = "";
    /** Kept in memory only, to reopen the document after saving; never persisted (§5.5). */
    @Nullable
    private String password;
    private int pendingStartPage = -1;

    private long sessionStart;
    private final Set<Integer> pagesVisited = new HashSet<>();

    public ReaderViewModel(@NonNull Application app) {
        super(app);
        documents = NineReaderApp.from(app).documents();
        editor = new DocumentEditor(app, NineReaderApp.from(app).db());
        bookmarks = Transformations.switchMap(documentId, documents::bookmarks);
        cropEnabled = NineReaderApp.from(app).prefs().smartCrop();
    }

    // ------------------------------------------------------------------ opening

    public LiveData<Status> status() {
        return status;
    }

    public LiveData<Boolean> wrongPassword() {
        return wrongPassword;
    }

    @NonNull
    public String errorDetail() {
        return errorDetail;
    }

    /** Opens a document (no-op when it is already open). The password is never stored (§5.5). */
    public void open(@NonNull Uri uri, @Nullable String password) {
        if (engine != null && uri.equals(this.uri)) return;
        Status s = status.getValue();
        if (s == Status.LOADING) return;
        this.uri = uri;
        status.setValue(Status.LOADING);
        AppExecutors.get().background().execute(() -> doOpen(uri, password));
    }

    private void doOpen(@NonNull Uri uri, @Nullable String password) {
        Application app = getApplication();
        PdfEngine e;
        try {
            e = PdfEngine.open(app, uri, password);
        } catch (PdfPasswordException ex) {
            wrongPassword.postValue(password != null);
            status.postValue(Status.PASSWORD);
            return;
        } catch (IOException | RuntimeException ex) {
            errorDetail = ex.getMessage() == null ? "" : ex.getMessage();
            status.postValue(Status.ERROR);
            return;
        }
        String name = FileUtils.displayName(app, uri);
        long size = FileUtils.size(app, uri);
        DocumentEntity doc = documents.registerOpened(uri.toString(), name, size, null, e.pageCount(),
                System.currentTimeMillis());
        int wanted = pendingStartPage >= 0 ? pendingStartPage : doc.lastPage;
        pendingStartPage = -1;
        int start = Math.max(0, Math.min(e.pageCount() - 1, wanted));
        this.password = password;
        measureInitial(e, start);
        engine = e;
        scheduler = new RenderScheduler(e, BitmapCache.forHeap());
        document = doc;
        displayName = name;
        initialPage = start;
        initialZoom = doc.lastZoom <= 0 ? 1f : doc.lastZoom;
        explicitMode = doc.readingMode == null ? null : ReadingMode.fromKey(doc.readingMode, ReadingMode.CONTINUOUS);
        sessionStart = System.currentTimeMillis();
        currentPage.postValue(start);
        documentId.postValue(doc.id);
        status.postValue(Status.READY);
        AppExecutors.get().mainThread().execute(() -> editor.load(uri, password));
        if (cropEnabled) computeCrop();
        textExecutor.execute(() -> {
            if (!e.isClosed()) outline.postValue(e.outline());
        });
        textExecutor.execute(this::measureRemaining);
        textExecutor.execute(this::indexText);
        AppExecutors.get().background().execute(() -> {
            String hash = FileUtils.quickHash(app, uri);
            if (hash != null && document != null) {
                document.hash = hash;
                documents.update(document);
            }
        });
    }

    private void measureInitial(@NonNull PdfEngine e, int start) {
        int n = e.pageCount();
        if (n <= MEASURE_ALL_LIMIT) {
            for (int i = 0; i < n; i++) e.ensureSize(i);
        } else {
            e.ensureSize(0);
            for (int i = Math.max(0, start - 20); i < Math.min(n, start + 20); i++) e.ensureSize(i);
        }
    }

    private void measureRemaining() {
        PdfEngine e = engine;
        if (e == null || e.pageCount() <= MEASURE_ALL_LIMIT) return;
        boolean changed = false;
        float w0 = e.pageWidth(0);
        float h0 = e.pageHeight(0);
        for (int i = 0; i < e.pageCount() && !e.isClosed(); i++) {
            if (e.isMeasured(i)) continue;
            e.ensureSize(i);
            if (Math.abs(e.pageWidth(i) - w0) > 1 || Math.abs(e.pageHeight(i) - h0) > 1) changed = true;
        }
        if (changed) sizesUpdated.postValue(true);
    }

    /** Extracts the text of every page in the background so that search is fast (§5.1). */
    private void indexText() {
        PdfEngine e = engine;
        if (e == null) return;
        for (int i = 0; i < e.pageCount() && !e.isClosed(); i++) {
            try {
                e.pageText(i);
            } catch (RuntimeException ignored) {
                // Damaged page: skip.
            }
        }
    }

    /** Emits true when page sizes changed after the first display (very large documents). */
    public LiveData<Boolean> sizesUpdated() {
        return sizesUpdated;
    }

    @Nullable
    public PdfEngine engine() {
        return engine;
    }

    @Nullable
    public RenderScheduler scheduler() {
        return scheduler;
    }

    @Nullable
    public Uri uri() {
        return uri;
    }

    @Nullable
    public DocumentEntity document() {
        return document;
    }

    @NonNull
    public String displayName() {
        return displayName;
    }

    public int pageCount() {
        return engine == null ? 0 : engine.pageCount();
    }

    public int initialPage() {
        return initialPage;
    }

    public float initialZoom() {
        return initialZoom;
    }

    /** Consumes the initial zoom so it is only restored once. */
    public float takeInitialZoom() {
        float z = initialZoom;
        initialZoom = 1f;
        return z;
    }

    @NonNull
    public DocumentEditor editor() {
        return editor;
    }

    // ------------------------------------------------------------------ position & mode

    public LiveData<Integer> currentPage() {
        return currentPage;
    }

    public int page() {
        Integer p = currentPage.getValue();
        return p == null ? 0 : p;
    }

    public void setCurrentPage(int page) {
        Integer cur = currentPage.getValue();
        if (cur == null || cur != page) currentPage.setValue(page);
        pagesVisited.add(page);
    }

    @Nullable
    public ReadingMode explicitMode() {
        return explicitMode;
    }

    public void setExplicitMode(@NonNull ReadingMode mode) {
        explicitMode = mode;
    }

    /** Mode to use given the screen configuration (LEC-14: double page by default on tablet landscape). */
    @NonNull
    public ReadingMode effectiveMode(boolean tabletLandscape) {
        if (explicitMode != null) return explicitMode;
        NineReaderApp app = NineReaderApp.from(getApplication());
        if (tabletLandscape && app.prefs().doublePageOnTabletLandscape()) return ReadingMode.DOUBLE;
        return ReadingMode.fromKey(app.prefs().readingMode(), ReadingMode.CONTINUOUS);
    }

    @Nullable
    public ReaderTheme theme() {
        return theme;
    }

    public void setTheme(@NonNull ReaderTheme theme) {
        this.theme = theme;
    }

    public void savePosition(float zoom) {
        DocumentEntity d = document;
        if (d == null) return;
        d.lastPage = page();
        d.lastZoom = zoom;
        documents.savePosition(d.id, page(), zoom, explicitMode == null ? null : explicitMode.key);
    }

    /** Records the reading session (RAP-07); called when the reader goes to background. */
    public void flushSession() {
        DocumentEntity d = document;
        long now = System.currentTimeMillis();
        if (d != null && sessionStart > 0) {
            documents.recordSession(d.id, sessionStart, now, pagesVisited.size());
        }
        sessionStart = now;
        pagesVisited.clear();
        pagesVisited.add(page());
    }

    public void resumeSession() {
        if (sessionStart == 0 || pagesVisited.isEmpty()) {
            sessionStart = System.currentTimeMillis();
            pagesVisited.add(page());
        }
    }

    public void statsAsync(@NonNull DocumentRepository.Callback<ReadingStats> callback) {
        DocumentEntity d = document;
        if (d == null) return;
        AppExecutors.get().diskIo().execute(() -> {
            d.lastPage = page();
            ReadingStats stats = documents.statsFor(d);
            AppExecutors.get().mainThread().execute(() -> callback.onResult(stats));
        });
    }

    // ------------------------------------------------------------------ smart crop (RAP-01)

    public LiveData<float[]> crop() {
        return crop;
    }

    public boolean isCropEnabled() {
        return cropEnabled;
    }

    public void setCropEnabled(boolean enabled) {
        cropEnabled = enabled;
        NineReaderApp.from(getApplication()).prefs().setSmartCrop(enabled);
        if (!enabled) {
            crop.setValue(null);
        } else if (computedCrop != null) {
            crop.setValue(computedCrop);
        } else {
            computeCrop();
        }
    }

    private void computeCrop() {
        RenderScheduler sch = scheduler;
        PdfEngine e = engine;
        if (sch == null || e == null) return;
        sch.post(RenderScheduler.PRIORITY_VISIBLE, () -> {
            int n = e.pageCount();
            int samples = Math.min(n, 8);
            float[] union = null;
            for (int k = 0; k < samples; k++) {
                // Skip the cover page when possible: it often has full-bleed artwork.
                int page = n <= 2 ? k : 1 + (int) ((long) k * (n - 1) / samples);
                page = Math.min(n - 1, page);
                float w = e.pageWidth(page);
                float h = e.pageHeight(page);
                int bw = 160;
                int bh = Math.max(1, Math.round(bw * h / w));
                Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                e.render(page, bmp, 0, 0, bw, bh, false);
                int[] px = new int[bw * bh];
                bmp.getPixels(px, 0, bw, 0, 0, bw, bh);
                bmp.recycle();
                float[] c = MarginDetector.detect(px, bw, bh);
                union = union == null ? c : MarginDetector.union(union, c);
            }
            computedCrop = union;
            if (cropEnabled) {
                boolean full = union == null || (union[0] <= 0 && union[1] <= 0 && union[2] >= 1 && union[3] >= 1);
                crop.postValue(full ? null : union);
            }
        });
    }

    // ------------------------------------------------------------------ outline & bookmarks

    public LiveData<List<OutlineItem>> outline() {
        return outline;
    }

    public LiveData<List<BookmarkEntity>> bookmarks() {
        return bookmarks;
    }

    public boolean isBookmarked(int page) {
        List<BookmarkEntity> list = bookmarks.getValue();
        if (list == null) return false;
        for (BookmarkEntity b : list) if (b.page == page) return true;
        return false;
    }

    public void toggleBookmark(int page, @NonNull String label) {
        DocumentEntity d = document;
        if (d != null) documents.toggleBookmark(d.id, page, label, null);
    }

    // ------------------------------------------------------------------ search (LEC-07)

    public LiveData<SearchState> search() {
        return search;
    }

    public void search(@NonNull String rawQuery) {
        PdfEngine e = engine;
        String query = TextSearch.normalizeQuery(rawQuery);
        int gen = searchGeneration.incrementAndGet();
        if (e == null || query.isEmpty()) {
            search.setValue(SearchState.empty());
            return;
        }
        int startPage = page();
        search.setValue(new SearchState(rawQuery, Collections.emptyList(), -1, false, 0));
        textExecutor.execute(() -> {
            int n = e.pageCount();
            List<TextSearch.Match> all = new ArrayList<>();
            int current = -1;
            long lastPost = 0;
            for (int k = 0; k < n; k++) {
                if (gen != searchGeneration.get() || e.isClosed()) return;
                int page = k; // results are kept in document order
                String text;
                try {
                    text = e.pageText(page);
                } catch (RuntimeException ex) {
                    continue;
                }
                List<TextSearch.Match> found = TextSearch.findInPage(page, text, query, MAX_SEARCH_RESULTS - all.size());
                if (!found.isEmpty()) {
                    if (current < 0 && page >= startPage) current = all.size();
                    all.addAll(found);
                }
                long now = System.currentTimeMillis();
                if (!found.isEmpty() && now - lastPost > 150) {
                    lastPost = now;
                    int cur = current >= 0 ? current : (all.isEmpty() ? -1 : 0);
                    search.postValue(new SearchState(rawQuery, new ArrayList<>(all), cur, false, k + 1));
                }
                if (all.size() >= MAX_SEARCH_RESULTS) break;
            }
            if (gen != searchGeneration.get()) return;
            int cur = current >= 0 ? current : (all.isEmpty() ? -1 : 0);
            search.postValue(new SearchState(rawQuery, all, cur, true, n));
        });
    }

    public void searchStep(int delta) {
        SearchState s = search.getValue();
        if (s == null || s.results.isEmpty()) return;
        int size = s.results.size();
        int next = ((s.current < 0 ? 0 : s.current + delta) % size + size) % size;
        search.setValue(s.withCurrent(next));
    }

    public void selectSearchResult(int index) {
        SearchState s = search.getValue();
        if (s != null && index >= 0 && index < s.results.size()) search.setValue(s.withCurrent(index));
    }

    public void clearSearch() {
        searchGeneration.incrementAndGet();
        search.setValue(SearchState.empty());
    }

    /** Rectangles (page coordinates) of a search match; computed off the main thread. */
    public void matchRects(@NonNull TextSearch.Match m, @NonNull DocumentRepository.Callback<List<RectF>> cb) {
        PdfEngine e = engine;
        if (e == null) return;
        textExecutor.execute(() -> {
            List<RectF> rects = e.textRects(m.page, m.start, m.length);
            AppExecutors.get().mainThread().execute(() -> cb.onResult(rects));
        });
    }

    // ------------------------------------------------------------------ selection (LEC-11)

    public LiveData<TextSelection> selection() {
        return selection;
    }

    private int selectionAnchor = -1;
    private int selectionPage = -1;
    private volatile boolean selectionBusy;

    /** Starts a selection on the word under a page point. */
    public void beginSelection(int page, float pageX, float pageY, float tolerance,
                               @NonNull DocumentRepository.Callback<Boolean> started) {
        PdfEngine e = engine;
        if (e == null) return;
        textExecutor.execute(() -> {
            int idx = e.charIndexAt(page, pageX, pageY, tolerance);
            String text = e.pageText(page);
            if (idx < 0 || idx >= text.length()) {
                AppExecutors.get().mainThread().execute(() -> started.onResult(false));
                return;
            }
            int[] word = TextSelection.wordAt(text, idx);
            selectionAnchor = word[0];
            selectionPage = page;
            TextSelection sel = buildSelection(e, page, word[0], word[1], text);
            selection.postValue(sel);
            AppExecutors.get().mainThread().execute(() -> started.onResult(true));
        });
    }

    /** Extends the selection to the character under a page point (same page only). */
    public void extendSelection(int page, float pageX, float pageY, float tolerance) {
        PdfEngine e = engine;
        if (e == null || page != selectionPage || selectionAnchor < 0 || selectionBusy) return;
        selectionBusy = true;
        textExecutor.execute(() -> {
            try {
                int idx = e.charIndexAt(page, pageX, pageY, tolerance);
                if (idx < 0) return;
                String text = e.pageText(page);
                int[] word = TextSelection.wordAt(text, idx);
                int s = Math.min(selectionAnchor, word[0]);
                int end = Math.max(selectionAnchor + 1, word[1]);
                if (idx < selectionAnchor) end = Math.max(end, TextSelection.wordAt(text, selectionAnchor)[1]);
                selection.postValue(buildSelection(e, page, s, end, text));
            } finally {
                selectionBusy = false;
            }
        });
    }

    /** Selects an explicit range (used by annotation editing and TTS). */
    public void selectRange(int page, int start, int end) {
        PdfEngine e = engine;
        if (e == null) return;
        textExecutor.execute(() -> selection.postValue(buildSelection(e, page, start, end, e.pageText(page))));
    }

    private TextSelection buildSelection(PdfEngine e, int page, int start, int end, String text) {
        int s = Math.max(0, Math.min(start, text.length()));
        int en = Math.max(s, Math.min(end, text.length()));
        List<RectF> rects = e.textRects(page, s, en - s);
        return new TextSelection(page, s, en, text.substring(s, en), rects);
    }

    public void clearSelection() {
        selectionAnchor = -1;
        selectionPage = -1;
        selection.setValue(null);
    }

    // ------------------------------------------------------------------ links (LEC-15) & text

    /** Finds the link under a page point. */
    public void linkAt(int page, float pageX, float pageY, @NonNull DocumentRepository.Callback<PdfDocument.Link> cb) {
        PdfEngine e = engine;
        if (e == null) {
            cb.onResult(null);
            return;
        }
        textExecutor.execute(() -> {
            PdfDocument.Link found = null;
            for (PdfDocument.Link l : e.links(page)) {
                RectF b = l.getBounds();
                float left = Math.min(b.left, b.right);
                float right = Math.max(b.left, b.right);
                float bottom = Math.min(b.top, b.bottom);
                float top = Math.max(b.top, b.bottom);
                if (pageX >= left - 2 && pageX <= right + 2 && pageY >= bottom - 2 && pageY <= top + 2) {
                    found = l;
                    break;
                }
            }
            PdfDocument.Link result = found;
            AppExecutors.get().mainThread().execute(() -> cb.onResult(result));
        });
    }

    /** Text of a page, off the main thread. */
    public void pageText(int page, @NonNull DocumentRepository.Callback<String> cb) {
        PdfEngine e = engine;
        if (e == null) return;
        textExecutor.execute(() -> {
            String t = e.pageText(page);
            AppExecutors.get().mainThread().execute(() -> cb.onResult(t));
        });
    }

    /** Text line rectangles of a page (page coordinates), off the main thread. */
    public void pageLineRects(int page, @NonNull DocumentRepository.Callback<List<RectF>> cb) {
        PdfEngine e = engine;
        if (e == null) return;
        textExecutor.execute(() -> {
            String t = e.pageText(page);
            List<RectF> rects = t.isEmpty() ? Collections.emptyList() : e.textRects(page, 0, t.length());
            AppExecutors.get().mainThread().execute(() -> cb.onResult(rects));
        });
    }

    /** Runs work on the text thread (keeps Pdfium access off the UI thread). */
    public void runOnTextThread(@NonNull Runnable r) {
        textExecutor.execute(r);
    }

    // ------------------------------------------------------------------ lifecycle

    /** Closes the current document (e.g. before reloading it after saving annotations). */
    public void closeDocument() {
        searchGeneration.incrementAndGet();
        RenderScheduler sch = scheduler;
        PdfEngine e = engine;
        scheduler = null;
        engine = null;
        if (sch != null) {
            sch.shutdown();
            sch.cache().clear();
        }
        if (e != null) textExecutor.execute(e::close);
    }

    /** Reopens the same document (after it was modified on disk). */
    public void reload() {
        Uri u = uri;
        if (u == null) return;
        pendingStartPage = page();
        String pwd = password;
        closeDocument();
        status.setValue(Status.LOADING);
        AppExecutors.get().background().execute(() -> doOpen(u, pwd));
    }

    /** Password used to open the current document (memory only), or null. */
    @Nullable
    public String password() {
        return password;
    }

    @Override
    protected void onCleared() {
        flushSession();
        closeDocument();
        textExecutor.shutdown();
        super.onCleared();
    }
}
